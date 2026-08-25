package com.wind.guild.repository;

import com.wind.guild.domain.Raid;
import com.wind.guild.domain.RaidStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface RaidRepository extends JpaRepository<Raid, Long> {
    List<Raid> findAllByOrderByScheduledAtDesc();
    List<Raid> findByStatusOrderByScheduledAtAsc(RaidStatus status);
    List<Raid> findByStatusAndPre30SentFalseAndScheduledAtBetween(RaidStatus status, LocalDateTime from, LocalDateTime to);
    List<Raid> findByStatusAndScheduledAtBetween(RaidStatus status, LocalDateTime from, LocalDateTime to);
}
