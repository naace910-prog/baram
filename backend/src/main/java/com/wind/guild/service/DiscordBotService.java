package com.wind.guild.service;

import com.wind.guild.config.DiscordProperties;
import com.wind.guild.domain.RaidTarget;
import com.wind.guild.domain.VoteType;
import com.wind.guild.repository.RaidTargetRepository;
import com.wind.guild.web.dto.RaidDto;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordBotService extends ListenerAdapter {

    private final DiscordProperties props;
    private final RaidTargetRepository targetRepository;
    private final RaidService raidService;
    private final ObjectProvider<DiscordNotifier> notifierProvider;
    private final ObjectProvider<ChatService> chatServiceProvider;

    private JDA jda;

    private DiscordNotifier notifier() { return notifierProvider.getIfAvailable(); }
    private ChatService chatService() { return chatServiceProvider.getIfAvailable(); }

    @PostConstruct
    public void start() {
        if (!props.isEnabled() || props.getBotToken() == null || props.getBotToken().isBlank()) {
            log.info("Discord bot is disabled (set DISCORD_ENABLED=true and DISCORD_BOT_TOKEN to enable).");
            return;
        }
        // ⚠ 별도 스레드에서 초기화: JDA.awaitReady() 가 rate limit 등으로 오래 걸리면
        // Spring Boot 부팅이 블록되어 Tomcat 이 포트에 바인딩되지 않는 버그 회피
        Thread t = new Thread(this::connectInBackground, "discord-bot-init");
        t.setDaemon(true);
        t.start();
    }

    private void connectInBackground() {
        try {
            jda = JDABuilder.createDefault(props.getBotToken(),
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(this)
                    .build();
            jda.awaitReady();
            log.info("Discord bot ready as {}", jda.getSelfUser().getAsTag());
            registerCommands();
        } catch (Exception e) {
            log.error("Discord bot startup failed: {}", e.toString());
        }
    }

    private void registerCommands() {
        Guild guild = (props.getGuildId() != null && !props.getGuildId().isBlank())
                ? jda.getGuildById(props.getGuildId()) : null;
        var target = guild != null ? guild : null;
        var upsert = target != null
                ? target.updateCommands()
                : jda.updateCommands();
        upsert.addCommands(
                Commands.slash("레이드등록", "새 레이드 일정을 등록합니다")
                        .addOptions(
                                new OptionData(OptionType.STRING, "대상", "레이드 대상 (예: 해골왕)", true),
                                new OptionData(OptionType.STRING, "시간", "HH:mm 또는 MM/dd HH:mm", true),
                                new OptionData(OptionType.STRING, "메모", "선택", false)
                        ),
                Commands.slash("레이드목록", "다가오는 레이드 목록을 봅니다")
        ).queue();
    }

    public boolean isReady() { return jda != null && jda.getStatus() == JDA.Status.CONNECTED; }

    public TextChannel notifyChannel() {
        if (!isReady() || props.getNotifyChannelId() == null || props.getNotifyChannelId().isBlank()) return null;
        return jda.getTextChannelById(props.getNotifyChannelId());
    }

    public TextChannel chatChannel() {
        if (!isReady() || props.getChatChannelId() == null || props.getChatChannelId().isBlank()) return null;
        return jda.getTextChannelById(props.getChatChannelId());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
        try {
            switch (e.getName()) {
                case "레이드등록" -> handleCreateRaid(e);
                case "레이드목록" -> handleListRaid(e);
            }
        } catch (Exception ex) {
            log.warn("Slash command failed: {}", ex.toString());
            if (!e.isAcknowledged()) e.reply("에러: " + ex.getMessage()).setEphemeral(true).queue();
        }
    }

    private void handleCreateRaid(SlashCommandInteractionEvent e) {
        String targetName = e.getOption("대상").getAsString();
        String timeStr = e.getOption("시간").getAsString();
        String memo = e.getOption("메모") != null ? e.getOption("메모").getAsString() : null;

        RaidTarget target = targetRepository.findByName(targetName).orElse(null);
        if (target == null) {
            List<String> known = targetRepository.findAll().stream().map(RaidTarget::getName).toList();
            e.reply("대상 '" + targetName + "'을 찾을 수 없습니다. 등록된 대상: " + known)
                    .setEphemeral(true).queue();
            return;
        }
        LocalDateTime when;
        try {
            when = parseWhen(timeStr);
        } catch (Exception ex) {
            e.reply("시간 형식이 잘못됐습니다. HH:mm 또는 MM/dd HH:mm").setEphemeral(true).queue();
            return;
        }
        var raid = raidService.create(new RaidDto.CreateRequest(target.getId(), when, memo));
        e.reply("레이드 등록됨: " + target.getName() + " " + when.format(DateTimeFormatter.ofPattern("MM/dd HH:mm")))
                .setEphemeral(true).queue();
        DiscordNotifier n = notifier(); if (n != null) n.notifyRaidCreated(raid.getId());
    }

    private LocalDateTime parseWhen(String s) {
        s = s.trim();
        LocalDateTime now = LocalDateTime.now();
        if (s.matches("\\d{1,2}:\\d{2}")) {
            LocalTime t = LocalTime.parse(s.length() == 4 ? "0" + s : s);
            LocalDateTime dt = now.withHour(t.getHour()).withMinute(t.getMinute()).withSecond(0).withNano(0);
            if (dt.isBefore(now)) dt = dt.plusDays(1);
            return dt;
        }
        String[] parts = s.split("\\s+");
        String[] md = parts[0].split("/");
        String[] hm = parts[1].split(":");
        int month = Integer.parseInt(md[0]);
        int day = Integer.parseInt(md[1]);
        int h = Integer.parseInt(hm[0]);
        int m = Integer.parseInt(hm[1]);
        LocalDateTime dt = LocalDateTime.of(now.getYear(), month, day, h, m);
        if (dt.isBefore(now.minusDays(1))) dt = dt.plusYears(1);
        return dt;
    }

    private void handleListRaid(SlashCommandInteractionEvent e) {
        var list = raidService.list();
        if (list.isEmpty()) { e.reply("등록된 레이드가 없습니다").setEphemeral(true).queue(); return; }
        StringBuilder sb = new StringBuilder();
        list.stream().limit(10).forEach(v -> sb.append("• [")
                .append(v.status()).append("] ")
                .append(v.targetName()).append(" ")
                .append(v.scheduledAt().format(DateTimeFormatter.ofPattern("MM/dd HH:mm")))
                .append(" (✅").append(v.yesCount()).append(" ❌").append(v.noCount())
                .append(" ❓").append(v.maybeCount()).append(")\n"));
        e.reply(sb.toString()).setEphemeral(true).queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent e) {
        String id = e.getComponentId();
        if (!id.startsWith("raid:vote:")) return;
        String[] parts = id.split(":");
        try {
            Long raidId = Long.parseLong(parts[2]);
            VoteType vote = VoteType.valueOf(parts[3]);
            String discordUserId = e.getUser().getId();
            raidService.voteByDiscordUser(raidId, discordUserId, vote);
            e.reply("투표 반영됨: " + vote).setEphemeral(true).queue();
            DiscordNotifier n = notifier(); if (n != null) n.updateEmbed(raidId);
        } catch (IllegalStateException ex) {
            e.reply("⚠ " + ex.getMessage()).setEphemeral(true).queue();
        } catch (Exception ex) {
            log.warn("Button interaction failed: {}", ex.toString());
            e.reply("에러: " + ex.getMessage()).setEphemeral(true).queue();
        }
    }

    // 자연어 파서: `해골왕 등록` `08/25 21:00 흑룡` `20260825 21시 진룡 등록해줘`
    private static final Pattern P_DATETIME = Pattern.compile("(\\d{4})[-./]?(\\d{2})[-./]?(\\d{2})");
    private static final Pattern P_MMDD = Pattern.compile("(\\d{1,2})[/월-](\\d{1,2})[일]?");
    private static final Pattern P_TIME = Pattern.compile("(\\d{1,2})[:시](\\d{2})?");
    private static final Pattern P_TARGET = Pattern.compile("(해골왕|흑룡|감룡|묵룡|진룡)");

    @Override
    public void onMessageReceived(MessageReceivedEvent e) {
        if (e.getAuthor().isBot()) return;
        String channelId = e.getChannel().getId();
        String raw = e.getMessage().getContentRaw();
        if (raw == null || raw.isBlank()) return;

        // 채팅 채널 메시지 → 사이트 채팅으로 브릿지
        if (props.getChatChannelId() != null && !props.getChatChannelId().isBlank()
                && channelId.equals(props.getChatChannelId())) {
            handleChatChannelMessage(e, raw);
            return;
        }

        // 알림 채널의 자연어 명령 파싱
        if (props.getNotifyChannelId() == null || props.getNotifyChannelId().isBlank()) return;
        if (!channelId.equals(props.getNotifyChannelId())) return;
        if (!raw.contains("등록") && !raw.contains("잡자") && !raw.contains("가자")) return;

        try {
            var target = extractTarget(raw);
            if (target == null) return;
            LocalDateTime when = extractDateTime(raw);
            if (when == null) return;
            var raid = raidService.create(new RaidDto.CreateRequest(target.getId(), when, null));
            e.getMessage().reply("✅ 레이드 등록됨: " + target.getName() + " · "
                    + when.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
                    + "\n" + props.getSiteBaseUrl() + "/raids/" + raid.getId()).queue();
            DiscordNotifier n = notifier();
            if (n != null) n.notifyRaidCreated(raid.getId());
        } catch (Exception ex) {
            log.debug("자연어 파싱 실패 (무시): {}", ex.toString());
        }
    }

    private void handleChatChannelMessage(MessageReceivedEvent e, String content) {
        // "**닉네임** (사이트): ..." 형태는 우리가 relay 한 것이므로 다시 저장하지 않음
        if (content.startsWith("**") && content.contains("(사이트):")) return;
        try {
            ChatService cs = chatService();
            if (cs == null) return;
            String nick = e.getMember() != null && e.getMember().getEffectiveName() != null
                    ? e.getMember().getEffectiveName()
                    : e.getAuthor().getName();
            cs.saveFromDiscord(content, e.getAuthor().getId(), nick, e.getMessageIdLong());
        } catch (Exception ex) {
            log.debug("chat ingest 실패: {}", ex.toString());
        }
    }

    private RaidTarget extractTarget(String text) {
        Matcher m = P_TARGET.matcher(text);
        if (!m.find()) return null;
        return targetRepository.findByName(m.group(1)).orElse(null);
    }

    private LocalDateTime extractDateTime(String text) {
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear(), month = now.getMonthValue(), day = now.getDayOfMonth();
        boolean dateFound = false;

        Matcher dt = P_DATETIME.matcher(text);
        if (dt.find()) {
            year = Integer.parseInt(dt.group(1));
            month = Integer.parseInt(dt.group(2));
            day = Integer.parseInt(dt.group(3));
            dateFound = true;
        } else {
            Matcher md = P_MMDD.matcher(text);
            if (md.find()) {
                month = Integer.parseInt(md.group(1));
                day = Integer.parseInt(md.group(2));
                dateFound = true;
            }
        }
        Matcher tm = P_TIME.matcher(text);
        if (!tm.find()) return null;
        int hour = Integer.parseInt(tm.group(1));
        int minute = tm.group(2) != null ? Integer.parseInt(tm.group(2)) : 0;

        LocalDateTime result = LocalDateTime.of(year, month, day, hour, minute);
        if (!dateFound && result.isBefore(now)) result = result.plusDays(1);
        return result;
    }

    @PreDestroy
    public void stop() {
        if (jda != null) jda.shutdown();
    }
}
