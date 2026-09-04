package com.wind.guild.web;

import com.wind.guild.config.DiscordProperties;
import com.wind.guild.config.SessionKeys;
import com.wind.guild.repository.LootShareRepository;
import com.wind.guild.repository.RaidLootRepository;
import com.wind.guild.service.DiscordBotService;
import com.wind.guild.service.DiscordNotifier;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final RaidLootRepository lootRepo;
    private final LootShareRepository shareRepo;
    private final JdbcTemplate jdbc;
    private final DiscordBotService discordBot;
    private final DiscordNotifier discordNotifier;
    private final DiscordProperties discordProps;
    private final com.wind.guild.repository.DiscordApiLogRepository discordLogRepo;

    /** Discord 봇 상태 진단 + 알림 채널에 테스트 텍스트 발송. */
    @PostMapping("/discord-test")
    public Map<String, Object> discordTest(HttpSession session) {
        String role = (String) session.getAttribute(SessionKeys.MEMBER_ROLE);
        if (!"MASTER".equals(role) && !"VICE".equals(role)) {
            throw new IllegalStateException("문주/부문주만 실행 가능합니다");
        }
        boolean enabled = discordProps.isEnabled();
        boolean ready = discordBot.isReady();
        String jdaStatus = discordBot.status();
        long gatewayPing = discordBot.gatewayPing();
        boolean notifyChSet = discordProps.getNotifyChannelId() != null && !discordProps.getNotifyChannelId().isBlank();
        boolean chanReachable = discordBot.notifyChannel() != null;
        long cooldownSec = discordNotifier.getCooldownRemainingSec();
        if (ready && chanReachable && cooldownSec == 0) {
            discordNotifier.postAlertMessage("🧪 Discord 진단 테스트 · " + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm:ss"))
                    + " — 이 메시지가 보이면 봇 정상 작동");
        }
        java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("discordEnabled", enabled);
        out.put("jdaStatus", jdaStatus);
        out.put("botReady", ready);
        out.put("gatewayPingMs", gatewayPing);
        out.put("notifyChannelIdSet", notifyChSet);
        out.put("notifyChannelReachable", chanReachable);
        out.put("cooldownRemainingSec", cooldownSec);
        out.put("testMessageAttempted", ready && chanReachable && cooldownSec == 0);
        return out;
    }

    /** 429 cooldown 즉시 해제 (Discord 자체 ban 이 실제로 풀렸다고 확인된 경우만). */
    @PostMapping("/discord-clear-cooldown")
    public Map<String, Object> clearCooldown(HttpSession session) {
        String role = (String) session.getAttribute(SessionKeys.MEMBER_ROLE);
        if (!"MASTER".equals(role)) throw new IllegalStateException("문주만 실행 가능합니다");
        long was = discordNotifier.getCooldownRemainingSec();
        discordNotifier.clearCooldown();
        return Map.of("clearedFromSec", was, "cooldownRemainingSec", discordNotifier.getCooldownRemainingSec());
    }

    /** 최근 Discord API 호출 로그 100건 + 최근 1h 성공/실패 통계. */
    @GetMapping("/discord-logs")
    public Map<String, Object> discordLogs(HttpSession session) {
        String role = (String) session.getAttribute(SessionKeys.MEMBER_ROLE);
        if (!"MASTER".equals(role) && !"VICE".equals(role)) throw new IllegalStateException("문주/부문주만 실행 가능합니다");
        java.time.LocalDateTime hourAgo = java.time.LocalDateTime.now().minusHours(1);
        long total1h = discordLogRepo.countByCreatedAtAfter(hourAgo);
        long fail1h = discordLogRepo.countBySuccessFalseAndCreatedAtAfter(hourAgo);
        var recent = discordLogRepo.findTop100ByOrderByIdDesc().stream().map(l -> {
            java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("createdAt", l.getCreatedAt().toString());
            m.put("op", l.getOp());
            m.put("kind", l.getKind());
            m.put("refId", l.getRefId());
            m.put("trigger", l.getTrigger());
            m.put("success", l.isSuccess());
            m.put("discordMsgId", l.getDiscordMessageId());
            m.put("latencyMs", l.getLatencyMs());
            m.put("error", l.getError());
            return m;
        }).toList();
        return Map.of("total1h", total1h, "fail1h", fail1h, "recent100", recent);
    }

    /** Discord API 로그 정리 (기본 7일 이전). */
    @PostMapping("/discord-logs-purge")
    @Transactional
    public Map<String, Object> purgeDiscordLogs(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "7") int days, HttpSession session) {
        String role = (String) session.getAttribute(SessionKeys.MEMBER_ROLE);
        if (!"MASTER".equals(role)) throw new IllegalStateException("문주만 실행 가능합니다");
        int deleted = discordLogRepo.deleteOlderThan(java.time.LocalDateTime.now().minusDays(days));
        return Map.of("deleted", deleted, "olderThanDays", days);
    }

    @PostMapping("/reset-loots")
    @Transactional
    public Map<String, Object> resetLoots(HttpSession session) {
        String role = (String) session.getAttribute(SessionKeys.MEMBER_ROLE);
        if (!"MASTER".equals(role)) {
            throw new IllegalStateException("문주만 실행 가능합니다");
        }
        long sharesBefore = shareRepo.count();
        long lootsBefore = lootRepo.count();
        // orphan 포함 전체 삭제
        jdbc.execute("DELETE FROM loot_shares");
        jdbc.execute("DELETE FROM raid_loots");
        return Map.of(
                "result", "ok",
                "deletedShares", sharesBefore,
                "deletedLoots", lootsBefore
        );
    }
}
