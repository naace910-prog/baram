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
    private final RestTemplate rest = new RestTemplate();

    private DiscordBotService bot() { return botProvider.getIfAvailable(); }

    public enum RaidTrigger { CREATED, VOTE, ATTENDEES, PARTY, PRE30, STATUS }
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
                ActionRow buttons = buildRaidButtons(r);

                if (r.getDiscordMessageId() == null) {
                    ch.sendMessageEmbeds(embed).setComponents(buttons).queue(msg -> {
                        r.setDiscordMessageId(msg.getIdLong());
                        raidRepository.save(r);
                    }, err -> log.warn("raid card send failed: {}", err.toString()));
                } else {
                    ch.editMessageEmbedsById(r.getDiscordMessageId(), embed)
                            .setComponents(buttons)
                            .queue(null, err -> {
                                log.warn("raid card edit failed (fallback to new send): {}", err.toString());
                                ch.sendMessageEmbeds(embed).setComponents(buttons).queue(m -> {
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
                    case VOTE -> { title = "📢 레이드 안내"; color = new Color(0x00B0FF); }
                    default -> { title = "📢 레이드 안내"; color = new Color(0x00B0FF); }
                }
            }
        }

        List<RaidVote> votes = voteRepository.findByRaidId(r.getId());
        long yes = votes.stream().filter(v -> v.getVote() == VoteType.YES).count();
        long no = votes.stream().filter(v -> v.getVote() == VoteType.NO).count();
        long maybe = votes.stream().filter(v -> v.getVote() == VoteType.MAYBE).count();

        List<Long> attendeeIds = attendeeRepository.findByRaidId(r.getId()).stream()
                .map(RaidAttendee::getMemberId).toList();
        Map<Long, String> nickMap = fetchNicks(attendeeIds);

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
                .setDescription(desc.toString())
                .addField("✅ 참가", String.valueOf(yes), true)
                .addField("❌ 불참", String.valueOf(no), true)
                .addField("❓ 미정", String.valueOf(maybe), true);

        if (!attendeeIds.isEmpty()) {
            String names = attendeeIds.stream()
                    .map(id -> nickMap.getOrDefault(id, "#" + id))
                    .collect(Collectors.joining(", "));
            eb.addField("🎯 참가확정 " + attendeeIds.size() + "명",
                    truncate(names, 1000), false);
        }

        appendPartiesToEmbed(eb, r.getId());
        appendLootsToEmbed(eb, r.getId());

        String siteLink = props.getSiteBaseUrl() + "/raids/" + r.getId();
        eb.setFooter("사이트: " + siteLink);
        return eb.build();
    }

    private void appendPartiesToEmbed(EmbedBuilder eb, Long raidId) {
        List<RaidParty> parties = partyRepository.findByRaidIdOrderByDisplayOrderAsc(raidId);
        if (parties.isEmpty()) return;

        Set<Long> refIds = new HashSet<>();
        Map<Long, List<RaidPartyMember>> membersByParty = new HashMap<>();
        for (RaidParty p : parties) {
            List<RaidPartyMember> ms = partyMemberRepository.findByPartyIdOrderByRoleAscDisplayOrderAsc(p.getId());
            membersByParty.put(p.getId(), ms);
            if (p.getMikeMemberId() != null) refIds.add(p.getMikeMemberId());
            ms.forEach(m -> { if (m.getMemberId() != null) refIds.add(m.getMemberId()); });
        }
        Map<Long, String> nickMap = fetchNicks(refIds);

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
            for (RaidPartyMember m : ms) {
                String name = m.getMemberId() != null
                        ? nickMap.getOrDefault(m.getMemberId(), "#" + m.getMemberId())
                        : m.getFreeName();
                byRole.computeIfAbsent(m.getRole(), k -> new ArrayList<>()).add(name);
            }
            for (var e : byRole.entrySet()) {
                body.append("• ").append(e.getKey()).append(" (").append(e.getValue().size()).append("): ")
                        .append(String.join(", ", e.getValue())).append("\n");
            }
            body.append("총원 ").append(ms.size()).append("명");
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

    private ActionRow buildRaidButtons(Raid r) {
        String siteLink = props.getSiteBaseUrl() + "/raids/" + r.getId();
        return ActionRow.of(
                Button.success("raid:vote:" + r.getId() + ":YES", "참가"),
                Button.danger("raid:vote:" + r.getId() + ":NO", "불참"),
                Button.secondary("raid:vote:" + r.getId() + ":MAYBE", "미정"),
                Button.link(siteLink, "상세보기"));
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
    public void updateEmbed(Long raidId) { syncRaidCard(raidId, RaidTrigger.VOTE); }
}
