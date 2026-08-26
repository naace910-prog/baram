package com.wind.guild.repository;

import com.wind.guild.domain.Raid;
import com.wind.guild.domain.RaidCategory;
import com.wind.guild.domain.RaidStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RaidRepository extends JpaRepository<Raid, Long> {
    /** discordMessageId 만 부분 update (다른 필드 race condition 방지). */
    @Modifying
    @Transactional
    @Query("update Raid r set r.discordMessageId = :msgId where r.id = :raidId")
    int updateDiscordMessageId(@Param("raidId") Long raidId, @Param("msgId") Long msgId);

    Optional<Raid> findFirstByTarget_IdAndScheduledAt(Long targetId, LocalDateTime scheduledAt);
    Optional<Raid> findFirstByCategoryAndScheduledAtAndTargetIsNull(RaidCategory category, LocalDateTime scheduledAt);
    List<Raid> findAllByOrderByScheduledAtDesc();
    List<Raid> findByStatusOrderByScheduledAtAsc(RaidStatus status);
    List<Raid> findByStatusAndPre30SentFalseAndScheduledAtBetween(RaidStatus status, LocalDateTime from, LocalDateTime to);
    List<Raid> findByStatusAndScheduledAtBetween(RaidStatus status, LocalDateTime from, LocalDateTime to);
    List<Raid> findTop3ByTarget_IdAndScheduledAtLessThanOrderByScheduledAtDesc(Long targetId, LocalDateTime before);
    List<Raid> findTop3ByCategoryAndScheduledAtLessThanOrderByScheduledAtDesc(RaidCategory category, LocalDateTime before);
}
