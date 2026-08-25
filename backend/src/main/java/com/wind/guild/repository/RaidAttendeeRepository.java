package com.wind.guild.repository;

import com.wind.guild.domain.RaidAttendee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RaidAttendeeRepository extends JpaRepository<RaidAttendee, Long> {
    List<RaidAttendee> findByRaidId(Long raidId);
    void deleteByRaidId(Long raidId);
}
