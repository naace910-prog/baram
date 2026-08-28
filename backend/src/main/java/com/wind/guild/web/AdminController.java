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

    /** Discord 봇 상태 진단 + 알림 채널에 테스트 텍스트 발송. */
    @PostMapping("/discord-test")
    public Map<String, Object> discordTest(HttpSession session) {
        String role = (String) session.getAttribute(SessionKeys.MEMBER_ROLE);
        if (!"MASTER".equals(role) && !"VICE".equals(role)) {
            throw new IllegalStateException("문주/부문주만 실행 가능합니다");
        }
        boolean enabled = discordProps.isEnabled();
        boolean ready = discordBot.isReady();
        boolean notifyChSet = discordProps.getNotifyChannelId() != null && !discordProps.getNotifyChannelId().isBlank();
        boolean chanReachable = discordBot.notifyChannel() != null;
        if (ready && chanReachable) {
            discordNotifier.postAlertMessage("🧪 Discord 진단 테스트 · " + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm:ss"))
                    + " — 이 메시지가 보이면 봇 정상 작동");
        }
        return Map.of(
                "discordEnabled", enabled,
                "botReady", ready,
                "notifyChannelIdSet", notifyChSet,
                "notifyChannelReachable", chanReachable,
                "testMessageAttempted", ready && chanReachable
        );
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
