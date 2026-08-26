package com.wind.guild.repository;

import com.wind.guild.domain.Raid;
import com.wind.guild.domain.RaidCategory;
import com.wind.guild.domain.RaidStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RaidRepository extends JpaRepository<Raid, Long> {
    Optional<Raid> findFirstByTarget_IdAndScheduledAt(Long targetId, LocalDateTime scheduledAt);
    Optional<Raid> findFirstByCategoryAndScheduledAtAndTargetIsNull(RaidCategory category, LocalDateTime scheduledAt);
    List<Raid> findAllByOrderByScheduledAtDesc();
    List<Raid> findByStatusOrderByScheduledAtAsc(RaidStatus status);
    List<Raid> findByStatusAndPre30SentFalseAndScheduledAtBetween(RaidStatus status, LocalDateTime from, LocalDateTime to);
    List<Raid> findByStatusAndScheduledAtBetween(RaidStatus status, LocalDateTime from, LocalDateTime to);
    List<Raid> findTop3ByTarget_IdAndScheduledAtLessThanOrderByScheduledAtDesc(Long targetId, LocalDateTime before);
    List<Raid> findTop3ByCategoryAndScheduledAtLessThanOrderByScheduledAtDesc(RaidCategory category, LocalDateTime before);
}
