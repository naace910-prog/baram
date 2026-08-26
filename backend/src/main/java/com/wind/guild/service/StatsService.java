package com.wind.guild.service;

import com.wind.guild.domain.*;
import com.wind.guild.repository.*;
import com.wind.guild.web.dto.StatsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final MemberRepository memberRepository;
    private final RaidRepository raidRepository;
    private final RaidTargetRepository targetRepository;
    private final RaidLootRepository lootRepository;
    private final LootShareRepository shareRepository;

    public StatsDto.Result compute() {
        List<Member> members = memberRepository.findAllByActiveTrueOrderByNicknameAsc();
        List<Raid> raids = raidRepository.findAll();
        List<RaidTarget> targets = targetRepository.findAll();
        List<RaidLoot> loots = lootRepository.findAll();
        List<LootShare> shares = shareRepository.findAll();

        long plannedRaids = raids.stream().filter(r -> r.getStatus() == RaidStatus.PLANNED).count();
        long doneRaids = raids.stream().filter(r -> r.getStatus() == RaidStatus.DONE).count();
        long totalRevenue = loots.stream()
                .filter(l -> l.getSoldPrice() != null)
                .mapToLong(RaidLoot::getSoldPrice).sum();
        long paidTotal = shares.stream()
                .filter(LootShare::isPaid)
                .mapToLong(LootShare::getShare).sum();
        // 미정산 = 총 판매금 - 지급완료 (미등록 인원 몫도 자동 포함)
        long unpaidTotal = Math.max(0, totalRevenue - paidTotal);

        StatsDto.Overview overview = new StatsDto.Overview(
                members.size(), plannedRaids, doneRaids, totalRevenue, unpaidTotal);

        Map<Long, String> memberNick = new HashMap<>();
        for (Member m : members) memberNick.put(m.getId(), m.getNickname());

        Map<Long, long[]> perMember = new HashMap<>();
        for (LootShare s : shares) {
            long[] agg = perMember.computeIfAbsent(s.getMemberId(), k -> new long[]{0, 0, 0, 0});
            agg[0] += 1;
            agg[1] += s.getShare();
            if (!s.isPaid()) {
                agg[2] += s.getShare();
                agg[3] += 1;
            }
        }
        List<StatsDto.MemberStat> memberStats = new ArrayList<>();
        for (Member m : members) {
            long[] agg = perMember.getOrDefault(m.getId(), new long[]{0, 0, 0, 0});
            memberStats.add(new StatsDto.MemberStat(
                    m.getId(), m.getNickname(),
                    agg[0], agg[1], agg[2], agg[3]));
        }
        memberStats.sort(Comparator.comparingLong(StatsDto.MemberStat::totalShare).reversed());

        // target 별 통계: **loot.targetId 기반** (어금니 raid 안의 흑룡/묵룡/감룡/진룡 개별 집계 포함)
        Map<Long, List<RaidLoot>> lootsByTarget = new HashMap<>();
        for (RaidLoot l : loots) {
            if (l.getTargetId() == null) continue;
            lootsByTarget.computeIfAbsent(l.getTargetId(), k -> new ArrayList<>()).add(l);
        }
        List<StatsDto.TargetStat> targetStats = new ArrayList<>();
        for (RaidTarget t : targets) {
            List<RaidLoot> tLoots = lootsByTarget.getOrDefault(t.getId(), List.of());
            // 킬 카운트 = 이 target 의 loot 개수 (해골왕/어금니 모두 loot 1개 = 1마리 잡음)
            long dropCount = tLoots.size();
            long total = 0, priceCount = 0;
            for (RaidLoot l : tLoots) {
                if (l.getSoldPrice() != null) {
                    total += l.getSoldPrice();
                    priceCount++;
                }
            }
            long avg = priceCount > 0 ? total / priceCount : 0;
            targetStats.add(new StatsDto.TargetStat(
                    t.getId(), t.getName(), t.getIcon(), t.getDropItemName(),
                    dropCount, total, avg));
        }
        targetStats.sort(Comparator.comparingLong(StatsDto.TargetStat::totalSoldPrice).reversed());

        List<StatsDto.MonthlyBucket> monthly = buildMonthly(raids, loots);

        return new StatsDto.Result(overview, memberStats, targetStats, monthly);
    }

    private List<StatsDto.MonthlyBucket> buildMonthly(List<Raid> raids, List<RaidLoot> loots) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        YearMonth now = YearMonth.from(LocalDate.now());
        List<YearMonth> months = new ArrayList<>();
        for (int i = 11; i >= 0; i--) months.add(now.minusMonths(i));

        Map<Long, YearMonth> raidMonth = new HashMap<>();
        for (Raid r : raids) {
            raidMonth.put(r.getId(), YearMonth.from(r.getScheduledAt()));
        }

        Map<YearMonth, Long> killByMonth = new HashMap<>();
        for (Raid r : raids) {
            if (r.getStatus() == RaidStatus.DONE) {
                killByMonth.merge(raidMonth.get(r.getId()), 1L, Long::sum);
            }
        }

        Map<YearMonth, Long> revenueByMonth = new HashMap<>();
        for (RaidLoot l : loots) {
            if (l.getSoldPrice() == null) continue;
            YearMonth ym = raidMonth.get(l.getRaidId());
            if (ym == null) continue;
            revenueByMonth.merge(ym, l.getSoldPrice(), Long::sum);
        }

        List<StatsDto.MonthlyBucket> buckets = new ArrayList<>();
        for (YearMonth m : months) {
            buckets.add(new StatsDto.MonthlyBucket(
                    m.format(fmt),
                    killByMonth.getOrDefault(m, 0L),
                    revenueByMonth.getOrDefault(m, 0L)));
        }
        return buckets;
    }
}
