package com.wind.guild.service;

import com.wind.guild.domain.LootShare;
import com.wind.guild.domain.Member;
import com.wind.guild.domain.RaidLoot;
import com.wind.guild.domain.RaidTarget;
import com.wind.guild.repository.LootShareRepository;
import com.wind.guild.repository.MemberRepository;
import com.wind.guild.repository.RaidLootRepository;
import com.wind.guild.repository.RaidTargetRepository;
import com.wind.guild.web.dto.LootDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LootService {

    private final RaidLootRepository lootRepository;
    private final LootShareRepository shareRepository;
    private final MemberRepository memberRepository;
    private final RaidTargetRepository targetRepository;

    @Transactional(readOnly = true)
    public List<LootDto.LootView> listByRaid(Long raidId) {
        List<RaidLoot> loots = lootRepository.findByRaidId(raidId);
        List<LootShare> allShares = loots.stream()
                .flatMap(l -> shareRepository.findByLootId(l.getId()).stream()).toList();
        Set<Long> memberIds = new HashSet<>();
        for (LootShare s : allShares) {
            memberIds.add(s.getMemberId());
            if (s.getPaidBy() != null) memberIds.add(s.getPaidBy());
        }
        Map<Long, String> nickMap = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getNickname));

        Set<Long> targetIds = loots.stream()
                .map(RaidLoot::getTargetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, RaidTarget> targetMap = targetRepository.findAllById(targetIds).stream()
                .collect(Collectors.toMap(RaidTarget::getId, t -> t));

        return loots.stream().map(l -> toView(l, nickMap, targetMap)).toList();
    }

    private LootDto.LootView toView(RaidLoot l, Map<Long, String> nickMap, Map<Long, RaidTarget> targetMap) {
        List<LootDto.ShareView> shares = shareRepository.findByLootId(l.getId()).stream()
                .map(s -> new LootDto.ShareView(
                        s.getId(), s.getMemberId(),
                        nickMap.getOrDefault(s.getMemberId(), "?"),
                        s.getShare(), s.isPaid(), s.getPaidAt(),
                        s.getPaidBy() != null ? nickMap.get(s.getPaidBy()) : null,
                        s.isReceived(), s.getReceivedAt()))
                .toList();
        RaidTarget t = l.getTargetId() != null ? targetMap.get(l.getTargetId()) : null;
        return new LootDto.LootView(
                l.getId(), l.getRaidId(),
                l.getTargetId(),
                t != null ? t.getName() : null,
                t != null ? t.getIcon() : null,
                l.getItemName(), l.isDropped(), l.getSoldPrice(), l.getSoldAt(), l.getMemo(), shares);
    }

    public RaidLoot upsert(Long raidId, Long lootId, LootDto.UpsertLootRequest req) {
        RaidLoot l = (lootId == null)
                ? RaidLoot.builder().raidId(raidId).build()
                : lootRepository.findById(lootId)
                    .orElseThrow(() -> new IllegalArgumentException("득템 없음: " + lootId));
        l.setTargetId(req.targetId());
        l.setItemName(req.itemName());
        l.setDropped(req.dropped());
        Long prevPrice = l.getSoldPrice();
        l.setSoldPrice(req.soldPrice());
        if (req.soldPrice() != null && (prevPrice == null || !prevPrice.equals(req.soldPrice()))) {
            l.setSoldAt(LocalDateTime.now());
        }
        l.setMemo(req.memo());
        return lootRepository.save(l);
    }

    public int bulkAdd(Long raidId, LootDto.BulkAddRequest req) {
        int created = 0;
        for (LootDto.BulkDropEntry e : req.drops()) {
            RaidTarget t = targetRepository.findById(e.targetId())
                    .orElseThrow(() -> new IllegalArgumentException("대상 없음: " + e.targetId()));
            Long unitPrice = (e.unitPrice() != null && e.unitPrice() > 0) ? e.unitPrice() : null;
            for (int i = 0; i < e.quantity(); i++) {
                lootRepository.save(RaidLoot.builder()
                        .raidId(raidId)
                        .targetId(t.getId())
                        .itemName(t.getDropItemName())
                        .dropped(true)
                        .soldPrice(unitPrice)
                        .soldAt(unitPrice != null ? LocalDateTime.now() : null)
                        .build());
                created++;
            }
        }
        return created;
    }

    public void delete(Long lootId) {
        shareRepository.deleteByLootId(lootId);
        lootRepository.deleteById(lootId);
    }

    public void distribute(Long lootId, List<Long> memberIds) {
        RaidLoot l = lootRepository.findById(lootId)
                .orElseThrow(() -> new IllegalArgumentException("득템 없음: " + lootId));
        if (l.getSoldPrice() == null || l.getSoldPrice() <= 0) {
            throw new IllegalStateException("판매금액이 등록되지 않아 분배할 수 없습니다");
        }
        if (memberIds == null || memberIds.isEmpty()) {
            throw new IllegalArgumentException("분배 대상 문파원이 없습니다");
        }
        shareRepository.deleteByLootId(lootId);
        shareRepository.flush();
        long total = l.getSoldPrice();
        long per = total / memberIds.size();
        long remainder = total - per * memberIds.size();
        for (int i = 0; i < memberIds.size(); i++) {
            long amount = per + (i == 0 ? remainder : 0);
            shareRepository.save(LootShare.builder()
                    .lootId(lootId)
                    .memberId(memberIds.get(i))
                    .share(amount)
                    .paid(false)
                    .build());
        }
    }

    public void markPaid(Long shareId, boolean paid, Long actorMemberId) {
        LootShare s = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("분배 없음: " + shareId));
        s.setPaid(paid);
        s.setPaidAt(paid ? LocalDateTime.now() : null);
        s.setPaidBy(paid ? actorMemberId : null);
        if (!paid) {
            s.setReceived(false);
            s.setReceivedAt(null);
        }
    }

    public void markReceived(Long shareId, boolean received, Long actorMemberId) {
        LootShare s = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("분배 없음: " + shareId));
        if (!Objects.equals(s.getMemberId(), actorMemberId)) {
            throw new IllegalStateException("본인의 분배금만 수령 확인할 수 있습니다");
        }
        if (received && !s.isPaid()) {
            throw new IllegalStateException("아직 지급되지 않았습니다");
        }
        s.setReceived(received);
        s.setReceivedAt(received ? LocalDateTime.now() : null);
    }

    public void updateShareAmount(Long shareId, long amount) {
        if (amount < 0) throw new IllegalArgumentException("금액은 0 이상이어야 합니다");
        LootShare s = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("분배 없음: " + shareId));
        s.setShare(amount);
    }
}
