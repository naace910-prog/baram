package com.wind.guild.repository;

import com.wind.guild.domain.RaidTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RaidTargetRepository extends JpaRepository<RaidTarget, Long> {
    Optional<RaidTarget> findByName(String name);
}
