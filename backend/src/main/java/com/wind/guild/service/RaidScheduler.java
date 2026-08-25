package com.wind.guild.service;

import com.wind.guild.domain.Raid;
import com.wind.guild.domain.RaidStatus;
import com.wind.guild.repository.RaidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class RaidScheduler {

    private final RaidRepository raidRepository;
    private final DiscordNotifier notifier;

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
                r.setPre30Sent(true);
                raidRepository.save(r);
            } catch (Exception e) {
                log.warn("pre-30 notify failed for raid {}: {}", r.getId(), e.toString());
            }
        }
    }
}
