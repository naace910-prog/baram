package com.wind.guild.service;

import com.wind.guild.config.DiscordProperties;
import com.wind.guild.domain.RaidCategory;
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
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
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
    private final com.wind.guild.repository.MemberRepository memberRepository;
    private final com.wind.guild.repository.RaidRepository raidRepository;
    private final com.wind.guild.repository.RaidAttendeeRepository attendeeRepository;
    private final com.wind.guild.repository.RaidLootRepository lootRepository;
    private final com.wind.guild.repository.LootShareRepository shareRepository;
    private final com.wind.guild.repository.RaidPartyRepository partyRepositoryLazy;
    private final com.wind.guild.repository.RaidPartyMemberRepository partyMemberRepositoryLazy;
    private final RaidService raidService;
    private final ObjectProvider<DiscordNotifier> notifierProvider;
    private final ObjectProvider<ChatService> chatServiceProvider;
    private final ObjectProvider<LootService> lootServiceProvider;

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
            // 서버 기동 완료 알림
            TextChannel ch = notifyChannel();
            if (ch != null) {
                String msg = "🟢 **서버 기동 완료 " + com.wind.guild.config.AppVersion.VERSION + "** · "
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd HH:mm:ss"))
                        + "\n\n**이번 배포 변경사항**\n" + com.wind.guild.config.AppVersion.CHANGELOG;
                ch.sendMessage(msg).queue(null, err -> log.debug("boot notify send failed: {}", err.toString()));
            }
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
                Commands.slash("레이드목록", "예정 레이드 목록 · 참가자 상세"),
                Commands.slash("레이드결과", "최근 완료 레이드 2건 · 참가자·득템·정산"),
                Commands.slash("닉네임변경", "본인 닉네임 변경 (사이트와 동기화)")
                        .addOptions(new OptionData(OptionType.STRING, "닉네임", "새 닉네임 (1~40자)", true)),
                Commands.slash("도움말", "바람클래식-개화 문파 시스템 전 기능 안내")
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
        // 3초 내 응답 규칙 회피: 즉시 defer 후 hook 으로 응답
        e.deferReply(true).queue();
        try {
            switch (e.getName()) {
                case "레이드등록" -> handleCreateRaid(e);
                case "레이드목록" -> handleListRaid(e);
                case "레이드결과" -> handleRaidResults(e);
                case "닉네임변경" -> handleChangeNickname(e);
                case "도움말" -> handleHelp(e);
            }
        } catch (Exception ex) {
            log.warn("Slash command failed: {}", ex.toString());
            try { e.getHook().sendMessage("에러: " + ex.getMessage()).setEphemeral(true).queue(); } catch (Exception ignore) {}
        }
    }

    private void handleCreateRaid(SlashCommandInteractionEvent e) {
        if (!isMasterByDiscord(e.getUser().getId())) {
            e.getHook().sendMessage("문주(부문주)만 레이드 등록 가능합니다. Discord ID 가 문파원에 연결돼있는지 확인해주세요.")
                    .setEphemeral(true).queue();
            return;
        }
        String targetName = e.getOption("대상").getAsString();
        String timeStr = e.getOption("시간").getAsString();
        String memo = e.getOption("메모") != null ? e.getOption("메모").getAsString() : null;

        var parsed = resolveTargetOrCategory(targetName);
        if (parsed == null) {
            List<String> known = targetRepository.findAll().stream().map(RaidTarget::getName).toList();
            e.getHook().sendMessage("대상 '" + targetName + "' 을 찾을 수 없습니다.\n"
                    + "등록된 대상: " + known + "\n"
                    + "또는 카테고리 키워드: '해골왕' / '어금니' / '용' / '룡'").setEphemeral(true).queue();
            return;
        }
        LocalDateTime when;
        try {
            when = parseWhen(timeStr);
        } catch (Exception ex) {
            e.getHook().sendMessage("시간 형식이 잘못됐습니다. HH:mm 또는 MM/dd HH:mm").setEphemeral(true).queue();
            return;
        }
        var raid = raidService.create(new RaidDto.CreateRequest(
                parsed.category(),
                parsed.target() != null ? parsed.target().getId() : null,
                when, memo));
        String label = parsed.target() != null ? parsed.target().getName()
                : (parsed.category() == RaidCategory.FANG ? "🐲 어금니 레이드" : "레이드");
        e.getHook().sendMessage("레이드 등록됨: " + label + " · " + when.format(DateTimeFormatter.ofPattern("MM/dd HH:mm")))
                .setEphemeral(true).queue();
        DiscordNotifier n = notifier(); if (n != null) n.notifyRaidCreated(raid.getId());
    }

    private void handleRaidResults(SlashCommandInteractionEvent e) {
        var done = raidRepository.findAllByOrderByScheduledAtDesc().stream()
                .filter(r -> r.getStatus() == com.wind.guild.domain.RaidStatus.DONE)
                .limit(2)
                .toList();
        if (done.isEmpty()) {
            e.getHook().sendMessage("완료된 레이드가 아직 없습니다").setEphemeral(true).queue();
            return;
        }
        java.util.Set<Long> mids = new java.util.HashSet<>();
        for (var r : done) attendeeRepository.findByRaidId(r.getId())
                .forEach(a -> mids.add(a.getMemberId()));
        java.util.Map<Long, String> nickMap = memberRepositoryFindAllById(mids);

        StringBuilder sb = new StringBuilder("📊 **최근 완료 레이드** ").append(done.size()).append("건\n\n");
        for (var r : done) {
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
            sb.append("── ").append(label).append(" · ")
                    .append(r.getScheduledAt().format(DateTimeFormatter.ofPattern("MM/dd(E) HH:mm", java.util.Locale.KOREAN)))
                    .append(" ──\n");

            var attendees = attendeeRepository.findByRaidId(r.getId()).stream()
                    .map(a -> nickMap.getOrDefault(a.getMemberId(), "#" + a.getMemberId())).toList();
            if (!attendees.isEmpty()) {
                sb.append("🎯 참가확정 (").append(attendees.size()).append("): ")
                        .append(String.join(", ", attendees)).append("\n");
            }

            var loots = lootRepository.findByRaidId(r.getId());
            if (loots.isEmpty()) {
                sb.append("💰 득템 없음\n\n");
                continue;
            }
            long soldTotal = 0, unpaidTotal = 0, paidTotalAmt = 0;
            int totalShares = 0, paidShares = 0;
            sb.append("💰 득템 & 판매:\n");
            for (var l : loots) {
                var shares = shareRepository.findByLootId(l.getId());
                int paid = (int) shares.stream().filter(com.wind.guild.domain.LootShare::isPaid).count();
                totalShares += shares.size();
                paidShares += paid;
                String prefix = l.getTargetId() != null
                        ? targetRepository.findById(l.getTargetId()).map(t -> t.getIcon() != null ? t.getIcon() + " " : "").orElse("")
                        : "";
                sb.append("  · ").append(prefix).append(l.getItemName());
                if (l.getSoldPrice() != null) {
                    sb.append(" · ").append(String.format("%,d", l.getSoldPrice())).append("전");
                    soldTotal += l.getSoldPrice();
                } else {
                    sb.append(" · 미판매");
                }
                if (!shares.isEmpty()) sb.append(" (정산 ").append(paid).append("/").append(shares.size()).append(")");
                sb.append("\n");
                paidTotalAmt += shares.stream()
                        .filter(com.wind.guild.domain.LootShare::isPaid)
                        .mapToLong(com.wind.guild.domain.LootShare::getShare).sum();
                unpaidTotal += shares.stream()
                        .filter(s -> !s.isPaid())
                        .mapToLong(com.wind.guild.domain.LootShare::getShare).sum();
            }
            sb.append("→ 판매 총액 ").append(String.format("%,d", soldTotal)).append("전")
                    .append(" · 정산 ").append(paidShares).append("/").append(totalShares);
            if (unpaidTotal > 0) {
                sb.append(" · ⚠️ 미정산 ").append(String.format("%,d", unpaidTotal)).append("전");
            }
            sb.append("\n\n");
        }
        String out = sb.toString();
        if (out.length() > 1900) out = out.substring(0, 1897) + "...";
        e.getHook().sendMessage(out).setEphemeral(true).queue();
    }

    private void handleChangeNickname(SlashCommandInteractionEvent e) {
        String discordId = e.getUser().getId();
        var found = memberRepository.findByDiscordUserId(discordId);
        if (found.isEmpty()) {
            e.getHook().sendMessage("이 Discord 계정이 문파원에 연결되지 않았습니다.\n"
                    + "사이트에서 'Discord로 로그인' 하거나 문주에게 등록 요청하세요.").setEphemeral(true).queue();
            return;
        }
        String newNick = e.getOption("닉네임").getAsString();
        try {
            var m = found.get();
            String trimmed = newNick == null ? "" : newNick.trim();
            if (trimmed.isEmpty()) { e.getHook().sendMessage("닉네임 입력 필요").setEphemeral(true).queue(); return; }
            if (trimmed.length() > 40) { e.getHook().sendMessage("40자 이내로 입력").setEphemeral(true).queue(); return; }
            String before = m.getNickname();
            m.setNickname(trimmed);
            memberRepository.save(m);
            e.getHook().sendMessage("✅ 닉네임 변경: " + before + " → " + trimmed).setEphemeral(true).queue();
        } catch (Exception ex) {
            e.getHook().sendMessage("변경 실패: " + ex.getMessage()).setEphemeral(true).queue();
        }
    }

    private void handleHelp(SlashCommandInteractionEvent e) {
        String base = props.getSiteBaseUrl();
        String site = (base == null || base.isBlank()) ? "" : base;

        StringBuilder sb = new StringBuilder();
        sb.append("🌸 **바람클래식-개화 문파 시스템 안내 ").append(com.wind.guild.config.AppVersion.VERSION).append("**\n");
        if (!site.isEmpty()) sb.append("🔗 사이트: ").append(site).append("\n");

        sb.append("\n**🎮 Discord 슬래시 커맨드**\n");
        sb.append("• `/레이드등록 대상:<이름> 시간:<HH:mm or MM/dd HH:mm> [메모]` — 문주/부문주만\n");
        sb.append("  · 대상 예시: 해골왕 · 흑룡 · 감룡 · 묵룡 · 진룡\n");
        sb.append("  · 키워드도 가능: `해골왕` · `어금니` · `용` · `룡`\n");
        sb.append("• `/레이드목록` — 예정 레이드 · 시간·참가자 상세\n");
        sb.append("• `/레이드결과` — 최근 완료 레이드 2건 · 참가자·득템·정산 내역\n");
        sb.append("• `/닉네임변경 닉네임:<새 닉네임>` — 사이트와 동기화\n");
        sb.append("• `/도움말` — 이 안내\n");

        sb.append("\n**🌐 사이트 기능** (Discord 로그인 or 계정)\n");
        sb.append("• **레이드 목록·상세** — 투표(참가/불참/미정) · 실시간 참가자 명단\n");
        sb.append("• **레이드 등록** (문주/부문주) — 대상·시간·메모 · 1분 단위 시간\n");
        sb.append("• **파티 편성** — 드래그앤드롭 · YES 자동배치 · **🤖 직전 파티 승계** (같은 대상 최근 편성 그대로, 현재 참가자만)\n");
        sb.append("• **득템·판매금·분배** — 드랍 대량입력 (수량+1개당가) → 각 득템 `분배` (계산) → `지급` 스위치 (실제 이체 표시)\n");
        sb.append("• **채팅** — 실시간 · 사이트 ↔ Discord 자동 릴레이 · ⭐ 중요 문주 강조\n");
        sb.append("• **문파원 관리** (문주/부문주) — 역할·별표·비번초기화·활성/비활성\n");
        sb.append("• **통계** — 참가율·득템·분배 집계\n");
        sb.append("• **설정** — 푸시 알림 · 닉네임 · (문주) 데이터 초기화\n");

        sb.append("\n**🔔 자동 알림**\n");
        sb.append("• 서버 기동 완료 · 재배포마다 변경사항 함께 전송\n");
        sb.append("• 레이드 30분 전 리마인더 (Discord + 웹 푸시)\n");
        sb.append("• 레이드 자동 완료 (예정 시각 30분 초과 시)\n");
        sb.append("• 분배금 지급 시 대상자에게 push + 채팅\n");

        sb.append("\n**👑 권한**\n");
        sb.append("• 문주(MASTER) · 부문주(VICE) · 일반(MEMBER)\n");
        sb.append("• 문주 중 **⭐ 중요별표** 는 채팅/디스코드에서 별도 강조 (문주만 지정 가능)\n");
        sb.append("• 레이드 등록 · 파티 편성 · 문파원 관리 · 분배/지급 = 문주/부문주\n");

        sb.append("\n**📱 모바일 · PWA**\n");
        sb.append("• 브라우저 메뉴 → **홈 화면에 추가** → 앱처럼 실행\n");
        sb.append("• iOS 는 홈화면 추가 후 Safari 16.4+ 에서 웹 푸시 가능\n");

        e.getHook().sendMessage(sb.toString()).setEphemeral(true).queue();
    }

    private boolean isMasterByDiscord(String discordUserId) {
        if (discordUserId == null) return false;
        return memberRepository.findByDiscordUserId(discordUserId)
                .map(m -> m.getRole() == com.wind.guild.domain.MemberRole.MASTER
                        || m.getRole() == com.wind.guild.domain.MemberRole.VICE)
                .orElse(false);
    }

    private record TargetParse(RaidTarget target, RaidCategory category) {}

    private TargetParse resolveTargetOrCategory(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.equals("어금니") || s.equals("용") || s.equals("룡")) {
            return new TargetParse(null, RaidCategory.FANG);
        }
        if (s.equals("해골") || s.equals("해골왕")) {
            RaidTarget t = targetRepository.findByName("해골왕").orElse(null);
            return new TargetParse(t, RaidCategory.SKULL_KING);
        }
        RaidTarget t = targetRepository.findByName(s).orElse(null);
        if (t != null) return new TargetParse(t, t.getCategory());
        return null;
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
        var planned = raidService.list().stream()
                .filter(v -> v.status() == com.wind.guild.domain.RaidStatus.PLANNED)
                .sorted(java.util.Comparator.comparing(RaidDto.ListView::scheduledAt))
                .toList();
        if (planned.isEmpty()) { e.getHook().sendMessage("예정 레이드가 없습니다").setEphemeral(true).queue(); return; }

        // 참가확정 memberId → nickname 매핑
        java.util.Set<Long> attendeeIds = new java.util.HashSet<>();
        planned.forEach(v -> attendeeIds.addAll(v.attendees()));
        java.util.Map<Long, String> nickMap = memberRepository != null
                ? memberRepositoryFindAllById(attendeeIds)
                : java.util.Map.of();

        StringBuilder sb = new StringBuilder();
        sb.append("🎯 **예정 레이드** ").append(planned.size()).append("건\n\n");
        for (var v : planned) {
            String label;
            if (v.targetName() != null) {
                label = (v.targetIcon() != null ? v.targetIcon() + " " : "") + v.targetName();
            } else if (v.category() == RaidCategory.FANG) {
                label = "🐲 어금니 레이드";
            } else if (v.category() == RaidCategory.SKULL_KING) {
                label = "💀 해골왕 레이드";
            } else {
                label = "레이드";
            }
            sb.append("── ").append(label).append(" · ")
                    .append(v.scheduledAt().format(DateTimeFormatter.ofPattern("MM/dd(E) HH:mm", java.util.Locale.KOREAN)))
                    .append("\n");

            var yes = v.votes().stream().filter(x -> x.vote() == VoteType.YES).map(RaidDto.VoteView::nickname).toList();
            var no = v.votes().stream().filter(x -> x.vote() == VoteType.NO).map(RaidDto.VoteView::nickname).toList();
            var maybe = v.votes().stream().filter(x -> x.vote() == VoteType.MAYBE).map(RaidDto.VoteView::nickname).toList();
            if (!yes.isEmpty()) sb.append("  ✅ 참가 (").append(yes.size()).append("): ").append(String.join(", ", yes)).append("\n");
            if (!no.isEmpty()) sb.append("  ❌ 불참 (").append(no.size()).append("): ").append(String.join(", ", no)).append("\n");
            if (!maybe.isEmpty()) sb.append("  ❓ 미정 (").append(maybe.size()).append("): ").append(String.join(", ", maybe)).append("\n");
            if (!v.attendees().isEmpty()) {
                var names = v.attendees().stream().map(id -> nickMap.getOrDefault(id, "#" + id)).toList();
                sb.append("  🎯 참가확정 (").append(names.size()).append("): ").append(String.join(", ", names)).append("\n");
            }
            sb.append("\n");
        }
        String out = sb.toString();
        if (out.length() > 1900) out = out.substring(0, 1897) + "...";
        e.getHook().sendMessage(out).setEphemeral(true).queue();
    }

    private java.util.Map<Long, String> memberRepositoryFindAllById(java.util.Set<Long> ids) {
        if (ids.isEmpty()) return java.util.Map.of();
        return memberRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.wind.guild.domain.Member::getId,
                        com.wind.guild.domain.Member::getNickname));
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent e) {
        String id = e.getComponentId();
        if (id.startsWith("raid:vote:")) {
            e.deferReply(true).queue();
            String[] parts = id.split(":");
            try {
                Long raidId = Long.parseLong(parts[2]);
                VoteType vote = VoteType.valueOf(parts[3]);
                String discordUserId = e.getUser().getId();
                raidService.voteByDiscordUser(raidId, discordUserId, vote);
                e.getHook().sendMessage("투표 반영됨: " + vote).setEphemeral(true).queue();
                DiscordNotifier n = notifier(); if (n != null) n.updateEmbed(raidId);
            } catch (IllegalStateException ex) {
                e.getHook().sendMessage("⚠ " + ex.getMessage()).setEphemeral(true).queue();
            } catch (Exception ex) {
                log.warn("Button interaction failed: {}", ex.toString());
                e.getHook().sendMessage("에러: " + ex.getMessage()).setEphemeral(true).queue();
            }
            return;
        }
        if (id.startsWith("loot:add:")) {
            // 문주/부문주만 허용
            if (!isMasterByDiscord(e.getUser().getId())) {
                e.reply("문주/부문주만 등록 가능합니다. Discord 계정이 문파원에 연결돼 있는지 확인.").setEphemeral(true).queue();
                return;
            }
            String[] parts = id.split(":");
            Long raidId, targetId;
            try {
                raidId = Long.parseLong(parts[2]);
                targetId = Long.parseLong(parts[3]);
            } catch (Exception ex) {
                e.reply("잘못된 버튼 ID").setEphemeral(true).queue();
                return;
            }
            RaidTarget t = targetRepository.findById(targetId).orElse(null);
            if (t == null) {
                e.reply("대상을 찾을 수 없습니다").setEphemeral(true).queue();
                return;
            }
            TextInput qty = TextInput.create("qty", "수량 (개)", TextInputStyle.SHORT)
                    .setPlaceholder("예: 2")
                    .setRequired(true)
                    .setMinLength(1).setMaxLength(3)
                    .setValue("1")
                    .build();
            TextInput price = TextInput.create("price", "1개당 가격 (전, 선택)", TextInputStyle.SHORT)
                    .setPlaceholder("예: 1000000 · 미정이면 공란")
                    .setRequired(false)
                    .setMaxLength(15)
                    .build();
            Modal modal = Modal.create("loot:modal:" + raidId + ":" + targetId,
                            "➕ " + t.getName() + " 새 드랍 추가")
                    .addComponents(
                            net.dv8tion.jda.api.interactions.components.ActionRow.of(qty),
                            net.dv8tion.jda.api.interactions.components.ActionRow.of(price))
                    .build();
            e.replyModal(modal).queue();
            return;
        }
        if (id.startsWith("loot:price:")) {
            if (!isMasterByDiscord(e.getUser().getId())) {
                e.reply("문주/부문주만 편집 가능합니다.").setEphemeral(true).queue();
                return;
            }
            long lootId;
            try { lootId = Long.parseLong(id.substring("loot:price:".length())); }
            catch (Exception ex) { e.reply("잘못된 버튼 ID").setEphemeral(true).queue(); return; }
            var lootOpt = lootRepository.findById(lootId);
            if (lootOpt.isEmpty()) { e.reply("득템 없음").setEphemeral(true).queue(); return; }
            var loot = lootOpt.get();
            TextInput price = TextInput.create("price", "판매금액 (전)", TextInputStyle.SHORT)
                    .setPlaceholder("예: 5000000")
                    .setRequired(true).setMaxLength(15)
                    .setValue(loot.getSoldPrice() != null ? String.valueOf(loot.getSoldPrice()) : "")
                    .build();
            Modal modal = Modal.create("loot:pricemodal:" + lootId,
                            "💵 " + loot.getItemName() + " 판매금 입력")
                    .addComponents(net.dv8tion.jda.api.interactions.components.ActionRow.of(price))
                    .build();
            e.replyModal(modal).queue();
            return;
        }
        if (id.startsWith("loot:distribute:")) {
            if (!isMasterByDiscord(e.getUser().getId())) {
                e.reply("문주/부문주만 분배 가능합니다.").setEphemeral(true).queue();
                return;
            }
            long lootId;
            try { lootId = Long.parseLong(id.substring("loot:distribute:".length())); }
            catch (Exception ex) { e.reply("잘못된 버튼 ID").setEphemeral(true).queue(); return; }
            var lootOpt = lootRepository.findById(lootId);
            if (lootOpt.isEmpty()) { e.reply("득템 없음").setEphemeral(true).queue(); return; }
            var loot = lootOpt.get();
            if (loot.getSoldPrice() == null || loot.getSoldPrice() <= 0) {
                e.reply("판매금액이 없어 분배할 수 없습니다. 먼저 판매금 입력하세요.").setEphemeral(true).queue();
                return;
            }
            // 파티 편성된 memberId 자동 산정
            int autoDivisor = countPartyMembersUnique(loot.getRaidId());
            String defaultDiv = autoDivisor > 0 ? String.valueOf(autoDivisor) : "";
            TextInput div = TextInput.create("divisor", "분배 인원수 (÷ N명)", TextInputStyle.SHORT)
                    .setPlaceholder("파티 편성 " + autoDivisor + "명 · 미등록 포함 시 늘리세요")
                    .setRequired(true).setMinLength(1).setMaxLength(3)
                    .setValue(defaultDiv)
                    .build();
            Modal modal = Modal.create("loot:distmodal:" + lootId,
                            "⚖️ " + loot.getItemName() + " · " + String.format("%,d", loot.getSoldPrice()) + "전 분배")
                    .addComponents(net.dv8tion.jda.api.interactions.components.ActionRow.of(div))
                    .build();
            e.replyModal(modal).queue();
            return;
        }
    }

    /** 파티에 편성된 등록 문파원 unique 수 (freeName 제외 — 시스템 분배 대상만). */
    private int countPartyMembersUnique(Long raidId) {
        try {
            var parties = partyRepositoryHolder();
            if (parties == null) return 0;
            java.util.Set<Long> unique = new java.util.HashSet<>();
            for (var p : parties.findByRaidIdOrderByDisplayOrderAsc(raidId)) {
                if (p.getMikeMemberId() != null) unique.add(p.getMikeMemberId());
                for (var m : partyMemberRepositoryHolder().findByPartyIdOrderByRoleAscDisplayOrderAsc(p.getId())) {
                    if (m.getMemberId() != null) unique.add(m.getMemberId());
                }
            }
            return unique.size();
        } catch (Exception ex) { return 0; }
    }
    private com.wind.guild.repository.RaidPartyRepository partyRepositoryHolder() { return partyRepositoryLazy; }
    private com.wind.guild.repository.RaidPartyMemberRepository partyMemberRepositoryHolder() { return partyMemberRepositoryLazy; }

    @Override
    public void onModalInteraction(ModalInteractionEvent e) {
        String id = e.getModalId();
        if (id.startsWith("loot:pricemodal:")) {
            handlePriceModal(e, id);
            return;
        }
        if (id.startsWith("loot:distmodal:")) {
            handleDistributeModal(e, id);
            return;
        }
        if (!id.startsWith("loot:modal:")) return;
        e.deferReply(true).queue();
        try {
            if (!isMasterByDiscord(e.getUser().getId())) {
                e.getHook().sendMessage("문주/부문주만 등록 가능합니다").setEphemeral(true).queue();
                return;
            }
            String[] parts = id.split(":");
            Long raidId = Long.parseLong(parts[2]);
            Long targetId = Long.parseLong(parts[3]);
            String qtyStr = e.getValue("qty") != null ? e.getValue("qty").getAsString().trim() : "";
            String priceStr = e.getValue("price") != null ? e.getValue("price").getAsString().trim() : "";
            int quantity;
            try {
                quantity = Integer.parseInt(qtyStr);
                if (quantity < 1) throw new NumberFormatException();
            } catch (Exception ex) {
                e.getHook().sendMessage("수량은 1 이상 정수여야 합니다").setEphemeral(true).queue();
                return;
            }
            Long unitPrice = null;
            if (!priceStr.isEmpty()) {
                try {
                    unitPrice = Long.parseLong(priceStr.replace(",", ""));
                    if (unitPrice < 0) unitPrice = null;
                } catch (Exception ex) {
                    e.getHook().sendMessage("가격은 숫자여야 합니다 (미정이면 비워두세요)").setEphemeral(true).queue();
                    return;
                }
            }
            LootService ls = lootServiceProvider.getIfAvailable();
            if (ls == null) {
                e.getHook().sendMessage("서버 초기화 중 — 잠시 후 재시도").setEphemeral(true).queue();
                return;
            }
            var entry = new com.wind.guild.web.dto.LootDto.BulkDropEntry(targetId, quantity, unitPrice);
            ls.bulkAdd(raidId, new com.wind.guild.web.dto.LootDto.BulkAddRequest(java.util.List.of(entry)));
            DiscordNotifier n = notifier();
            if (n != null) n.syncRaidCardFresh(raidId, DiscordNotifier.RaidTrigger.LOOT);
            ChatService cs = chatService();
            if (cs != null) {
                RaidTarget t = targetRepository.findById(targetId).orElse(null);
                String tname = t != null ? ((t.getIcon() != null ? t.getIcon() + " " : "") + t.getName()) : "#" + targetId;
                String msg = "💰 [Discord] " + tname + " " + quantity + "개"
                        + (unitPrice != null ? " · " + String.format("%,d", unitPrice * quantity) + "전" : "");
                cs.saveSystem(msg);
            }
            e.getHook().sendMessage("✅ 등록 완료 · " + quantity + "개"
                    + (unitPrice != null ? " · 1개당 " + String.format("%,d", unitPrice) + "전" : "")).setEphemeral(true).queue();
        } catch (Exception ex) {
            log.warn("Modal loot submit failed: {}", ex.toString());
            try {
                e.getHook().sendMessage("에러: " + ex.getMessage()).setEphemeral(true).queue();
            } catch (Exception ignore) {}
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
            if (!isMasterByDiscord(e.getAuthor().getId())) {
                e.getMessage().reply("문주(부문주)만 레이드 등록 가능합니다").queue();
                return;
            }
            var raid = raidService.create(new RaidDto.CreateRequest(target.getCategory(), target.getId(), when, null));
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

    private void handlePriceModal(ModalInteractionEvent e, String id) {
        e.deferReply(true).queue();
        try {
            if (!isMasterByDiscord(e.getUser().getId())) {
                e.getHook().sendMessage("문주/부문주만 편집 가능합니다").setEphemeral(true).queue();
                return;
            }
            long lootId = Long.parseLong(id.substring("loot:pricemodal:".length()));
            String priceStr = e.getValue("price") != null ? e.getValue("price").getAsString().trim().replace(",", "") : "";
            long price;
            try {
                price = Long.parseLong(priceStr);
                if (price < 0) throw new NumberFormatException();
            } catch (Exception ex) {
                e.getHook().sendMessage("판매금액은 0 이상 숫자여야 합니다").setEphemeral(true).queue();
                return;
            }
            LootService ls = lootServiceProvider.getIfAvailable();
            var lootOpt = lootRepository.findById(lootId);
            if (ls == null || lootOpt.isEmpty()) {
                e.getHook().sendMessage("득템 없음 or 서버 초기화 중").setEphemeral(true).queue();
                return;
            }
            var loot = lootOpt.get();
            ls.upsert(loot.getRaidId(), lootId, new com.wind.guild.web.dto.LootDto.UpsertLootRequest(
                    loot.getTargetId(), loot.getItemName(), true, price, loot.getMemo()));
            DiscordNotifier n = notifier();
            if (n != null) n.syncRaidCardFresh(loot.getRaidId(), DiscordNotifier.RaidTrigger.LOOT);
            ChatService cs = chatService();
            if (cs != null) cs.saveSystem("💵 [Discord] " + loot.getItemName() + " 판매금 " + String.format("%,d", price) + "전");
            e.getHook().sendMessage("✅ 판매금 등록 완료 · " + String.format("%,d", price) + "전 · 이제 [⚖️ 분배] 버튼으로 진행").setEphemeral(true).queue();
        } catch (Exception ex) {
            log.warn("price modal failed: {}", ex.toString());
            try { e.getHook().sendMessage("에러: " + ex.getMessage()).setEphemeral(true).queue(); } catch (Exception ignore) {}
        }
    }

    private void handleDistributeModal(ModalInteractionEvent e, String id) {
        e.deferReply(true).queue();
        try {
            if (!isMasterByDiscord(e.getUser().getId())) {
                e.getHook().sendMessage("문주/부문주만 분배 가능합니다").setEphemeral(true).queue();
                return;
            }
            long lootId = Long.parseLong(id.substring("loot:distmodal:".length()));
            String divStr = e.getValue("divisor") != null ? e.getValue("divisor").getAsString().trim() : "";
            int divisor;
            try {
                divisor = Integer.parseInt(divStr);
                if (divisor < 1) throw new NumberFormatException();
            } catch (Exception ex) {
                e.getHook().sendMessage("분배 인원수는 1 이상 정수여야 합니다").setEphemeral(true).queue();
                return;
            }
            var lootOpt = lootRepository.findById(lootId);
            if (lootOpt.isEmpty()) {
                e.getHook().sendMessage("득템 없음").setEphemeral(true).queue();
                return;
            }
            var loot = lootOpt.get();
            // 파티 편성된 등록 문파원 memberId 수집
            java.util.LinkedHashSet<Long> memberIds = new java.util.LinkedHashSet<>();
            for (var p : partyRepositoryLazy.findByRaidIdOrderByDisplayOrderAsc(loot.getRaidId())) {
                if (p.getMikeMemberId() != null) memberIds.add(p.getMikeMemberId());
                for (var m : partyMemberRepositoryLazy.findByPartyIdOrderByRoleAscDisplayOrderAsc(p.getId())) {
                    if (m.getMemberId() != null) memberIds.add(m.getMemberId());
                }
            }
            if (memberIds.isEmpty()) {
                e.getHook().sendMessage("파티에 편성된 등록 문파원이 없습니다 (사이트에서 파티 편성 필요)").setEphemeral(true).queue();
                return;
            }
            if (divisor < memberIds.size()) {
                e.getHook().sendMessage("분배 인원수(" + divisor + ") 는 파티 등록 문파원(" + memberIds.size() + ") 이상이어야 합니다").setEphemeral(true).queue();
                return;
            }
            LootService ls = lootServiceProvider.getIfAvailable();
            if (ls == null) {
                e.getHook().sendMessage("서버 초기화 중").setEphemeral(true).queue();
                return;
            }
            ls.distribute(lootId, new java.util.ArrayList<>(memberIds), divisor);
            DiscordNotifier n = notifier();
            if (n != null) n.syncRaidCardFresh(loot.getRaidId(), DiscordNotifier.RaidTrigger.LOOT);
            long per = loot.getSoldPrice() / divisor;
            String extLabel = divisor > memberIds.size() ? " (외부 " + (divisor - memberIds.size()) + "명 포함)" : "";
            ChatService cs = chatService();
            if (cs != null) cs.saveSystem("⚖️ [Discord] " + loot.getItemName() + " " + String.format("%,d", loot.getSoldPrice()) + "전 · "
                    + divisor + "명" + extLabel + " · 1인 " + String.format("%,d", per) + "전");
            e.getHook().sendMessage("✅ 분배 완료 · " + divisor + "명" + extLabel + " · 1인 " + String.format("%,d", per) + "전").setEphemeral(true).queue();
        } catch (Exception ex) {
            log.warn("distribute modal failed: {}", ex.toString());
            try { e.getHook().sendMessage("에러: " + ex.getMessage()).setEphemeral(true).queue(); } catch (Exception ignore) {}
        }
    }

    @PreDestroy
    public void stop() {
        if (jda != null) jda.shutdown();
    }
}
