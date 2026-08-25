package com.wind.guild.repository;

import com.wind.guild.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByAccount(String account);
    Optional<Member> findByDiscordUserId(String discordUserId);
    List<Member> findAllByActiveTrueOrderByNicknameAsc();
    boolean existsByAccount(String account);
}
