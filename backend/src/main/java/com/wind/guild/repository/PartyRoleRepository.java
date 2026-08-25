package com.wind.guild.repository;

import com.wind.guild.domain.PartyRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartyRoleRepository extends JpaRepository<PartyRole, Long> {
    List<PartyRole> findAllByActiveTrueOrderByDisplayOrderAsc();
}
