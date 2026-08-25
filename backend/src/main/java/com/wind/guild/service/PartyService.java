package com.wind.guild.service;

import com.wind.guild.domain.ChannelType;
import com.wind.guild.domain.Member;
import com.wind.guild.domain.Raid;
import com.wind.guild.domain.RaidAttendee;
import com.wind.guild.domain.RaidParty;
import com.wind.guild.domain.RaidPartyMember;
import com.wind.guild.domain.RaidVote;
import com.wind.guild.domain.VoteType;
import com.wind.guild.repository.MemberRepository;
import com.wind.guild.repository.RaidAttendeeRepository;
import com.wind.guild.repository.RaidPartyMemberRepository;
import com.wind.guild.repository.RaidPartyRepository;
import com.wind.guild.repository.RaidRepository;
import com.wind.guild.repository.RaidVoteRepository;
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
    private final RaidAttendeeRepository attendeeRepo;
    private final RaidVoteRepository voteRepo;

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
        RaidParty p = partyRepo.findById(partyId).orElse(null);
        memberRepo.deleteByPartyId(partyId);
        partyRepo.deleteById(partyId);
        if (p != null) syncAttendeesFromParties(p.getRaidId());
    }

    public PartyDto.PartyView replaceMembers(Long partyId, PartyDto.MembersReplaceRequest req) {
        RaidParty p = partyRepo.findById(partyId).orElseThrow(() -> new IllegalArgumentException("파티 없음: " + partyId));
        memberRepo.deleteByPartyId(partyId);
        memberRepo.flush();
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
        syncAttendeesFromParties(p.getRaidId());
        return listByRaid(p.getRaidId()).stream()
                .filter(v -> v.id().equals(partyId)).findFirst().orElseThrow();
    }

    public PartyDto.AutoAssignResult autoAssignFromPrevious(Long raidId) {
        Raid target = raidRepo.findById(raidId).orElseThrow(() -> new IllegalArgumentException("레이드 없음: " + raidId));

        List<Raid> candidates;
        String basis;
        if (target.getTarget() != null) {
            candidates = raidRepo.findTop3ByTarget_IdAndScheduledAtLessThanOrderByScheduledAtDesc(
                    target.getTarget().getId(), target.getScheduledAt());
            basis = target.getTarget().getName();
        } else if (target.getCategory() != null) {
            candidates = raidRepo.findTop3ByCategoryAndScheduledAtLessThanOrderByScheduledAtDesc(
                    target.getCategory(), target.getScheduledAt());
            basis = target.getCategory().name();
        } else {
            throw new IllegalStateException("대상/카테고리 정보가 없습니다");
        }

        Raid prev = null;
        for (Raid r : candidates) {
            if (!partyRepo.findByRaidIdOrderByDisplayOrderAsc(r.getId()).isEmpty()) {
                prev = r; break;
            }
        }
        if (prev == null) {
            throw new IllegalStateException("직전 " + basis + " 레이드에 파티 편성 이력이 없습니다");
        }

        Set<Long> attending = voteRepo.findByRaidId(raidId).stream()
                .filter(v -> v.getVote() == VoteType.YES)
                .map(RaidVote::getMemberId)
                .collect(Collectors.toSet());

        List<RaidParty> prevParties = partyRepo.findByRaidIdOrderByDisplayOrderAsc(prev.getId());

        for (RaidParty old : partyRepo.findByRaidIdOrderByDisplayOrderAsc(raidId)) {
            memberRepo.deleteByPartyId(old.getId());
        }
        memberRepo.flush();
        partyRepo.deleteByRaidId(raidId);
        partyRepo.flush();

        Set<Long> assigned = new HashSet<>();
        int order = 1;
        int carriedParties = 0;
        for (RaidParty pp : prevParties) {
            List<RaidPartyMember> pms = memberRepo.findByPartyIdOrderByRoleAscDisplayOrderAsc(pp.getId());
            List<RaidPartyMember> kept = pms.stream()
                    .filter(m -> m.getMemberId() != null && attending.contains(m.getMemberId()))
                    .toList();
            Long newMike = (pp.getMikeMemberId() != null && attending.contains(pp.getMikeMemberId()))
                    ? pp.getMikeMemberId() : null;
            if (kept.isEmpty() && newMike == null) continue;

            RaidParty np = partyRepo.save(RaidParty.builder()
                    .raidId(raidId)
                    .channelType(pp.getChannelType())
                    .channelNumber(pp.getChannelNumber())
                    .memo(pp.getMemo())
                    .mikeMemberId(newMike)
                    .displayOrder(order++)
                    .build());
            Map<String, Integer> ord = new HashMap<>();
            for (RaidPartyMember m : kept) {
                int o = ord.merge(m.getRole(), 1, Integer::sum);
                memberRepo.save(RaidPartyMember.builder()
                        .partyId(np.getId())
                        .role(m.getRole())
                        .memberId(m.getMemberId())
                        .displayOrder(o)
                        .build());
                assigned.add(m.getMemberId());
            }
            if (newMike != null) assigned.add(newMike);
            carriedParties++;
        }

        Set<Long> newcomers = new HashSet<>(attending);
        newcomers.removeAll(assigned);
        int newcomerCount = newcomers.size();
        if (!newcomers.isEmpty()) {
            RaidParty overflow = partyRepo.save(RaidParty.builder()
                    .raidId(raidId)
                    .channelType(ChannelType.MAIN)
                    .memo("신규 참가자 · 역할 재배정 필요")
                    .displayOrder(order++)
                    .build());
            int o = 0;
            for (Long mid : newcomers) {
                memberRepo.save(RaidPartyMember.builder()
                        .partyId(overflow.getId())
                        .role("격수")
                        .memberId(mid)
                        .displayOrder(++o)
                        .build());
            }
        }

        int droppedFromPrev = 0;
        Set<Long> prevAll = new HashSet<>();
        for (RaidParty pp : prevParties) {
            if (pp.getMikeMemberId() != null) prevAll.add(pp.getMikeMemberId());
            for (RaidPartyMember m : memberRepo.findByPartyIdOrderByRoleAscDisplayOrderAsc(pp.getId())) {
                if (m.getMemberId() != null) prevAll.add(m.getMemberId());
            }
        }
        for (Long pid : prevAll) if (!attending.contains(pid)) droppedFromPrev++;

        syncAttendeesFromParties(raidId);

        List<PartyDto.PartyView> parties = listByRaid(raidId);
        return new PartyDto.AutoAssignResult(
                basis,
                prev.getId(),
                prev.getScheduledAt(),
                carriedParties,
                assigned.size(),
                newcomerCount,
                droppedFromPrev,
                parties);
    }

    private void syncAttendeesFromParties(Long raidId) {
        List<RaidParty> parties = partyRepo.findByRaidIdOrderByDisplayOrderAsc(raidId);
        Set<Long> distinctIds = new HashSet<>();
        for (RaidParty pp : parties) {
            if (pp.getMikeMemberId() != null) distinctIds.add(pp.getMikeMemberId());
            for (RaidPartyMember m : memberRepo.findByPartyIdOrderByRoleAscDisplayOrderAsc(pp.getId())) {
                if (m.getMemberId() != null) distinctIds.add(m.getMemberId());
            }
        }
        attendeeRepo.deleteByRaidId(raidId);
        attendeeRepo.flush();  // insert 전에 delete 실행 확정 (unique 제약 충돌 방지)
        for (Long mid : distinctIds) {
            attendeeRepo.save(RaidAttendee.builder().raidId(raidId).memberId(mid).build());
        }
    }

    private PartyDto.PartyView toView(RaidParty p, List<PartyDto.MemberView> members) {
        return new PartyDto.PartyView(
                p.getId(), p.getRaidId(), p.getChannelType(), p.getChannelNumber(), p.getMemo(),
                p.getMikeMemberId(), p.getMikeFreeName(), null,
                p.getDisplayOrder(), members);
    }
}
