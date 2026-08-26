package com.wind.guild.repository;

import com.wind.guild.domain.RaidLoot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface RaidLootRepository extends JpaRepository<RaidLoot, Long> {
    List<RaidLoot> findByRaidId(Long raidId);
    void deleteByRaidId(Long raidId);

    @Modifying
    @Transactional
    @Query("update RaidLoot l set l.discordMessageId = :msgId where l.id = :lootId")
    int updateDiscordMessageId(@Param("lootId") Long lootId, @Param("msgId") Long msgId);
}
