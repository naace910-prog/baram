package com.wind.guild.service;

import com.wind.guild.domain.Member;
import com.wind.guild.domain.RaidParty;
import com.wind.guild.domain.RaidPartyMember;
import com.wind.guild.repository.MemberRepository;
import com.wind.guild.repository.RaidPartyMemberRepository;
import com.wind.guild.repository.RaidPartyRepository;
import com.wind.guild.repository.RaidRepository;
import com.wind.guild.web.dto.PartyDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PartyService {

    private final RaidRepository raidRepo;
    private final RaidPartyRepository partyRepo;
    private final RaidPartyMemberRepository memberRepo;
    private final MemberRepository membersRepo;

    @Transactional(readOnly = true)
    public List<PartyDto.PartyView> listByRaid(Long raidId) {
        List<RaidParty> parties = partyRepo.findByRaidIdOrderByDisplayOrderAsc(raidId);
        if (parties.isEmpty()) return List.of();

        List<Long> allPartyIds = parties.stream().map(RaidParty::getId).toList();
        Map<Long, List<RaidPartyMember>> membersByParty = new HashMap<>();
        for (Long pid : allPartyIds) {
            membersByParty.put(pid, memberRepo.findByPartyIdOrderByRoleAscDisplayOrderAsc(pid));
        }

        Set<Long> refMemberIds = new HashSet<>();
        for (RaidParty p : parties) {
            if (p.getMikeMemberId() != null) refMemberIds.add(p.getMikeMemberId());
        }
        for (List<RaidPartyMember> ms : membersByParty.values()) {
            for (RaidPartyMember m : ms) if (m.getMemberId() != null) refMemberIds.add(m.getMemberId());
        }
        Map<Long, String> nickMap = membersRepo.findAllById(refMemberIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getNickname));

        return parties.stream().map(p -> {
            List<PartyDto.MemberView> mvs = membersByParty.getOrDefault(p.getId(), List.of()).stream()
                    .map(m -> new PartyDto.MemberView(
                            m.getId(), m.getRole(), m.getMemberId(), m.getFreeName(),
                            m.getMemberId() != null ? nickMap.get(m.getMemberId()) : m.getFreeName(),
                            m.getDisplayOrder()))
                    .toList();
            String mikeNick = p.getMikeMemberId() != null
                    ? nickMap.get(p.getMikeMemberId())
                    : p.getMikeFreeName();
            return new PartyDto.PartyView(
                    p.getId(), p.getRaidId(), p.getChannelType(), p.getChannelNumber(), p.getMemo(),
                    p.getMikeMemberId(), p.getMikeFreeName(), mikeNick,
                    p.getDisplayOrder(), mvs);
        }).toList();
    }

    public PartyDto.PartyView create(Long raidId, PartyDto.PartyCreateRequest req) {
        raidRepo.findById(raidId).orElseThrow(() -> new IllegalArgumentException("레이드 없음: " + raidId));
        int order = partyRepo.findByRaidIdOrderByDisplayOrderAsc(raidId).size() + 1;
        RaidParty saved = partyRepo.save(RaidParty.builder()
                .raidId(raidId)
                .channelType(req.channelType())
                .channelNumber(req.channelNumber())
                .memo(req.memo())
                .mikeMemberId(req.mikeMemberId())
                .mikeFreeName(req.mikeFreeName())
                .displayOrder(order)
                .build());
        return toView(saved, List.of());
    }

    public PartyDto.PartyView update(Long partyId, PartyDto.PartyUpdateRequest req) {
        RaidParty p = partyRepo.findById(partyId).orElseThrow(() -> new IllegalArgumentException("파티 없음: " + partyId));
        p.setChannelType(req.channelType());
        p.setChannelNumber(req.channelNumber());
        p.setMemo(req.memo());
        p.setMikeMemberId(req.mikeMemberId());
        p.setMikeFreeName(req.mikeFreeName());
        if (req.displayOrder() != null) p.setDisplayOrder(req.displayOrder());
        return listByRaid(p.getRaidId()).stream()
                .filter(v -> v.id().equals(partyId)).findFirst().orElseThrow();
    }

    public void delete(Long partyId) {
        memberRepo.deleteByPartyId(partyId);
        partyRepo.deleteById(partyId);
    }

    public PartyDto.PartyView replaceMembers(Long partyId, PartyDto.MembersReplaceRequest req) {
        RaidParty p = partyRepo.findById(partyId).orElseThrow(() -> new IllegalArgumentException("파티 없음: " + partyId));
        memberRepo.deleteByPartyId(partyId);
        Map<String, Integer> orderPerRole = new HashMap<>();
        for (PartyDto.MemberEntry e : req.members()) {
            if ((e.memberId() == null) && (e.freeName() == null || e.freeName().isBlank())) {
                throw new IllegalArgumentException("멤버 항목은 memberId 또는 freeName 중 하나가 필요합니다");
            }
            int ord = orderPerRole.merge(e.role(), 1, Integer::sum);
            memberRepo.save(RaidPartyMember.builder()
                    .partyId(partyId)
                    .role(e.role())
                    .memberId(e.memberId())
                    .freeName(e.freeName())
                    .displayOrder(ord)
                    .build());
        }
        return listByRaid(p.getRaidId()).stream()
                .filter(v -> v.id().equals(partyId)).findFirst().orElseThrow();
    }

    private PartyDto.PartyView toView(RaidParty p, List<PartyDto.MemberView> members) {
        return new PartyDto.PartyView(
                p.getId(), p.getRaidId(), p.getChannelType(), p.getChannelNumber(), p.getMemo(),
                p.getMikeMemberId(), p.getMikeFreeName(), null,
                p.getDisplayOrder(), members);
    }
}
