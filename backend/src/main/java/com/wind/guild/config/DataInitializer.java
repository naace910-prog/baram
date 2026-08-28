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

    private void runSchemaFix(String label, String sql) {
        try {
            jdbc.execute(sql);
            log.info("스키마 마이그레이션 [{}] 성공", label);
        } catch (Exception e) {
            log.debug("스키마 마이그레이션 [{}] 스킵 or 실패 (이미 반영): {}", label, e.toString());
        }
    }

    @Override
    @Transactional
    public void run(String... args) {
        // 스키마 마이그레이션: Hibernate ddl-auto=update 는 컬럼 NOT NULL 해제·CHECK 제약 업데이트 못 함
        runSchemaFix("raids", "ALTER TABLE raids ALTER COLUMN target_id DROP NOT NULL");
        // ChatOrigin enum 에 SYSTEM 추가 · 기존 CHECK 는 SITE/DISCORD 만 허용
        runSchemaFix("chat_messages_origin_check",
                "ALTER TABLE chat_messages DROP CONSTRAINT IF EXISTS chat_messages_origin_check");
        // 다른 잠재적 CHECK 제약 (RaidCategory · ChannelType 등) 도 확장 대비 해제
        runSchemaFix("raids_category_check",
                "ALTER TABLE raids DROP CONSTRAINT IF EXISTS raids_category_check");
        runSchemaFix("raid_targets_category_check",
                "ALTER TABLE raid_targets DROP CONSTRAINT IF EXISTS raid_targets_category_check");
        runSchemaFix("raid_parties_channel_type_check",
                "ALTER TABLE raid_parties DROP CONSTRAINT IF EXISTS raid_parties_channel_type_check");

        // v1.0.12: 신규 컬럼 (NOT NULL) 은 기존 행 때문에 Hibernate ddl-auto 로 추가 불가 → DEFAULT 로 사전 추가
        runSchemaFix("members_starred",
                "ALTER TABLE members ADD COLUMN IF NOT EXISTS starred BOOLEAN NOT NULL DEFAULT FALSE");
        runSchemaFix("chat_messages_author_starred",
                "ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS author_starred BOOLEAN NOT NULL DEFAULT FALSE");
        runSchemaFix("loot_shares_received",
                "ALTER TABLE loot_shares ADD COLUMN IF NOT EXISTS received BOOLEAN NOT NULL DEFAULT FALSE");
        runSchemaFix("loot_shares_received_at",
                "ALTER TABLE loot_shares ADD COLUMN IF NOT EXISTS received_at TIMESTAMP");
        runSchemaFix("loot_shares_paid_by",
                "ALTER TABLE loot_shares ADD COLUMN IF NOT EXISTS paid_by BIGINT");
        // v1.0.29: 카테고리별 최초 발송 flag + 분배자 + 하루 후 미분배 알림 flag
        runSchemaFix("raids_party_fresh_sent",
                "ALTER TABLE raids ADD COLUMN IF NOT EXISTS party_fresh_sent BOOLEAN NOT NULL DEFAULT FALSE");
        runSchemaFix("raids_loot_fresh_sent",
                "ALTER TABLE raids ADD COLUMN IF NOT EXISTS loot_fresh_sent BOOLEAN NOT NULL DEFAULT FALSE");
        runSchemaFix("raids_dist_fresh_sent",
                "ALTER TABLE raids ADD COLUMN IF NOT EXISTS dist_fresh_sent BOOLEAN NOT NULL DEFAULT FALSE");
        runSchemaFix("raids_stale_dist_alerted",
                "ALTER TABLE raids ADD COLUMN IF NOT EXISTS stale_dist_alerted BOOLEAN NOT NULL DEFAULT FALSE");
        runSchemaFix("raid_loots_distributed_by",
                "ALTER TABLE raid_loots ADD COLUMN IF NOT EXISTS distributed_by BIGINT");
        runSchemaFix("raid_loots_distributed_at",
                "ALTER TABLE raid_loots ADD COLUMN IF NOT EXISTS distributed_at TIMESTAMP");
        // v1.0.41: raid.scheduledAt null 허용 (시간 미정 상태 지원)
        runSchemaFix("raids_scheduled_at_nullable",
                "ALTER TABLE raids ALTER COLUMN scheduled_at DROP NOT NULL");

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
