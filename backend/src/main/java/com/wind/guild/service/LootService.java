package com.wind.guild.service;

import com.wind.guild.domain.LootShare;
import com.wind.guild.domain.Member;
import com.wind.guild.domain.RaidLoot;
import com.wind.guild.repository.LootShareRepository;
import com.wind.guild.repository.MemberRepository;
import com.wind.guild.repository.RaidLootRepository;
import com.wind.guild.web.dto.LootDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LootService {

    private final RaidLootRepository lootRepository;
    private final LootShareRepository shareRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public List<LootDto.LootView> listByRaid(Long raidId) {
        List<RaidLoot> loots = lootRepository.findByRaidId(raidId);
        List<Long> memberIds = loots.stream()
                .flatMap(l -> shareRepository.findByLootId(l.getId()).stream())
                .map(LootShare::getMemberId).distinct().toList();
        Map<Long, String> nickMap = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getNickname));
        return loots.stream().map(l -> toView(l, nickMap)).toList();
    }

    private LootDto.LootView toView(RaidLoot l, Map<Long, String> nickMap) {
        List<LootDto.ShareView> shares = shareRepository.findByLootId(l.getId()).stream()
                .map(s -> new LootDto.ShareView(
                        s.getId(), s.getMemberId(),
                        nickMap.getOrDefault(s.getMemberId(), "?"),
                        s.getShare(), s.isPaid(), s.getPaidAt()))
                .toList();
        return new LootDto.LootView(l.getId(), l.getRaidId(), l.getItemName(),
                l.isDropped(), l.getSoldPrice(), l.getSoldAt(), l.getMemo(), shares);
    }

    public RaidLoot upsert(Long raidId, Long lootId, LootDto.UpsertLootRequest req) {
        RaidLoot l = (lootId == null)
                ? RaidLoot.builder().raidId(raidId).build()
                : lootRepository.findById(lootId)
                    .orElseThrow(() -> new IllegalArgumentException("득템 없음: " + lootId));
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
        shareRepository.flush();  // insert 전에 delete 확정
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

    public void markPaid(Long shareId, boolean paid) {
        LootShare s = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("분배 없음: " + shareId));
        s.setPaid(paid);
        s.setPaidAt(paid ? LocalDateTime.now() : null);
    }
}
