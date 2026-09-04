package com.wind.guild.repository;

import com.wind.guild.domain.DiscordApiLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface DiscordApiLogRepository extends JpaRepository<DiscordApiLog, Long> {

    List<DiscordApiLog> findTop100ByOrderByIdDesc();

    List<DiscordApiLog> findTop50BySuccessOrderByIdDesc(boolean success);

    long countByCreatedAtAfter(LocalDateTime after);
    long countBySuccessFalseAndCreatedAtAfter(LocalDateTime after);

    @Modifying
    @Query("delete from DiscordApiLog l where l.createdAt < :before")
    int deleteOlderThan(java.time.LocalDateTime before);
}
