package com.wind.guild.service;

import com.wind.guild.domain.Raid;
import com.wind.guild.domain.RaidCategory;
import com.wind.guild.domain.RaidLoot;
import com.wind.guild.domain.RaidStatus;
import com.wind.guild.repository.RaidLootRepository;
import com.wind.guild.repository.LootShareRepository;
import com.wind.guild.repository.RaidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class RaidScheduler {

    private final RaidRepository raidRepository;
    private final RaidLootRepository lootRepository;
    private final LootShareRepository shareRepository;
    private final DiscordNotifier notifier;
    private final WebPushService push;
    private final ChatService chat;
    private final org.springframework.beans.factory.ObjectProvider<RaidService> raidServiceProvider;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM/dd(E) HH:mm", java.util.Locale.KOREAN);

    /**
     * 트랜잭션 커밋 후 실행 (트랜잭션이 없으면 즉시 실행).
     *
     * notifier 메서드들은 @Async 라 별도 스레드/트랜잭션에서 DB 를 다시 읽는다.
     * 커밋 전에 호출하면 변경 전 상태를 읽어 잘못된 카드를 만들거나 조건 분기가 어긋난다.
     * (실제 사고: 자동완료 시 status 가 PLANNED 로 읽혀 득템 버튼 카드가 발송되지 않음)
     */
    private void runAfterCommit(Runnable action) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() {
                            try { action.run(); }
                            catch (Exception e) { log.warn("afterCommit 작업 실패: {}", e.toString(), e); }
                        }
                    });
        } else {
            action.run();
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void checkPre30() {
        LocalDateTime now = LocalDateTime.now();
        // 창을 넓힘 (지금 ~ 30분 이내). pre30Sent=false 로 1회만 발송.
        // 이유: 이전엔 (now+29 ~ now+31) 2분 창이라 Render sleep · 30분내 늦게 등록된 레이드는 놓쳤음.
        LocalDateTime from = now;
        LocalDateTime to = now.plusMinutes(30);
        List<Raid> ready = raidRepository.findByStatusAndPre30SentFalseAndScheduledAtBetween(
                RaidStatus.PLANNED, from, to);
        for (Raid r : ready) {
            try {
                dispatchPre30(r, /*manual*/ false);
            } catch (Exception e) {
                log.warn("pre-30 notify failed for raid {}: {}", r.getId(), e.toString());
            }
        }
    }

    /**
     * 30분 리마인더 발송 로직. 스케줄러와 수동 트리거가 공유.
     * Discord (@here) + push + chat + pre30Sent=true.
     */
    @Transactional
    public void dispatchPre30(Raid r, boolean manual) {
        LocalDateTime now = LocalDateTime.now();
        long minsLeft = Math.max(0, java.time.Duration.between(now, r.getScheduledAt()).toMinutes());
        // lazy 연관 접근은 트랜잭션 안에서 미리 해소
        final Long raidId = r.getId();
        final String label = raidLabel(r);
        final String whenText = r.getScheduledAt().format(FMT);
        final String prefix = manual ? "🔔 문주 발송 · " : "⏰ ";
        // ⚠ 커밋 이후에 발송.
        //   커밋 전에 부르면 postRaidPre30Fresh 가 갱신한 discordMessageId 를
        //   이 트랜잭션의 오래된 엔티티가 커밋하면서 덮어쓴다 (리마인더 카드 유실).
        runAfterCommit(() -> {
            notifier.postRaidPre30Fresh(raidId);
            push.sendToAll(prefix + "곧 시작: " + label + " (" + minsLeft + "분 뒤)",
                    whenText, "/raids/" + raidId);
            chat.saveSystem(prefix + minsLeft + "분 뒤 시작 · " + label + " · " + whenText,
                    "RAID_VOTE", raidId);
        });
        r.setPre30Sent(true);
        raidRepository.save(r);
    }

    // Discord rate-limit 폭발 방지: 한 tick 에서 최대 이만큼만 처리, 나머지는 다음 tick.
    private static final int AUTO_COMPLETE_MAX_PER_TICK = 3;
    // 이보다 오래된 raid 는 backfill 로 간주 → 조용히 DONE + next 생성도 스킵 (Discord 스팸 방지).
    private static final long BACKFILL_THRESHOLD_HOURS = 6;

    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    @Transactional
    public void autoComplete() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime after = now.minusDays(3);       // 3일 전 이후만 (backfill 스팸 방지)
        LocalDateTime before = now.minusMinutes(30);  // 30분 지난 것
        LocalDateTime backfillCut = now.minusHours(BACKFILL_THRESHOLD_HOURS);

        List<Raid> stale = raidRepository.findByStatusAndScheduledAtBetween(
                RaidStatus.PLANNED, after, before);
        int processed = 0;
        for (Raid r : stale) {
            if (processed >= AUTO_COMPLETE_MAX_PER_TICK) {
                log.info("autoComplete tick cap reached ({} 처리, {} 남음 · 다음 tick 에서 계속)",
                        AUTO_COMPLETE_MAX_PER_TICK, stale.size() - processed);
                break;
            }
            boolean isBackfill = r.getScheduledAt().isBefore(backfillCut);
            try {
                r.setStatus(RaidStatus.DONE);
                raidRepository.save(r);
                // ⚠ lazy 연관(target) 접근은 트랜잭션 안에서 미리 끝내둔다.
                //   afterCommit 콜백 시점엔 세션이 닫혀 LazyInitializationException 이 난다.
                final Long doneRaidId = r.getId();
                final String label = raidLabel(r);
                final String whenText = r.getScheduledAt().format(FMT);
                if (isBackfill) {
                    // 오래된 raid: Discord API 아끼려고 조용히 DONE 만 (카드·챗 알림·next 생성 모두 스킵)
                    log.info("자동 완료 (backfill · 알림 생략): raid={} {} scheduled={}", doneRaidId, label, r.getScheduledAt());
                } else {
                    Long nextRaidId = null;
                    try {
                        RaidService rs = raidServiceProvider.getIfAvailable();
                        if (rs != null) {
                            Raid next = rs.createNextAfterDone(r);
                            if (next != null) {
                                nextRaidId = next.getId();
                                log.info("자동 다음 레이드 생성: raid={} label={}", nextRaidId, label);
                            }
                        }
                    } catch (Exception ex) { log.warn("자동 다음 레이드 생성 실패 raid {}: {}", doneRaidId, ex.toString()); }

                    // ⚠ Discord 발송은 반드시 커밋 이후에.
                    //   notifier 는 @Async 라 별도 스레드/트랜잭션에서 DB 를 다시 읽는다.
                    //   커밋 전에 던지면 status 가 아직 PLANNED 로 읽혀
                    //   (1) DONE 신규발송 조건이 거짓이 되고
                    //   (2) buildRaidButtons 가 득템 버튼 대신 투표 버튼을 그린다.
                    final Long nextIdFinal = nextRaidId;
                    runAfterCommit(() -> {
                        notifier.syncRaidCardCategoryAware(doneRaidId, DiscordNotifier.RaidTrigger.STATUS);
                        chat.saveSystem("✅ 레이드 자동 완료 처리 · " + label + " · " + whenText);
                        log.info("자동 완료 처리: raid={} {}", doneRaidId, label);
                        if (nextIdFinal != null) {
                            notifier.syncRaidCard(nextIdFinal, DiscordNotifier.RaidTrigger.CREATED);
                            chat.saveSystem("🆕 다음 레이드 자동 등록 · " + label + " · 시간 미정 (Discord 카드에서 설정)");
                        }
                    });
                }
                processed++;
            } catch (Exception e) {
                log.warn("자동 완료 실패 raid {}: {}", r.getId(), e.toString());
            }
        }
    }

    /** DONE 이후 24시간 경과 · 판매금 없거나 미분배 loot 있는 raid → 한 번만 discord 알림. */
    @Scheduled(fixedDelay = 3600_000, initialDelay = 90_000)  // 1시간마다
    @Transactional
    public void checkStaleDistribution() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime before = now.minusHours(24);
        LocalDateTime after = now.minusDays(7);  // 7일 전~어제 사이 완료된 것만 (더 오래된 backfill 스팸 방지)
        List<Raid> candidates = raidRepository.findByStatusAndScheduledAtBetween(
                RaidStatus.DONE, after, before);
        for (Raid r : candidates) {
            if (r.isStaleDistAlerted()) continue;
            try {
                List<RaidLoot> loots = lootRepository.findByRaidId(r.getId());
                if (loots.isEmpty()) continue;
                List<String> pending = new java.util.ArrayList<>();
                for (RaidLoot l : loots) {
                    if (!l.isDropped()) continue;
                    if (l.getSoldPrice() == null || l.getSoldPrice() <= 0) {
                        pending.add(l.getItemName() + " (판매금 미입력)");
                    } else if (shareRepository.findByLootId(l.getId()).isEmpty()) {
                        pending.add(l.getItemName() + " (미분배)");
                    }
                }
                if (pending.isEmpty()) continue;
                String label = raidLabel(r);
                String msg = "⚠️ **" + label + "** · " + r.getScheduledAt().format(FMT)
                        + " 완료 후 24시간 경과, 처리 필요 항목:\n"
                        + "  · " + String.join("\n  · ", pending)
                        + "\n사이트 또는 카드 버튼으로 진행하세요.";
                notifier.postAlertMessage(msg);
                chat.saveSystem(msg);
                r.setStaleDistAlerted(true);
                raidRepository.save(r);
                log.info("stale-dist alert: raid={} pending={}", r.getId(), pending);
            } catch (Exception ex) {
                log.warn("stale-dist alert failed raid {}: {}", r.getId(), ex.toString());
            }
        }
    }

    private String raidLabel(Raid r) {
        if (r.getTarget() != null) return r.getTarget().getName();
        if (r.getCategory() == RaidCategory.FANG) return "어금니 레이드";
        if (r.getCategory() == RaidCategory.SKULL_KING) return "해골왕";
        return "레이드";
    }
}
