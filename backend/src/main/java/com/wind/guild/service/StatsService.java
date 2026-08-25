package com.wind.guild.service;

import com.wind.guild.domain.*;
import com.wind.guild.repository.*;
import com.wind.guild.web.dto.StatsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        long unpaidTotal = shares.stream()
                .filter(s -> !s.isPaid())
                .mapToLong(LootShare::getShare).sum();

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

        Map<Long, List<Raid>> raidsByTarget = new HashMap<>();
        for (Raid r : raids) raidsByTarget.computeIfAbsent(r.getTarget().getId(), k -> new ArrayList<>()).add(r);
        Map<Long, List<RaidLoot>> lootsByRaid = new HashMap<>();
        for (RaidLoot l : loots) lootsByRaid.computeIfAbsent(l.getRaidId(), k -> new ArrayList<>()).add(l);

        List<StatsDto.TargetStat> targetStats = new ArrayList<>();
        for (RaidTarget t : targets) {
            List<Raid> tRaids = raidsByTarget.getOrDefault(t.getId(), List.of());
            long kill = tRaids.stream().filter(r -> r.getStatus() == RaidStatus.DONE).count();
            long total = 0, count = 0;
            for (Raid r : tRaids) {
                for (RaidLoot l : lootsByRaid.getOrDefault(r.getId(), List.of())) {
                    if (l.getSoldPrice() != null) {
                        total += l.getSoldPrice();
                        count++;
                    }
                }
            }
            long avg = count > 0 ? total / count : 0;
            targetStats.add(new StatsDto.TargetStat(
                    t.getId(), t.getName(), t.getDropItemName(),
                    kill, total, avg));
        }
        targetStats.sort(Comparator.comparingLong(StatsDto.TargetStat::totalSoldPrice).reversed());

        return new StatsDto.Result(overview, memberStats, targetStats);
    }
}
