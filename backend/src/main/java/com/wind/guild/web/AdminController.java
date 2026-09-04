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
        // 연결 실패 진단
        out.put("connectAttempts", discordBot.connectAttempts());
        out.put("connectLoopRunning", discordBot.connectLoopRunning());
        out.put("lastConnectAttemptAt", discordBot.lastConnectAttemptAt());
        out.put("lastConnectError", discordBot.lastConnectError());
        return out;
    }

    /**
     * IP 레벨 차단 판별.
     * https://discord.com/api/v10/gateway 는 인증이 전혀 필요 없는 엔드포인트다.
     * 이게 429 면 봇토큰/OAuth 자격증명과 무관하게 **이 서버의 아웃바운드 IP** 가
     * Discord(Cloudflare)에 의해 차단된 것 → 우리 코드로는 해결 불가.
     * 200 이면 IP 는 멀쩡하므로 429 원인은 토큰/앱 단위로 좁혀진다.
     */
    @PostMapping("/discord-ip-check")
    public Map<String, Object> discordIpCheck(HttpSession session) {
        String role = (String) session.getAttribute(SessionKeys.MEMBER_ROLE);
        if (!"MASTER".equals(role) && !"VICE".equals(role)) {
            throw new IllegalStateException("문주/부문주만 실행 가능합니다");
        }
        java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
        long t0 = System.currentTimeMillis();
        try {
            var rt = new org.springframework.web.client.RestTemplate();
            var resp = rt.getForEntity("https://discord.com/api/v10/gateway", String.class);
            out.put("httpStatus", resp.getStatusCode().value());
            String b = resp.getBody();
            out.put("body", b == null ? null : (b.length() > 300 ? b.substring(0, 300) : b));
            out.put("verdict", "IP 정상 — 인증 없는 엔드포인트 응답 OK");
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            int sc = e.getStatusCode().value();
            String body = e.getResponseBodyAsString();
            out.put("httpStatus", sc);
            out.put("body", body.length() > 300 ? body.substring(0, 300) : body);
            out.put("verdict", sc == 429
                    ? "⛔ IP 레벨 차단 확정 — 봇토큰/OAuth 와 무관. Render 아웃바운드 IP 가 Discord 에 차단됨"
                    : "인증 없는 요청이 " + sc + " 로 실패");
        } catch (Exception e) {
            out.put("httpStatus", -1);
            out.put("body", e.toString());
            out.put("verdict", "요청 자체 실패 (네트워크/DNS)");
        }
        out.put("latencyMs", System.currentTimeMillis() - t0);
        return out;
    }

    /** 봇 수동 재연결 (Render 재배포 없이). */
    @PostMapping("/discord-reconnect")
    public Map<String, Object> discordReconnect(HttpSession session) {
        String role = (String) session.getAttribute(SessionKeys.MEMBER_ROLE);
        if (!"MASTER".equals(role) && !"VICE".equals(role)) {
            throw new IllegalStateException("문주/부문주만 실행 가능합니다");
        }
        String result = discordBot.reconnectNow();
        return Map.of("result", result, "botReady", discordBot.isReady(), "jdaStatus", discordBot.status());
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
