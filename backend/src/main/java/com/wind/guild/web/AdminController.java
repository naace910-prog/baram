package com.wind.guild.web;

import com.wind.guild.config.SessionKeys;
import com.wind.guild.repository.LootShareRepository;
import com.wind.guild.repository.RaidLootRepository;
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
