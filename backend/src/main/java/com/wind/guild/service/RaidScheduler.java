package com.wind.guild.service;

import com.wind.guild.domain.Raid;
import com.wind.guild.domain.RaidCategory;
import com.wind.guild.domain.RaidStatus;
import com.wind.guild.repository.RaidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class RaidScheduler {

    private final RaidRepository raidRepository;
    private final DiscordNotifier notifier;
    private final WebPushService push;
    private final ChatService chat;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM/dd HH:mm");

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void checkPre30() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.plusMinutes(29);
        LocalDateTime to = now.plusMinutes(31);
        List<Raid> ready = raidRepository.findByStatusAndPre30SentFalseAndScheduledAtBetween(
                RaidStatus.PLANNED, from, to);
        for (Raid r : ready) {
            try {
                notifier.notifyRaidPre30(r.getId());
                String label = raidLabel(r);
                push.sendToAll("⏰ 30분 뒤 시작: " + label,
                        r.getScheduledAt().format(FMT) + " · 곧 시작합니다",
                        "/raids/" + r.getId());
                chat.saveSystem("⏰ 30분 뒤 시작 · " + label + " · " + r.getScheduledAt().format(FMT),
                        "RAID_VOTE", r.getId());
                r.setPre30Sent(true);
                raidRepository.save(r);
            } catch (Exception e) {
                log.warn("pre-30 notify failed for raid {}: {}", r.getId(), e.toString());
            }
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    @Transactional
    public void autoComplete() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime after = now.minusDays(3);       // 3일 전 이후만 (backfill 스팸 방지)
        LocalDateTime before = now.minusMinutes(30);  // 30분 지난 것

        List<Raid> stale = raidRepository.findByStatusAndScheduledAtBetween(
                RaidStatus.PLANNED, after, before);
        for (Raid r : stale) {
            try {
                r.setStatus(RaidStatus.DONE);
                raidRepository.save(r);
                notifier.syncRaidCard(r.getId(), DiscordNotifier.RaidTrigger.STATUS);
                String label = raidLabel(r);
                chat.saveSystem("✅ 레이드 자동 완료 처리 · " + label + " · " + r.getScheduledAt().format(FMT));
                log.info("자동 완료 처리: raid={} {}", r.getId(), label);
            } catch (Exception e) {
                log.warn("자동 완료 실패 raid {}: {}", r.getId(), e.toString());
            }
        }
    }

    private String raidLabel(Raid r) {
        if (r.getTarget() != null) return r.getTarget().getName();
        if (r.getCategory() == RaidCategory.FANG) return "어금니 레이드";
        if (r.getCategory() == RaidCategory.SKULL_KING) return "해골왕";
        return "레이드";
    }
}
