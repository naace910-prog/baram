package com.wind.guild.repository;

import com.wind.guild.domain.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpoint(String endpoint);
    List<PushSubscription> findByMemberId(Long memberId);
    void deleteByEndpoint(String endpoint);
    void deleteByMemberIdAndEndpoint(Long memberId, String endpoint);
}
