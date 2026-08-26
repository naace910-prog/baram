package com.wind.guild.repository;

import com.wind.guild.domain.LootShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LootShareRepository extends JpaRepository<LootShare, Long> {
    List<LootShare> findByLootId(Long lootId);
    List<LootShare> findByLootIdIn(Collection<Long> lootIds);
    List<LootShare> findByMemberIdAndPaidFalse(Long memberId);
    void deleteByLootId(Long lootId);
}
