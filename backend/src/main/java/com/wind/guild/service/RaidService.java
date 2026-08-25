package com.wind.guild.service;

import com.wind.guild.domain.*;
import com.wind.guild.repository.*;
import com.wind.guild.web.dto.RaidDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RaidService {

    private final RaidRepository raidRepository;
    private final RaidTargetRepository targetRepository;
    private final RaidVoteRepository voteRepository;
    private final RaidAttendeeRepository attendeeRepository;
    private final MemberRepository memberRepository;
    private final RaidLootRepository lootRepository;
    private final LootShareRepository lootShareRepository;
    private final RaidPartyRepository partyRepository;
    private final RaidPartyMemberRepository partyMemberRepository;

    @Transactional(readOnly = true)
    public List<RaidDto.ListView> list() {
        List<Raid> raids = raidRepository.findAllByOrderByScheduledAtDesc();
        if (raids.isEmpty()) return List.of();

        // 배치 조회: 모든 raid 의 vote/attendee + 닉네임
        Map<Long, List<RaidVote>> votesByRaid = new HashMap<>();
        Map<Long, List<Long>> attendeesByRaid = new HashMap<>();
        Set<Long> refMemberIds = new HashSet<>();
        for (Raid r : raids) {
            List<RaidVote> vs = voteRepository.findByRaidId(r.getId());
            votesByRaid.put(r.getId(), vs);
            vs.forEach(v -> refMemberIds.add(v.getMemberId()));
            List<Long> aids = attendeeRepository.findByRaidId(r.getId()).stream()
                    .map(RaidAttendee::getMemberId).toList();
            attendeesByRaid.put(r.getId(), aids);
            refMemberIds.addAll(aids);
        }
        Map<Long, String> nickMap = memberRepository.findAllById(refMemberIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getNickname));

        return raids.stream().map(r -> toListView(r, votesByRaid, attendeesByRaid, nickMap)).toList();
    }

    private RaidDto.ListView toListView(Raid r,
                                        Map<Long, List<RaidVote>> votesByRaid,
                                        Map<Long, List<Long>> attendeesByRaid,
                                        Map<Long, String> nickMap) {
        List<RaidVote> votes = votesByRaid.getOrDefault(r.getId(), List.of());
        int y = 0, n = 0, m = 0;
        for (RaidVote v : votes) {
            switch (v.getVote()) {
                case YES -> y++;
                case NO -> n++;
                case MAYBE -> m++;
            }
        }
        List<RaidDto.VoteView> voteViews = votes.stream()
                .map(v -> new RaidDto.VoteView(v.getMemberId(),
                        nickMap.getOrDefault(v.getMemberId(), "?"),
                        v.getVote(), v.getVotedAt()))
                .toList();
        RaidTarget t = r.getTarget();
        return new RaidDto.ListView(
                r.getId(),
                r.getCategory(),
                t != null ? t.getId() : null,
                t != null ? t.getName() : null,
                t != null ? t.getIcon() : null,
                t != null ? t.getDropItemName() : null,
                r.getScheduledAt(), r.getStatus(), r.getMemo(), y, n, m,
                voteViews, attendeesByRaid.getOrDefault(r.getId(), List.of()));
    }

    @Transactional(readOnly = true)
    public RaidDto.DetailView get(Long id) {
        Raid r = raidRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("레이드 없음: " + id));
        List<RaidVote> votes = voteRepository.findByRaidId(id);
        Map<Long, String> nickCache = new HashMap<>();
        List<RaidDto.VoteView> voteViews = votes.stream()
                .map(v -> new RaidDto.VoteView(
                        v.getMemberId(),
                        nickCache.computeIfAbsent(v.getMemberId(),
                                mid -> memberRepository.findById(mid).map(Member::getNickname).orElse("?")),
                        v.getVote(), v.getVotedAt()))
                .toList();
        List<Long> attendees = attendeeRepository.findByRaidId(id).stream()
                .map(RaidAttendee::getMemberId).toList();
        RaidTarget t = r.getTarget();
        return new RaidDto.DetailView(
                r.getId(),
                r.getCategory(),
                t != null ? t.getId() : null,
                t != null ? t.getName() : null,
                t != null ? t.getIcon() : null,
                t != null ? t.getDropItemName() : null,
                r.getScheduledAt(), r.getStatus(), r.getMemo(), voteViews, attendees);
    }

    public Raid create(RaidDto.CreateRequest req) {
        RaidTarget target = null;
        RaidCategory category = req.category();
        if (req.targetId() != null) {
            target = targetRepository.findById(req.targetId())
                    .orElseThrow(() -> new IllegalArgumentException("대상 없음: " + req.targetId()));
            if (category == null) category = target.getCategory();
        }
        if (category == null) {
            throw new IllegalArgumentException("레이드 카테고리를 지정해주세요");
        }
        return raidRepository.save(Raid.builder()
                .target(target)
                .category(category)
                .scheduledAt(req.scheduledAt())
                .memo(req.memo())
                .status(RaidStatus.PLANNED)
                .build());
    }

    public Raid update(Long id, RaidDto.UpdateRequest req) {
        Raid r = raidRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("레이드 없음: " + id));
        RaidTarget target = null;
        if (req.targetId() != null) {
            target = targetRepository.findById(req.targetId())
                    .orElseThrow(() -> new IllegalArgumentException("대상 없음: " + req.targetId()));
        }
        r.setTarget(target);
        if (req.category() != null) r.setCategory(req.category());
        r.setScheduledAt(req.scheduledAt());
        r.setStatus(req.status());
        r.setMemo(req.memo());
        return r;
    }

    public void delete(Long id) {
        // 정산·득템·파티(멤버포함)·투표·참가확정 모두 cascade 삭제
        for (RaidLoot l : lootRepository.findByRaidId(id)) {
            lootShareRepository.deleteByLootId(l.getId());
        }
        lootRepository.deleteByRaidId(id);
        for (RaidParty p : partyRepository.findByRaidIdOrderByDisplayOrderAsc(id)) {
            partyMemberRepository.deleteByPartyId(p.getId());
        }
        partyRepository.deleteByRaidId(id);
        voteRepository.deleteByRaidId(id);
        attendeeRepository.deleteByRaidId(id);
        raidRepository.deleteById(id);
    }

    public void vote(Long raidId, Long memberId, VoteType vote) {
        raidRepository.findById(raidId)
                .orElseThrow(() -> new IllegalArgumentException("레이드 없음: " + raidId));
        memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("문파원 없음: " + memberId));
        RaidVote v = voteRepository.findByRaidIdAndMemberId(raidId, memberId)
                .orElseGet(() -> RaidVote.builder()
                        .raidId(raidId).memberId(memberId).build());
        v.setVote(vote);
        voteRepository.save(v);
    }

    public void voteByDiscordUser(Long raidId, String discordUserId, VoteType vote) {
        Member m = memberRepository.findByDiscordUserId(discordUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "디스코드 계정이 문파원과 연결되지 않았습니다. 사이트 > 문파원관리에서 디스코드ID 등록 필요."));
        vote(raidId, m.getId(), vote);
    }

    public void setAttendees(Long raidId, List<Long> memberIds) {
        raidRepository.findById(raidId)
                .orElseThrow(() -> new IllegalArgumentException("레이드 없음: " + raidId));
        attendeeRepository.deleteByRaidId(raidId);
        attendeeRepository.flush();
        for (Long mid : memberIds) {
            attendeeRepository.save(RaidAttendee.builder()
                    .raidId(raidId).memberId(mid).build());
        }
    }
}
