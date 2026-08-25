package com.wind.guild.repository;

import com.wind.guild.domain.RaidParty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RaidPartyRepository extends JpaRepository<RaidParty, Long> {
    List<RaidParty> findByRaidIdOrderByDisplayOrderAsc(Long raidId);
    void deleteByRaidId(Long raidId);
}
