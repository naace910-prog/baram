package com.wind.guild.config;

import com.wind.guild.domain.Member;
import com.wind.guild.domain.MemberRole;
import com.wind.guild.domain.PartyRole;
import com.wind.guild.domain.RaidTarget;
import com.wind.guild.repository.MemberRepository;
import com.wind.guild.repository.PartyRoleRepository;
import com.wind.guild.repository.RaidTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final RaidTargetRepository raidTargetRepository;
    private final PartyRoleRepository partyRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (memberRepository.count() == 0) {
            memberRepository.save(Member.builder()
                    .account("master")
                    .password(passwordEncoder.encode("1234"))
                    .nickname("문주")
                    .role(MemberRole.MASTER)
                    .active(true)
                    .build());
            log.info("초기 문주 계정 생성: master / 1234  (로그인 후 즉시 변경 권장)");
        }

        if (raidTargetRepository.count() == 0) {
            raidTargetRepository.saveAll(List.of(
                    RaidTarget.builder().name("해골왕").dropItemName("해골왕의 뼈").icon("💀").build(),
                    RaidTarget.builder().name("흑룡").dropItemName("흑룡의 어금니").icon("🐲").build(),
                    RaidTarget.builder().name("감룡").dropItemName("감룡의 어금니").icon("🦎").build(),
                    RaidTarget.builder().name("묵룡").dropItemName("묵룡의 어금니").icon("🐉").build(),
                    RaidTarget.builder().name("진룡").dropItemName("진룡의 어금니").icon("🦖").build()
            ));
            log.info("레이드 대상 5마리 시드 완료");
        }

        if (partyRoleRepository.count() == 0) {
            partyRoleRepository.saveAll(List.of(
                    PartyRole.builder().name("격수").icon("⚔️").displayOrder(1).active(true).build(),
                    PartyRole.builder().name("태성").icon("✨").displayOrder(2).active(true).build(),
                    PartyRole.builder().name("진선").icon("🗡️").displayOrder(3).active(true).build()
            ));
            log.info("파티 역할 3종 시드 완료 (격수/태성/진선)");
        }
    }
}
