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

    public enum RaidTrigger { CREATED, VOTE, ATTENDEES, PARTY, PRE30, STATUS, LOOT }
    public enum LootTrigger { DISTRIBUTED, PAID_CHANGED, PRICE_CHANGED }

    // ============================================================
    // Raid card: 한 레이드당 메시지 1개, 어떤 이벤트든 같은 카드 편집
    // ============================================================

    public void syncRaidCard(Long raidId, RaidTrigger trigger) {
        try {
            Raid r = raidRepository.findById(raidId).orElse(null);
            if (r == null) return;

            DiscordBotService bot = bot();
            if (bot != null && bot.isReady()) {
                TextChannel ch = bot.notifyChannel();
                if (ch == null) return;
                MessageEmbed embed = buildRaidEmbed(r, trigger);
                var buttons = buildRaidButtons(r);
                var buttonsArr = buttons.toArray(new net.dv8tion.jda.api.interactions.components.LayoutComponent[0]);

                if (r.getDiscordMessageId() == null) {
                    ch.sendMessageEmbeds(embed).setComponents(buttonsArr).queue(msg -> {
                        r.setDiscordMessageId(msg.getIdLong());
                        raidRepository.save(r);
                    }, err -> log.warn("raid card send failed: {}", err.toString()));
                } else {
                    ch.editMessageEmbedsById(r.getDiscordMessageId(), embed)
                            .setComponents(buttonsArr)
                            .queue(null, err -> {
                                log.warn("raid card edit failed (fallback to new send): {}", err.toString());
                                ch.sendMessageEmbeds(embed).setComponents(buttonsArr).queue(m -> {
                                    r.setDiscordMessageId(m.getIdLong());
                                    raidRepository.save(r);
                                });
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
            log.warn("syncRaidCard error: {}", e.toString());
        }
    }

    /**
     * 항상 **새 메시지** 로 카드를 발송하고, discordMessageId 를 새 것으로 갱신.
     * 사용: 레이드 등록·파티편성·득템 입력·분배·지급·리마인더 등 '완료' 성격 이벤트.
     * 이후 minor 이벤트 (투표 등) 는 syncRaidCard 로 새 카드에 편집.
     */
    public void syncRaidCardFresh(Long raidId, RaidTrigger trigger) {
        try {
            Raid r = raidRepository.findById(raidId).orElse(null);
            if (r == null) return;
            DiscordBotService bot = bot();
            if (bot == null || !bot.isReady()) {
                syncRaidCard(raidId, trigger); // fallback (webhook 등)
                return;
            }
            TextChannel ch = bot.notifyChannel();
            if (ch == null) return;
            MessageEmbed embed = buildRaidEmbed(r, trigger);
            var buttons = buildRaidButtons(r);
            var buttonsArr = buttons.toArray(new net.dv8tion.jda.api.interactions.components.LayoutComponent[0]);
            ch.sendMessageEmbeds(embed).setComponents(buttonsArr).queue(msg -> {
                r.setDiscordMessageId(msg.getIdLong());
                raidRepository.save(r);
            }, err -> log.warn("raid card fresh send failed: {}", err.toString()));
        } catch (Exception e) {
            log.warn("syncRaidCardFresh error: {}", e.toString());
        }
    }

    private MessageEmbed buildRaidEmbed(Raid r, RaidTrigger trigger) {
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
                    case VOTE -> { title = "📢 레이드 안내"; color = new Color(0x00B0FF); }
                    default -> { title = "📢 레이드 안내"; color = new Color(0x00B0FF); }
                }
            }
        }

        List<RaidVote> votes = voteRepository.findByRaidId(r.getId());
        List<Long> yesIds = votes.stream().filter(v -> v.getVote() == VoteType.YES).map(RaidVote::getMemberId).toList();
        List<Long> noIds = votes.stream().filter(v -> v.getVote() == VoteType.NO).map(RaidVote::getMemberId).toList();
        List<Long> maybeIds = votes.stream().filter(v -> v.getVote() == VoteType.MAYBE).map(RaidVote::getMemberId).toList();

        List<Long> attendeeIds = attendeeRepository.findByRaidId(r.getId()).stream()
                .map(RaidAttendee::getMemberId).toList();

        // 파티 참가자 memberId 도 nick 조회 대상에 포함
        List<RaidParty> parties = partyRepository.findByRaidIdOrderByDisplayOrderAsc(r.getId());
        Map<Long, List<RaidPartyMember>> partyMembersMap = new HashMap<>();
        for (RaidParty p : parties) {
            partyMembersMap.put(p.getId(), partyMemberRepository.findByPartyIdOrderByRoleAscDisplayOrderAsc(p.getId()));
        }

        Set<Long> refIds = new HashSet<>();
        refIds.addAll(yesIds); refIds.addAll(noIds); refIds.addAll(maybeIds); refIds.addAll(attendeeIds);
        for (RaidParty p : parties) {
            if (p.getMikeMemberId() != null) refIds.add(p.getMikeMemberId());
            for (RaidPartyMember m : partyMembersMap.get(p.getId())) {
                if (m.getMemberId() != null) refIds.add(m.getMemberId());
            }
        }
        Map<Long, String> nickMap = fetchNicks(refIds);

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
                .append("**📅 ").append(r.getScheduledAt().format(FMT)).append("**\n");
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
        appendLootsToEmbed(eb, r.getId());

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
            for (RaidPartyMember m : partyMembersMap.get(p.getId())) {
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
            List<RaidPartyMember> ms = membersByParty.get(p.getId());
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

    private void appendLootsToEmbed(EmbedBuilder eb, Long raidId) {
        List<RaidLoot> loots = lootRepository.findByRaidId(raidId);
        if (loots.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (RaidLoot l : loots) {
            sb.append("• ").append(l.getItemName());
            if (l.isDropped()) {
                if (l.getSoldPrice() != null) {
                    sb.append(" · ").append(MONEY.format(l.getSoldPrice())).append("전");
                    long total = shareRepository.findByLootId(l.getId()).size();
                    long paid = shareRepository.findByLootId(l.getId()).stream()
                            .filter(LootShare::isPaid).count();
                    if (total > 0) sb.append(" (정산 ").append(paid).append("/").append(total).append(")");
                }
            } else {
                sb.append(" (노드랍)");
            }
            sb.append("\n");
        }
        eb.addField("💰 득템", truncate(sb.toString(), 1000), false);
    }

    private List<ActionRow> buildRaidButtons(Raid r) {
        String siteLink = props.getSiteBaseUrl() + "/raids/" + r.getId();
        if (r.getStatus() == RaidStatus.DONE) {
            // 완료 상태: 상단 = [상세보기] + 대상별 [➕ 추가] · 하단 = 각 loot 라인 상태별 액션
            List<RaidTarget> targets;
            if (r.getTarget() != null) {
                targets = List.of(r.getTarget());
            } else if (r.getCategory() != null) {
                targets = targetRepository.findByCategoryOrderByIdAsc(r.getCategory());
            } else {
                targets = List.of();
            }
            List<ActionRow> rows = new ArrayList<>();

            // Row 1: 상세보기 + 대상별 [➕ 추가] (라벨 명확화: '드랍' → '추가')
            List<Button> row1 = new ArrayList<>();
            row1.add(Button.link(siteLink, "상세보기"));
            for (RaidTarget t : targets) {
                if (row1.size() >= 5) break;
                String label = "➕ " + t.getName() + " 추가";
                row1.add(Button.primary("loot:add:" + r.getId() + ":" + t.getId(), label));
            }
            rows.add(ActionRow.of(row1));

            // Row 2+: 각 loot 라인마다 상태별 액션 (최대 4행 x 5버튼 = 20개)
            List<RaidLoot> loots = lootRepository.findByRaidId(r.getId());
            List<Button> lootBtns = new ArrayList<>();
            for (RaidLoot l : loots) {
                if (lootBtns.size() >= 20) break;
                String shortName = l.getItemName();
                if (shortName.length() > 10) shortName = shortName.substring(0, 10);
                boolean hasShares = !shareRepository.findByLootId(l.getId()).isEmpty();
                if (hasShares) {
                    // 이미 분배됨: 정보만, disabled
                    lootBtns.add(Button.secondary("loot:done:" + l.getId(), "✅ " + shortName).asDisabled());
                } else if (l.getSoldPrice() == null || l.getSoldPrice() <= 0) {
                    // 판매금 없음
                    lootBtns.add(Button.primary("loot:price:" + l.getId(), "💵 " + shortName));
                } else {
                    // 판매금 있음, 미분배
                    lootBtns.add(Button.success("loot:distribute:" + l.getId(), "⚖️ " + shortName));
                }
            }
            // 5개씩 끊어서 ActionRow 분할
            for (int i = 0; i < lootBtns.size(); i += 5) {
                rows.add(ActionRow.of(lootBtns.subList(i, Math.min(i + 5, lootBtns.size()))));
                if (rows.size() >= 5) break;
            }
            return rows;
        }
        return List.of(ActionRow.of(
                Button.success("raid:vote:" + r.getId() + ":YES", "참가"),
                Button.danger("raid:vote:" + r.getId() + ":NO", "불참"),
                Button.secondary("raid:vote:" + r.getId() + ":MAYBE", "미정"),
                Button.link(siteLink, "상세보기")));
    }

    // ============================================================
    // Loot card: 득템 1건당 메시지 1개, 정산 상태 변경 시 편집
    // ============================================================

    public void syncLootCard(Long lootId, LootTrigger trigger) {
        try {
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
                ch.sendMessageEmbeds(embed).queue(msg -> {
                    l.setDiscordMessageId(msg.getIdLong());
                    lootRepository.save(l);
                }, err -> log.warn("loot card send failed: {}", err.toString()));
            } else {
                ch.editMessageEmbedsById(l.getDiscordMessageId(), embed).queue(null,
                        err -> log.warn("loot card edit failed: {}", err.toString()));
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
                    "description", "시간: " + r.getScheduledAt().format(FMT)
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
    public void notifyRaidCreated(Long raidId) { syncRaidCard(raidId, RaidTrigger.CREATED); }
    public void notifyRaidPre30(Long raidId) { syncRaidCard(raidId, RaidTrigger.PRE30); }

    // 30분 전 리마인더는 **새 메시지** 로 (알림 트리거를 위해)
    // 임베드는 레이드 카드와 동일 (참가/불참/미정 닉네임 · 파티 편성 · 득템 포함)
    public void postRaidPre30Fresh(Long raidId) {
        try {
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
            ch.sendMessage(content).setEmbeds(embed).setComponents(buttonsArr)
                    .queue(msg -> {
                        // 새 카드로 discordMessageId 갱신 → 이후 minor 편집은 새 카드에 반영
                        r.setDiscordMessageId(msg.getIdLong());
                        raidRepository.save(r);
                    }, err -> log.debug("pre30 fresh send failed: {}", err.toString()));
        } catch (Exception e) {
            log.debug("postRaidPre30Fresh error: {}", e.toString());
        }
    }
    public void updateEmbed(Long raidId) { syncRaidCard(raidId, RaidTrigger.VOTE); }
}
