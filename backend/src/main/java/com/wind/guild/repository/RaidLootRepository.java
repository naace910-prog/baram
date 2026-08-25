package com.wind.guild.repository;

import com.wind.guild.domain.RaidLoot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RaidLootRepository extends JpaRepository<RaidLoot, Long> {
    List<RaidLoot> findByRaidId(Long raidId);
    void deleteByRaidId(Long raidId);
}
