package com.wind.guild.repository;

import com.wind.guild.domain.RaidVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RaidVoteRepository extends JpaRepository<RaidVote, Long> {
    List<RaidVote> findByRaidId(Long raidId);
    Optional<RaidVote> findByRaidIdAndMemberId(Long raidId, Long memberId);
    void deleteByRaidId(Long raidId);
}
