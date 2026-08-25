package com.wind.guild.service;

import com.wind.guild.domain.*;
import com.wind.guild.repository.*;
import com.wind.guild.web.dto.RaidDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class RaidService {

    private final RaidRepository raidRepository;
    private final RaidTargetRepository targetRepository;
    private final RaidVoteRepository voteRepository;
    private final RaidAttendeeRepository attendeeRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public List<RaidDto.ListView> list() {
        List<Raid> raids = raidRepository.findAllByOrderByScheduledAtDesc();
        return raids.stream().map(this::toListView).toList();
    }

    private RaidDto.ListView toListView(Raid r) {
        List<RaidVote> votes = voteRepository.findByRaidId(r.getId());
        int y = 0, n = 0, m = 0;
        for (RaidVote v : votes) {
            switch (v.getVote()) {
                case YES -> y++;
                case NO -> n++;
                case MAYBE -> m++;
            }
        }
        RaidTarget t = r.getTarget();
        return new RaidDto.ListView(
                r.getId(), t.getId(), t.getName(), t.getDropItemName(),
                r.getScheduledAt(), r.getStatus(), r.getMemo(), y, n, m);
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
                r.getId(), t.getId(), t.getName(), t.getDropItemName(),
                r.getScheduledAt(), r.getStatus(), r.getMemo(), voteViews, attendees);
    }

    public Raid create(RaidDto.CreateRequest req) {
        RaidTarget target = targetRepository.findById(req.targetId())
                .orElseThrow(() -> new IllegalArgumentException("대상 없음: " + req.targetId()));
        return raidRepository.save(Raid.builder()
                .target(target)
                .scheduledAt(req.scheduledAt())
                .memo(req.memo())
                .status(RaidStatus.PLANNED)
                .build());
    }

    public Raid update(Long id, RaidDto.UpdateRequest req) {
        Raid r = raidRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("레이드 없음: " + id));
        RaidTarget target = targetRepository.findById(req.targetId())
                .orElseThrow(() -> new IllegalArgumentException("대상 없음: " + req.targetId()));
        r.setTarget(target);
        r.setScheduledAt(req.scheduledAt());
        r.setStatus(req.status());
        r.setMemo(req.memo());
        return r;
    }

    public void delete(Long id) {
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
        for (Long mid : memberIds) {
            attendeeRepository.save(RaidAttendee.builder()
                    .raidId(raidId).memberId(mid).build());
        }
    }
}
