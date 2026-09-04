package com.wind.guild.service;

import com.wind.guild.config.DiscordProperties;
import com.wind.guild.domain.*;
import com.wind.guild.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.awt.*;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordNotifier {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM/dd(E) HH:mm", java.util.Locale.KOREAN);
    private static final DecimalFormat MONEY = new DecimalFormat("#,###");

    private final DiscordProperties props;
    private final ObjectProvider<DiscordBotService> botProvider;
    private final RaidRepository raidRepository;
    private final RaidVoteRepository voteRepository;
    private final RaidAttendeeRepository attendeeRepository;
    private final RaidLootRepository lootRepository;
    private final LootShareRepository shareRepository;
    private final RaidPartyRepository partyRepository;
    private final RaidPartyMemberRepository partyMemberRepository;
    private final MemberRepository memberRepository;
    private final RaidTargetRepository targetRepository;
    private final RestTemplate rest = new RestTemplate();

    private DiscordBotService bot() { return botProvider.getIfAvailable(); }

    // Discord 글로벌 429 감지 시 이 시각까지 모든 outbound 스킵 (재쇄도로 ban 연장 방지)
    private static volatile long globalCooldownUntilMillis = 0;
    private static final long COOLDOWN_MS_ON_429 = 10 * 60 * 1000L; // 10분

    private boolean inCooldown() { return System.currentTimeMillis() < globalCooldownUntilMillis; }
    private long cooldownRemainingSec() { return Math.max(0, (globalCooldownUntilMillis - System.currentTimeMillis()) / 1000); }

    /** JDA 에러 콜백에서 429 감지 시 호출. 이후 10분간 모든 outbound 스킵. */
    static void noteRateLimitError(Throwable err) {
        String s = err == null ? "" : err.toString();
        if (s.contains("429") || s.toLowerCase().contains("rate limit") || s.toLowerCase().contains("too many requests")) {
            globalCooldownUntilMillis = System.currentTimeMillis() + COOLDOWN_MS_ON_429;
        }
    }

    public enum RaidTrigger { CREATED, VOTE, ATTENDEES, PARTY, PRE30, STATUS, LOOT, DIST }
    public enum LootTrigger { DISTRIBUTED, PAID_CHANGED, PRICE_CHANGED }

    // ============================================================
    // Raid card: 한 레이드당 메시지 1개, 어떤 이벤트든 같은 카드 편집
    // ============================================================

    /** 카드 렌더링용 데이터 (embed+buttons 공유). N+1 방지 목적. */
    private record RaidCardData(
            List<RaidVote> votes,
            List<Long> attendeeIds,
            List<RaidParty> parties,
            Map<Long, List<RaidPartyMember>> partyMembersByPartyId,
            List<RaidLoot> loots,
            Map<Long, List<LootShare>> sharesByLootId,
            Map<Long, String> nickMap
    ) {}

    /** 한 번의 syncRaidCard 호출 안에서 필요한 모든 데이터를 배치 로드 (N+1 제거). */
    private RaidCardData loadCardData(Raid r) {
        Long raidId = r.getId();
        List<RaidVote> votes = voteRepository.findByRaidId(raidId);
        List<Long> attendeeIds = attendeeRepository.findByRaidId(raidId).stream()
                .map(RaidAttendee::getMemberId).toList();

        List<RaidParty> parties = partyRepository.findByRaidIdOrderByDisplayOrderAsc(raidId);
        Map<Long, List<RaidPartyMember>> partyMembersByPartyId = new HashMap<>();
        if (!parties.isEmpty()) {
            List<Long> pids = parties.stream().map(RaidParty::getId).toList();
            for (RaidPartyMember m : partyMemberRepository.findByPartyIdInOrderByPartyIdAscRoleAscDisplayOrderAsc(pids)) {
                partyMembersByPartyId.computeIfAbsent(m.getPartyId(), k -> new ArrayList<>()).add(m);
            }
        }

        List<RaidLoot> loots = lootRepository.findByRaidId(raidId);
        Map<Long, List<LootShare>> sharesByLootId = new HashMap<>();
        if (!loots.isEmpty()) {
            List<Long> lootIds = loots.stream().map(RaidLoot::getId).toList();
            for (LootShare s : shareRepository.findByLootIdIn(lootIds)) {
                sharesByLootId.computeIfAbsent(s.getLootId(), k -> new ArrayList<>()).add(s);
            }
        }

        // 통합 memberId 집합 (nick 배치 조회 1회)
        Set<Long> refIds = new HashSet<>();
        for (RaidVote v : votes) refIds.add(v.getMemberId());
        refIds.addAll(attendeeIds);
        for (RaidParty p : parties) {
            if (p.getMikeMemberId() != null) refIds.add(p.getMikeMemberId());
            List<RaidPartyMember> ms = partyMembersByPartyId.get(p.getId());
            if (ms != null) for (RaidPartyMember m : ms) if (m.getMemberId() != null) refIds.add(m.getMemberId());
        }
        for (List<LootShare> ss : sharesByLootId.values()) {
            for (LootShare s : ss) {
                refIds.add(s.getMemberId());
                if (s.getPaidBy() != null) refIds.add(s.getPaidBy());
            }
        }
        for (RaidLoot l : loots) if (l.getDistributedBy() != null) refIds.add(l.getDistributedBy());
        Map<Long, String> nickMap = fetchNicks(refIds);

        return new RaidCardData(votes, attendeeIds, parties, partyMembersByPartyId, loots, sharesByLootId, nickMap);
    }

    @org.springframework.scheduling.annotation.Async("discordExecutor")
    public void syncRaidCard(Long raidId, RaidTrigger trigger) {
        try {
            if (inCooldown()) {
                log.warn("syncRaidCard skipped (Discord 429 cooldown · {}s 남음, raidId={}, trigger={})",
                        cooldownRemainingSec(), raidId, trigger);
                return;
            }
            Raid r = raidRepository.findById(raidId).orElse(null);
            if (r == null) { log.warn("syncRaidCard skipped: raid {} not found", raidId); return; }

            DiscordBotService bot = bot();
            boolean botReady = bot != null && bot.isReady();
            if (!botReady) {
                log.warn("syncRaidCard skipped: bot not ready (raidId={}, trigger={})", raidId, trigger);
            }
            if (bot != null && bot.isReady()) {
                TextChannel ch = bot.notifyChannel();
                if (ch == null) return;
                RaidCardData d = loadCardData(r);
                MessageEmbed embed = buildRaidEmbed(r, trigger, d);
                var buttons = buildRaidButtons(r, d);
                var buttonsArr = buttons.toArray(new net.dv8tion.jda.api.interactions.components.LayoutComponent[0]);

                Long targetRaidId = r.getId();
                // CREATED 는 항상 새 메시지 (재발송·최초등록 모두 안정 · edit path 의 async race 우회)
                boolean forceNew = trigger == RaidTrigger.CREATED || r.getDiscordMessageId() == null;
                if (forceNew) {
                    log.info("syncRaidCard send NEW (raidId={}, trigger={}, chId={})", targetRaidId, trigger, ch.getId());
                    ch.sendMessageEmbeds(embed).setComponents(buttonsArr).queue(msg -> {
                        raidRepository.updateDiscordMessageId(targetRaidId, msg.getIdLong());
                        log.info("syncRaidCard NEW ok (raidId={}, msgId={})", targetRaidId, msg.getIdLong());
                    }, err -> { noteRateLimitError(err); log.warn("raid card send failed (raidId={}, trigger={}): {}", targetRaidId, trigger, err.toString()); });
                } else {
                    log.info("syncRaidCard EDIT (raidId={}, trigger={}, msgId={})", targetRaidId, trigger, r.getDiscordMessageId());
                    ch.editMessageEmbedsById(r.getDiscordMessageId(), embed)
                            .setComponents(buttonsArr)
                            .queue(null, err -> {
                                log.warn("raid card edit failed (fallback to new send · raidId={}): {}", targetRaidId, err.toString());
                                ch.sendMessageEmbeds(embed).setComponents(buttonsArr).queue(m -> {
                                    raidRepository.updateDiscordMessageId(targetRaidId, m.getIdLong());
                                    log.info("syncRaidCard fallback NEW ok (raidId={}, msgId={})", targetRaidId, m.getIdLong());
                                }, err2 -> { noteRateLimitError(err2); log.warn("raid card fallback send failed (raidId={}): {}", targetRaidId, err2.toString()); });
                            });
                }
                return;
            }

            // Webhook fallback: bot 이 없을 때만 최초 생성 시 알림 (편집 불가)
            if (trigger == RaidTrigger.CREATED
                    && props.getWebhookUrl() != null && !props.getWebhookUrl().isBlank()) {
                sendRaidViaWebhook(r);
            }
        } catch (Exception e) {
            log.warn("syncRaidCard error (raidId={}, trigger={}): {}", raidId, trigger, e.toString(), e);
        }
    }

    /**
     * 카테고리별 최초 발송만 new, 이후는 edit.
     * · PARTY: raid.partyFreshSent flag 관리
     * · LOOT: raid.lootFreshSent flag 관리
     * · DIST: raid.distFreshSent flag 관리
     * · 그 외: syncRaidCard 위임 (edit)
     */
    @org.springframework.scheduling.annotation.Async("discordExecutor")
    public void syncRaidCardCategoryAware(Long raidId, RaidTrigger trigger) {
        try {
            Raid r = raidRepository.findById(raidId).orElse(null);
            if (r == null) return;
            boolean firstOfCategory = false;
            if (trigger == RaidTrigger.PARTY && !r.isPartyFreshSent()) {
                r.setPartyFreshSent(true); raidRepository.save(r); firstOfCategory = true;
            } else if (trigger == RaidTrigger.LOOT && !r.isLootFreshSent()) {
                r.setLootFreshSent(true); raidRepository.save(r); firstOfCategory = true;
            } else if (trigger == RaidTrigger.DIST && !r.isDistFreshSent()) {
                r.setDistFreshSent(true); raidRepository.save(r); firstOfCategory = true;
            }
            if (firstOfCategory) syncRaidCardFresh(raidId, trigger);
            else syncRaidCard(raidId, trigger);
        } catch (Exception e) {
            log.warn("syncRaidCardCategoryAware error (raidId={}, trigger={}): {}", raidId, trigger, e.toString(), e);
        }
    }

    /**
     * 항상 **새 메시지** 로 카드를 발송하고, discordMessageId 를 새 것으로 갱신.
     * 사용: 레이드 등록·파티편성·득템 입력·분배·지급·리마인더 등 '완료' 성격 이벤트.
     * 이후 minor 이벤트 (투표 등) 는 syncRaidCard 로 새 카드에 편집.
     */
    @org.springframework.scheduling.annotation.Async("discordExecutor")
    public void syncRaidCardFresh(Long raidId, RaidTrigger trigger) {
        try {
            if (inCooldown()) {
                log.warn("syncRaidCardFresh skipped (Discord 429 cooldown · {}s 남음, raidId={}, trigger={})",
                        cooldownRemainingSec(), raidId, trigger);
                return;
            }
            Raid r = raidRepository.findById(raidId).orElse(null);
            if (r == null) return;
            DiscordBotService bot = bot();
            if (bot == null || !bot.isReady()) {
                syncRaidCard(raidId, trigger); // fallback (webhook 등)
                return;
            }
            TextChannel ch = bot.notifyChannel();
            if (ch == null) return;
            RaidCardData d = loadCardData(r);
            MessageEmbed embed = buildRaidEmbed(r, trigger, d);
            var buttons = buildRaidButtons(r, d);
            var buttonsArr = buttons.toArray(new net.dv8tion.jda.api.interactions.components.LayoutComponent[0]);
            Long raidId2 = r.getId();
            log.info("syncRaidCardFresh send NEW (raidId={}, trigger={}, chId={})", raidId2, trigger, ch.getId());
            ch.sendMessageEmbeds(embed).setComponents(buttonsArr).queue(msg -> {
                raidRepository.updateDiscordMessageId(raidId2, msg.getIdLong());
                log.info("syncRaidCardFresh NEW ok (raidId={}, msgId={})", raidId2, msg.getIdLong());
            }, err -> { noteRateLimitError(err); log.warn("raid card fresh send failed (raidId={}, trigger={}): {}", raidId2, trigger, err.toString()); });
        } catch (Exception e) {
            log.warn("syncRaidCardFresh error (raidId={}, trigger={}): {}", raidId, trigger, e.toString(), e);
        }
    }

    private MessageEmbed buildRaidEmbed(Raid r, RaidTrigger trigger) {
        return buildRaidEmbed(r, trigger, loadCardData(r));
    }

    private MessageEmbed buildRaidEmbed(Raid r, RaidTrigger trigger, RaidCardData d) {
        String title;
        Color color;
        switch (r.getStatus()) {
            case DONE -> { title = "✅ 레이드 완료"; color = new Color(0x52C41A); }
            case CANCELLED -> { title = "🚫 레이드 취소"; color = new Color(0x8C8C8C); }
            default -> {
                switch (trigger) {
                    case PRE30 -> { title = "⏰ 30분 뒤 시작"; color = new Color(0xFAAD14); }
                    case CREATED -> { title = "🆕 새 레이드"; color = new Color(0x00B0FF); }
                    case ATTENDEES -> { title = "🎯 참가자 확정"; color = new Color(0x7C3AED); }
                    case PARTY -> { title = "🛡️ 파티 편성"; color = new Color(0x7C3AED); }
                    case LOOT -> { title = "💰 득템 등록"; color = new Color(0xFAAD14); }
                    case DIST -> { title = "⚖️ 분배"; color = new Color(0x52C41A); }
                    case VOTE -> { title = "📢 레이드 안내"; color = new Color(0x00B0FF); }
                    default -> { title = "📢 레이드 안내"; color = new Color(0x00B0FF); }
                }
            }
        }

        // 캐시된 데이터 사용 (N+1 제거)
        List<RaidVote> votes = d.votes();
        List<Long> yesIds = votes.stream().filter(v -> v.getVote() == VoteType.YES).map(RaidVote::getMemberId).toList();
        List<Long> noIds = votes.stream().filter(v -> v.getVote() == VoteType.NO).map(RaidVote::getMemberId).toList();
        List<Long> maybeIds = votes.stream().filter(v -> v.getVote() == VoteType.MAYBE).map(RaidVote::getMemberId).toList();
        List<Long> attendeeIds = d.attendeeIds();
        List<RaidParty> parties = d.parties();
        Map<Long, List<RaidPartyMember>> partyMembersMap = d.partyMembersByPartyId();
        Map<Long, String> nickMap = d.nickMap();

        RaidTarget t = r.getTarget();
        String label = t != null ? t.getName()
                : (r.getCategory() == RaidCategory.FANG ? "🐲 어금니 레이드"
                    : r.getCategory() == RaidCategory.SKULL_KING ? "💀 해골왕" : "레이드");
        String dropItemName = t != null ? t.getDropItemName()
                : (r.getCategory() == RaidCategory.FANG ? "흑/묵/감/진룡 어금니 (드랍 시 등록)"
                    : "-");

        StringBuilder desc = new StringBuilder();
        desc.append("**🎯 ").append(label).append("** · ")
                .append(dropItemName).append("\n")
                .append("**📅 ").append(r.getScheduledAt() != null ? r.getScheduledAt().format(FMT) : "⏳ 시간 미정 (하단 버튼으로 설정)").append("**\n");
        if (r.getMemo() != null && !r.getMemo().isBlank()) {
            desc.append("💬 ").append(r.getMemo()).append("\n");
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(title + " · " + label)
                .setColor(color)
                .setDescription(desc.toString());

        String yesNames = yesIds.isEmpty() ? "-" : yesIds.stream().map(id -> nickMap.getOrDefault(id, "#" + id)).collect(Collectors.joining(", "));
        String noNames = noIds.isEmpty() ? "-" : noIds.stream().map(id -> nickMap.getOrDefault(id, "#" + id)).collect(Collectors.joining(", "));
        String maybeNames = maybeIds.isEmpty() ? "-" : maybeIds.stream().map(id -> nickMap.getOrDefault(id, "#" + id)).collect(Collectors.joining(", "));

        eb.addField("✅ 참가 (" + yesIds.size() + ")", truncate(yesNames, 1000), false);
        eb.addField("❌ 불참 (" + noIds.size() + ")", truncate(noNames, 1000), false);
        eb.addField("❓ 미정 (" + maybeIds.size() + ")", truncate(maybeNames, 1000), false);

        // 참가확정: 파티가 있으면 파티 참가자 unique (external 포함, 중복 역할 (N) 표기)
        //           파티가 없으면 raid_attendees 로 fallback
        LinkedHashMap<String, Integer> partyPeople = collectPartyParticipants(parties, partyMembersMap, nickMap);
        if (!partyPeople.isEmpty()) {
            String names = partyPeople.entrySet().stream()
                    .map(e -> e.getValue() > 1 ? e.getKey() + " (" + e.getValue() + ")" : e.getKey())
                    .collect(Collectors.joining(", "));
            eb.addField("🎯 참가확정 " + partyPeople.size() + "명",
                    truncate(names, 1000), false);
        } else if (!attendeeIds.isEmpty()) {
            String names = attendeeIds.stream()
                    .map(id -> nickMap.getOrDefault(id, "#" + id))
                    .collect(Collectors.joining(", "));
            eb.addField("🎯 참가확정 " + attendeeIds.size() + "명",
                    truncate(names, 1000), false);
        }

        appendPartiesToEmbedShared(eb, parties, partyMembersMap, nickMap);
        appendLootsToEmbed(eb, d);

        String siteLink = props.getSiteBaseUrl() + "/raids/" + r.getId();
        eb.setFooter("사이트: " + siteLink);
        return eb.build();
    }

    /**
     * 파티들의 참가자를 unique 하게 집계 (memberId 또는 freeName 기준).
     * key = 표시명, value = 등장 횟수 (여러 역할/파티에 걸침).
     */
    private LinkedHashMap<String, Integer> collectPartyParticipants(
            List<RaidParty> parties,
            Map<Long, List<RaidPartyMember>> partyMembersMap,
            Map<Long, String> nickMap) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (RaidParty p : parties) {
            List<RaidPartyMember> pmembers = partyMembersMap.getOrDefault(p.getId(), List.of());
            for (RaidPartyMember m : pmembers) {
                String name;
                if (m.getMemberId() != null) {
                    name = nickMap.getOrDefault(m.getMemberId(), "#" + m.getMemberId());
                } else if (m.getFreeName() != null && !m.getFreeName().isBlank()) {
                    name = m.getFreeName();
                } else {
                    continue;
                }
                counts.merge(name, 1, Integer::sum);
            }
        }
        return counts;
    }

    private void appendPartiesToEmbedShared(
            EmbedBuilder eb,
            List<RaidParty> parties,
            Map<Long, List<RaidPartyMember>> membersByParty,
            Map<Long, String> nickMap) {
        if (parties.isEmpty()) return;

        for (RaidParty p : parties) {
            List<RaidPartyMember> ms = membersByParty.getOrDefault(p.getId(), List.of());
            String head = (p.getChannelType() == ChannelType.MAIN ? "본대" : "침략")
                    + (p.getChannelNumber() != null ? " · 채널 " + p.getChannelNumber() : "")
                    + (p.getMemo() != null && !p.getMemo().isBlank() ? " · " + p.getMemo() : "");

            StringBuilder body = new StringBuilder();
            String mike = p.getMikeMemberId() != null
                    ? nickMap.getOrDefault(p.getMikeMemberId(), "#" + p.getMikeMemberId())
                    : (p.getMikeFreeName() != null ? p.getMikeFreeName() : "-");
            body.append("🎤 마이크: ").append(mike).append("\n");

            Map<String, List<String>> byRole = new LinkedHashMap<>();
            Set<String> uniqueParticipants = new HashSet<>();
            for (RaidPartyMember m : ms) {
                String name;
                if (m.getMemberId() != null) {
                    name = nickMap.getOrDefault(m.getMemberId(), "#" + m.getMemberId());
                    uniqueParticipants.add("m:" + m.getMemberId());
                } else if (m.getFreeName() != null && !m.getFreeName().isBlank()) {
                    name = m.getFreeName();
                    uniqueParticipants.add("f:" + m.getFreeName());
                } else {
                    continue;
                }
                byRole.computeIfAbsent(m.getRole(), k -> new ArrayList<>()).add(name);
            }
            for (var e : byRole.entrySet()) {
                body.append("• ").append(e.getKey()).append(" (").append(e.getValue().size()).append("): ")
                        .append(String.join(", ", e.getValue())).append("\n");
            }
            // 총원 = unique 인원 수 (한 사람 여러 역할 시 1로 카운트)
            body.append("총원 ").append(uniqueParticipants.size()).append("명");
            eb.addField(head, truncate(body.toString(), 1000), false);
        }
    }

    private void appendLootsToEmbed(EmbedBuilder eb, RaidCardData d) {
        List<RaidLoot> loots = d.loots();
        if (loots.isEmpty()) return;
        Map<Long, String> nickMap = d.nickMap();
        Map<Long, List<LootShare>> sharesMap = d.sharesByLootId();
        StringBuilder sb = new StringBuilder();
        for (RaidLoot l : loots) {
            sb.append("• ").append(l.getItemName());
            if (l.isDropped()) {
                if (l.getSoldPrice() != null) {
                    sb.append(" · ").append(MONEY.format(l.getSoldPrice())).append("전");
                    List<LootShare> shares = sharesMap.getOrDefault(l.getId(), List.of());
                    long total = shares.size();
                    long paid = shares.stream().filter(LootShare::isPaid).count();
                    if (total > 0) sb.append(" (지급 ").append(paid).append("/").append(total).append(")");
                    if (l.getDistributedBy() != null && l.getDistributedAt() != null) {
                        String nick = nickMap.getOrDefault(l.getDistributedBy(), "#" + l.getDistributedBy());
                        sb.append(" · 분배 ").append(nick)
                                .append(" · ").append(l.getDistributedAt().format(FMT));
                    }
                }
            } else {
                sb.append(" (노드랍)");
            }
            sb.append("\n");
        }
        eb.addField("💰 득템", truncate(sb.toString(), 1000), false);
    }

    private List<ActionRow> buildRaidButtons(Raid r) {
        return buildRaidButtons(r, loadCardData(r));
    }

    private List<ActionRow> buildRaidButtons(Raid r, RaidCardData d) {
        String siteLink = props.getSiteBaseUrl() + "/raids/" + r.getId();
        if (r.getStatus() == RaidStatus.DONE) {
            List<RaidTarget> targets;
            if (r.getTarget() != null) {
                targets = List.of(r.getTarget());
            } else if (r.getCategory() != null) {
                targets = targetRepository.findByCategoryOrderByIdAsc(r.getCategory());
            } else {
                targets = List.of();
            }
            List<ActionRow> rows = new ArrayList<>();

            List<Button> row1 = new ArrayList<>();
            row1.add(Button.link(siteLink, "상세보기"));
            for (RaidTarget t : targets) {
                if (row1.size() >= 5) break;
                String label = "➕ " + t.getName() + " 추가";
                row1.add(Button.primary("loot:add:" + r.getId() + ":" + t.getId(), label));
            }
            rows.add(ActionRow.of(row1));

            // 캐시된 loots + shares 사용 (N+1 제거)
            List<RaidLoot> loots = d.loots();
            Map<Long, List<LootShare>> sharesMap = d.sharesByLootId();
            List<Button> lootBtns = new ArrayList<>();
            for (RaidLoot l : loots) {
                if (lootBtns.size() >= 20) break;
                String shortName = l.getItemName();
                if (shortName.length() > 10) shortName = shortName.substring(0, 10);
                boolean hasShares = !sharesMap.getOrDefault(l.getId(), List.of()).isEmpty();
                if (l.getSoldPrice() == null || l.getSoldPrice() <= 0) {
                    lootBtns.add(Button.primary("loot:price:" + l.getId(), "💵 " + shortName));
                } else if (hasShares) {
                    lootBtns.add(Button.secondary("loot:paid:" + l.getId(), "💰 " + shortName));
                } else {
                    lootBtns.add(Button.success("loot:distribute:" + l.getId(), "⚖️ " + shortName));
                }
            }
            for (int i = 0; i < lootBtns.size(); i += 5) {
                rows.add(ActionRow.of(lootBtns.subList(i, Math.min(i + 5, lootBtns.size()))));
                if (rows.size() >= 5) break;
            }
            return rows;
        }
        List<Button> voteRow = new ArrayList<>();
        voteRow.add(Button.success("raid:vote:" + r.getId() + ":YES", "참가"));
        voteRow.add(Button.danger("raid:vote:" + r.getId() + ":NO", "불참"));
        voteRow.add(Button.secondary("raid:vote:" + r.getId() + ":MAYBE", "미정"));
        voteRow.add(Button.link(siteLink, "상세보기"));
        List<ActionRow> plannedRows = new ArrayList<>();
        plannedRows.add(ActionRow.of(voteRow));
        // 시간 미정 raid: [🕐 시간 입력] 버튼 추가 행
        if (r.getScheduledAt() == null) {
            plannedRows.add(ActionRow.of(
                    Button.primary("raid:settime:" + r.getId(), "🕐 시간 입력")));
        }
        return plannedRows;
    }

    // ============================================================
    // Loot card: 득템 1건당 메시지 1개, 정산 상태 변경 시 편집
    // ============================================================

    @org.springframework.scheduling.annotation.Async("discordExecutor")
    public void syncLootCard(Long lootId, LootTrigger trigger) {
        try {
            if (inCooldown()) { log.warn("syncLootCard skipped (Discord 429 cooldown · {}s 남음, lootId={})", cooldownRemainingSec(), lootId); return; }
            RaidLoot l = lootRepository.findById(lootId).orElse(null);
            if (l == null) return;
            DiscordBotService bot = bot();
            if (bot == null || !bot.isReady()) return;
            TextChannel ch = bot.notifyChannel();
            if (ch == null) return;

            List<LootShare> shares = shareRepository.findByLootId(lootId);
            if (shares.isEmpty()) return; // 분배 안 됐으면 카드 안 보냄

            MessageEmbed embed = buildLootEmbed(l, shares);

            if (l.getDiscordMessageId() == null) {
                final Long lootIdF = lootId;
                ch.sendMessageEmbeds(embed).queue(msg -> {
                    lootRepository.updateDiscordMessageId(lootIdF, msg.getIdLong());
                }, err -> { noteRateLimitError(err); log.warn("loot card send failed: {}", err.toString()); });
            } else {
                ch.editMessageEmbedsById(l.getDiscordMessageId(), embed).queue(null,
                        err -> { noteRateLimitError(err); log.warn("loot card edit failed: {}", err.toString()); });
            }
        } catch (Exception e) {
            log.warn("syncLootCard error: {}", e.toString());
        }
    }

    private MessageEmbed buildLootEmbed(RaidLoot l, List<LootShare> shares) {
        Raid r = raidRepository.findById(l.getRaidId()).orElse(null);
        String raidLabel;
        if (r == null) {
            raidLabel = "레이드";
        } else if (r.getTarget() != null) {
            raidLabel = r.getTarget().getName();
        } else if (r.getCategory() == RaidCategory.FANG) {
            raidLabel = "어금니 레이드";
        } else {
            raidLabel = "레이드";
        }

        long paid = shares.stream().filter(LootShare::isPaid).count();
        long total = shares.size();
        boolean allPaid = paid == total;

        Color color = allPaid ? new Color(0x52C41A) : new Color(0xFA8C16);
        String title = (allPaid ? "✅ 정산 완료" : "💰 분배 진행중")
                + " · " + raidLabel + " · " + l.getItemName();

        Map<Long, String> nickMap = fetchNicks(shares.stream()
                .map(LootShare::getMemberId).toList());

        StringBuilder header = new StringBuilder();
        if (l.getSoldPrice() != null) {
            header.append("**판매금액**: ").append(MONEY.format(l.getSoldPrice())).append("전\n");
            if (total > 0) {
                header.append("**1인당**: ").append(MONEY.format(l.getSoldPrice() / total)).append("전\n");
            }
        }
        header.append("**정산**: ").append(paid).append(" / ").append(total)
                .append(" (").append(total > 0 ? (paid * 100 / total) : 0).append("%)");

        StringBuilder paidSb = new StringBuilder();
        StringBuilder unpaidSb = new StringBuilder();
        for (LootShare s : shares) {
            String name = nickMap.getOrDefault(s.getMemberId(), "#" + s.getMemberId());
            String line = "• " + name + " · " + MONEY.format(s.getShare()) + "전\n";
            if (s.isPaid()) paidSb.append(line);
            else unpaidSb.append(line);
        }

        String siteLink = props.getSiteBaseUrl() + "/raids/" + l.getRaidId();
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setDescription(header.toString());
        if (paidSb.length() > 0) eb.addField("✅ 정산 완료", truncate(paidSb.toString(), 1000), false);
        if (unpaidSb.length() > 0) eb.addField("❌ 미정산", truncate(unpaidSb.toString(), 1000), false);
        eb.setFooter("사이트: " + siteLink);
        return eb.build();
    }

    // ============================================================
    // Utilities
    // ============================================================

    private Map<Long, String> fetchNicks(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return memberRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Member::getId, Member::getNickname));
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private void sendRaidViaWebhook(Raid r) {
        try {
            List<RaidVote> votes = voteRepository.findByRaidId(r.getId());
            long yes = votes.stream().filter(v -> v.getVote() == VoteType.YES).count();
            long no = votes.stream().filter(v -> v.getVote() == VoteType.NO).count();
            long maybe = votes.stream().filter(v -> v.getVote() == VoteType.MAYBE).count();
            String siteLink = props.getSiteBaseUrl() + "/raids/" + r.getId();
            RaidTarget t = r.getTarget();
            String label = t != null ? t.getName()
                    : (r.getCategory() == RaidCategory.FANG ? "어금니 레이드" : "레이드");
            String dropItemName = t != null ? t.getDropItemName()
                    : (r.getCategory() == RaidCategory.FANG ? "흑/묵/감/진룡 어금니" : "-");
            Map<String, Object> embed = Map.of(
                    "title", "🆕 새 레이드 · " + label,
                    "description", "시간: " + (r.getScheduledAt() != null ? r.getScheduledAt().format(FMT) : "⏳ 시간 미정")
                            + "\n드랍: " + dropItemName
                            + (r.getMemo() == null || r.getMemo().isBlank() ? "" : "\n메모: " + r.getMemo())
                            + "\n\n✅ " + yes + " · ❌ " + no + " · ❓ " + maybe
                            + "\n\n[상세보기](" + siteLink + ")",
                    "color", 0x00B0FF
            );
            Map<String, Object> body = Map.of("embeds", List.of(embed));
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            rest.postForEntity(props.getWebhookUrl(), new HttpEntity<>(body, h), String.class);
        } catch (Exception e) {
            log.warn("Discord webhook send failed: {}", e.toString());
        }
    }

    // 하위 호환 (기존 호출부용 shim)
    @org.springframework.scheduling.annotation.Async("discordExecutor")
    public void notifyRaidCreated(Long raidId) { syncRaidCard(raidId, RaidTrigger.CREATED); }
    @org.springframework.scheduling.annotation.Async("discordExecutor")
    public void notifyRaidPre30(Long raidId) { syncRaidCard(raidId, RaidTrigger.PRE30); }

    /** 레이드 삭제 시 Discord 카드도 삭제 (있으면). */
    @org.springframework.scheduling.annotation.Async("discordExecutor")
    public void deleteRaidCard(Long discordMessageId) {
        if (discordMessageId == null) return;
        try {
            DiscordBotService b = bot();
            if (b == null || !b.isReady()) return;
            TextChannel ch = b.notifyChannel();
            if (ch == null) return;
            ch.deleteMessageById(discordMessageId).queue(null, err -> log.debug("delete raid card failed: {}", err.toString()));
        } catch (Exception e) {
            log.debug("deleteRaidCard error: {}", e.toString());
        }
    }

    /** notify 채널에 텍스트 메시지 발송 (알림 등 별도 메시지용). */
    @org.springframework.scheduling.annotation.Async("discordExecutor")
    public void postAlertMessage(String content) {
        try {
            if (inCooldown()) { log.warn("postAlertMessage skipped (Discord 429 cooldown · {}s 남음)", cooldownRemainingSec()); return; }
            DiscordBotService b = bot();
            if (b == null || !b.isReady()) return;
            TextChannel ch = b.notifyChannel();
            if (ch == null || content == null || content.isBlank()) return;
            String msg = content.length() > 1900 ? content.substring(0, 1897) + "..." : content;
            ch.sendMessage(msg).queue(null, err -> { noteRateLimitError(err); log.debug("alert send failed: {}", err.toString()); });
        } catch (Exception e) {
            log.debug("postAlertMessage error: {}", e.toString());
        }
    }

    // 30분 전 리마인더는 **새 메시지** 로 (알림 트리거를 위해)
    // 임베드는 레이드 카드와 동일 (참가/불참/미정 닉네임 · 파티 편성 · 득템 포함)
    @org.springframework.scheduling.annotation.Async("discordExecutor")
    public void postRaidPre30Fresh(Long raidId) {
        try {
            if (inCooldown()) { log.warn("postRaidPre30Fresh skipped (Discord 429 cooldown · {}s 남음, raidId={})", cooldownRemainingSec(), raidId); return; }
            DiscordBotService b = bot();
            if (b == null || !b.isReady()) return;
            TextChannel ch = b.notifyChannel();
            if (ch == null) return;
            Raid r = raidRepository.findById(raidId).orElse(null);
            if (r == null) return;
            long minsLeft = Math.max(0,
                    java.time.Duration.between(java.time.LocalDateTime.now(), r.getScheduledAt()).toMinutes());
            String label;
            if (r.getTarget() != null) {
                label = (r.getTarget().getIcon() != null ? r.getTarget().getIcon() + " " : "") + r.getTarget().getName();
            } else if (r.getCategory() == RaidCategory.FANG) {
                label = "🐲 어금니 레이드";
            } else if (r.getCategory() == RaidCategory.SKULL_KING) {
                label = "💀 해골왕 레이드";
            } else {
                label = "레이드";
            }
            String content = "@here ⏰ **곧 시작: " + label + "**"
                    + " · " + r.getScheduledAt().format(FMT)
                    + " (" + minsLeft + "분 뒤) · 준비 요망";
            MessageEmbed embed = buildRaidEmbed(r, RaidTrigger.PRE30);
            var buttons = buildRaidButtons(r);
            var buttonsArr = buttons.toArray(new net.dv8tion.jda.api.interactions.components.LayoutComponent[0]);
            Long raidId2 = r.getId();
            ch.sendMessage(content).setEmbeds(embed).setComponents(buttonsArr)
                    .queue(msg -> {
                        // targeted update (다른 필드 race 방지 · pre30Sent 반복 발송 버그 fix)
                        raidRepository.updateDiscordMessageId(raidId2, msg.getIdLong());
                    }, err -> { noteRateLimitError(err); log.debug("pre30 fresh send failed: {}", err.toString()); });
        } catch (Exception e) {
            log.debug("postRaidPre30Fresh error: {}", e.toString());
        }
    }
    @org.springframework.scheduling.annotation.Async("discordExecutor")
    public void updateEmbed(Long raidId) { syncRaidCard(raidId, RaidTrigger.VOTE); }
}
