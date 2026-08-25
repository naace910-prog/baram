package com.wind.guild.config;

import com.wind.guild.domain.Member;
import com.wind.guild.domain.MemberRole;
import com.wind.guild.domain.PartyRole;
import com.wind.guild.domain.Raid;
import com.wind.guild.domain.RaidCategory;
import com.wind.guild.domain.RaidTarget;
import com.wind.guild.repository.MemberRepository;
import com.wind.guild.repository.PartyRoleRepository;
import com.wind.guild.repository.RaidRepository;
import com.wind.guild.repository.RaidTargetRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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
    private final RaidRepository raidRepository;
    private final PartyRoleRepository partyRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(String... args) {
        // 스키마 마이그레이션: Hibernate ddl-auto=update 는 컬럼 NOT NULL 해제 못 함
        // 어금니 카테고리 raid 는 target_id 가 null 이어야 함
        try {
            jdbc.execute("ALTER TABLE raids ALTER COLUMN target_id DROP NOT NULL");
            log.info("스키마 마이그레이션: raids.target_id NOT NULL 해제");
        } catch (Exception e) {
            log.debug("raids.target_id NOT NULL 해제 스킵 (이미 nullable): {}", e.toString());
        }

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
                    RaidTarget.builder().name("해골왕").dropItemName("해골왕의 뼈").icon("💀").category(RaidCategory.SKULL_KING).build(),
                    RaidTarget.builder().name("흑룡").dropItemName("흑룡의 어금니").icon("🐲").category(RaidCategory.FANG).build(),
                    RaidTarget.builder().name("감룡").dropItemName("감룡의 어금니").icon("🦎").category(RaidCategory.FANG).build(),
                    RaidTarget.builder().name("묵룡").dropItemName("묵룡의 어금니").icon("🐉").category(RaidCategory.FANG).build(),
                    RaidTarget.builder().name("진룡").dropItemName("진룡의 어금니").icon("🦖").category(RaidCategory.FANG).build()
            ));
            log.info("레이드 대상 5마리 시드 완료");
        }

        // 마이그레이션: 기존 target 에 카테고리·아이콘 채우기 (이름 기반)
        java.util.Map<String, String> defaultIcons = java.util.Map.of(
                "해골왕", "💀", "흑룡", "🐲", "감룡", "🦎", "묵룡", "🐉", "진룡", "🦖");
        int migrated = 0;
        for (RaidTarget t : raidTargetRepository.findAll()) {
            boolean changed = false;
            if (t.getCategory() == null) {
                t.setCategory("해골왕".equals(t.getName()) ? RaidCategory.SKULL_KING : RaidCategory.FANG);
                changed = true;
            }
            if ((t.getIcon() == null || t.getIcon().isBlank()) && defaultIcons.containsKey(t.getName())) {
                t.setIcon(defaultIcons.get(t.getName()));
                changed = true;
            }
            if (changed) migrated++;
        }
        if (migrated > 0) log.info("기존 레이드 대상 {}개에 카테고리·아이콘 자동 채워넣기 완료", migrated);

        // 마이그레이션: 기존 raid 에 카테고리 채우기 (target 의 카테고리 참조)
        int raidMigrated = 0;
        for (Raid r : raidRepository.findAll()) {
            if (r.getCategory() == null && r.getTarget() != null) {
                r.setCategory(r.getTarget().getCategory());
                raidMigrated++;
            }
        }
        if (raidMigrated > 0) log.info("기존 레이드 {}건에 카테고리 자동 채워넣기 완료", raidMigrated);

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
