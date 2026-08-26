package com.wind.guild.repository;

import com.wind.guild.domain.RaidPartyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RaidPartyMemberRepository extends JpaRepository<RaidPartyMember, Long> {
    List<RaidPartyMember> findByPartyIdOrderByRoleAscDisplayOrderAsc(Long partyId);
    List<RaidPartyMember> findByPartyIdInOrderByPartyIdAscRoleAscDisplayOrderAsc(Collection<Long> partyIds);
    void deleteByPartyId(Long partyId);
}
