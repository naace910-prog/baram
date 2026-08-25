package com.wind.guild.service;

import com.wind.guild.config.DiscordProperties;
import com.wind.guild.domain.Raid;
import com.wind.guild.domain.RaidVote;
import com.wind.guild.repository.RaidRepository;
import com.wind.guild.repository.RaidVoteRepository;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordNotifier {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM/dd(E) HH:mm");
    private final DiscordProperties props;
    private final ObjectProvider<DiscordBotService> botProvider;
    private final RaidRepository raidRepository;
    private final RaidVoteRepository voteRepository;
    private final RestTemplate rest = new RestTemplate();

    private DiscordBotService bot() { return botProvider.getIfAvailable(); }

    public void notifyRaidCreated(Long raidId) {
        Raid r = raidRepository.findById(raidId).orElse(null);
        if (r == null) return;
        sendEmbed(r, "🆕 새 레이드 등록", new Color(0x00B0FF));
    }

    public void notifyRaidPre30(Long raidId) {
        Raid r = raidRepository.findById(raidId).orElse(null);
        if (r == null) return;
        sendEmbed(r, "⏰ 30분 뒤 레이드 시작", new Color(0xFFA500));
    }

    private void sendEmbed(Raid r, String title, Color color) {
        List<RaidVote> votes = voteRepository.findByRaidId(r.getId());
        long yes = votes.stream().filter(v -> v.getVote().name().equals("YES")).count();
        long no = votes.stream().filter(v -> v.getVote().name().equals("NO")).count();
        long maybe = votes.stream().filter(v -> v.getVote().name().equals("MAYBE")).count();
        String siteLink = props.getSiteBaseUrl() + "/raids/" + r.getId();

        DiscordBotService bot = bot();
        if (bot != null && bot.isReady()) {
            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle(title + " · " + r.getTarget().getName())
                    .setColor(color)
                    .setDescription("**시간**: " + r.getScheduledAt().format(FMT) + "\n"
                            + "**드랍**: " + r.getTarget().getDropItemName() + "\n"
                            + (r.getMemo() == null || r.getMemo().isBlank() ? "" : "**메모**: " + r.getMemo() + "\n"))
                    .addField("참가", "✅ " + yes, true)
                    .addField("불참", "❌ " + no, true)
                    .addField("미정", "❓ " + maybe, true)
                    .setFooter("사이트: " + siteLink);
            TextChannel ch = bot.notifyChannel();
            if (ch != null) {
                ch.sendMessageEmbeds(eb.build())
                        .setComponents(ActionRow.of(
                                Button.success("raid:vote:" + r.getId() + ":YES", "참가"),
                                Button.danger("raid:vote:" + r.getId() + ":NO", "불참"),
                                Button.secondary("raid:vote:" + r.getId() + ":MAYBE", "미정"),
                                Button.link(siteLink, "상세보기")))
                        .queue(msg -> {
                            r.setDiscordMessageId(msg.getIdLong());
                            raidRepository.save(r);
                        }, err -> log.warn("Discord bot send failed: {}", err.toString()));
                return;
            }
        }

        if (props.getWebhookUrl() != null && !props.getWebhookUrl().isBlank()) {
            sendWebhook(title + " · " + r.getTarget().getName(), r, yes, no, maybe, siteLink);
        }
    }

    private void sendWebhook(String title, Raid r, long yes, long no, long maybe, String siteLink) {
        try {
            Map<String, Object> embed = Map.of(
                    "title", title,
                    "description", "시간: " + r.getScheduledAt().format(FMT)
                            + "\n드랍: " + r.getTarget().getDropItemName()
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

    public void updateEmbed(Long raidId) {
        Raid r = raidRepository.findById(raidId).orElse(null);
        if (r == null || r.getDiscordMessageId() == null) return;
        DiscordBotService bot = bot();
        if (bot == null || !bot.isReady()) return;
        TextChannel ch = bot.notifyChannel();
        if (ch == null) return;

        List<RaidVote> votes = voteRepository.findByRaidId(r.getId());
        long yes = votes.stream().filter(v -> v.getVote().name().equals("YES")).count();
        long no = votes.stream().filter(v -> v.getVote().name().equals("NO")).count();
        long maybe = votes.stream().filter(v -> v.getVote().name().equals("MAYBE")).count();
        String siteLink = props.getSiteBaseUrl() + "/raids/" + r.getId();

        ch.retrieveMessageById(r.getDiscordMessageId()).queue(msg -> {
            MessageEmbed old = msg.getEmbeds().isEmpty() ? null : msg.getEmbeds().get(0);
            String title = old != null ? old.getTitle() : "레이드";
            Color color = old != null && old.getColor() != null ? old.getColor() : new Color(0x00B0FF);
            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle(title)
                    .setColor(color)
                    .setDescription("**시간**: " + r.getScheduledAt().format(FMT) + "\n"
                            + "**드랍**: " + r.getTarget().getDropItemName() + "\n"
                            + (r.getMemo() == null || r.getMemo().isBlank() ? "" : "**메모**: " + r.getMemo() + "\n"))
                    .addField("참가", "✅ " + yes, true)
                    .addField("불참", "❌ " + no, true)
                    .addField("미정", "❓ " + maybe, true)
                    .setFooter("사이트: " + siteLink);
            msg.editMessageEmbeds(eb.build()).queue(null, err ->
                    log.warn("Discord edit failed: {}", err.toString()));
        }, err -> log.debug("Discord message fetch failed: {}", err.toString()));
    }

    public LocalDateTime now() { return LocalDateTime.now(); }
}
