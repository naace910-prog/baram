package com.wind.guild.service;

import com.wind.guild.domain.*;
import com.wind.guild.repository.*;
import com.wind.guild.web.dto.RaidDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
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
        // 중복 등록 방지: 같은 (target, scheduledAt) 조합이 이미 있으면 그것을 반환 (더블클릭 대응)
        LocalDateTime scheduledAt = req.scheduledAt();
        java.util.Optional<Raid> existing;
        if (target != null) {
            existing = raidRepository.findFirstByTarget_IdAndScheduledAt(target.getId(), scheduledAt);
        } else {
            existing = raidRepository.findFirstByCategoryAndScheduledAtAndTargetIsNull(category, scheduledAt);
        }
        if (existing.isPresent()) {
            return existing.get();
        }
        Raid saved = raidRepository.save(Raid.builder()
                .target(target)
                .category(category)
                .scheduledAt(scheduledAt)
                .memo(req.memo())
                .status(RaidStatus.PLANNED)
                .build());
        // 신규 레이드는 본대 파티 1개 자동 생성 (사용자 요청 · 매번 새로 만드는 수고 절약)
        try {
            partyRepository.save(RaidParty.builder()
                    .raidId(saved.getId())
                    .channelType(ChannelType.MAIN)
                    .displayOrder(1)
                    .build());
        } catch (Exception ignored) {}
        return saved;
    }

    /**
     * 완료된 레이드와 같은 대상/카테고리로 새 레이드를 자동 생성.
     * scheduledAt = null (사용자가 Discord [🕐 시간 입력] 이나 사이트에서 나중에 설정).
     * 중복 방지: 이미 같은 대상+null 시각 이거나 미완료 raid 있으면 skip.
     */
    public Raid createNextAfterDone(Raid prev) {
        RaidTarget target = prev.getTarget();
        RaidCategory category = prev.getCategory();
        if (category == null) {
            log.info("createNextAfterDone skip: category=null (prev raid={})", prev.getId());
            return null;
        }

        // 이미 같은 target/category 로 미완료(PLANNED) raid 있으면 skip (스팸 방지)
        List<Raid> planned = raidRepository.findByStatusOrderByScheduledAtAsc(RaidStatus.PLANNED);
        for (Raid p : planned) {
            boolean sameTarget = (target != null && p.getTarget() != null && target.getId().equals(p.getTarget().getId()))
                    || (target == null && p.getTarget() == null && category == p.getCategory());
            if (sameTarget) {
                log.info("createNextAfterDone skip: already exists PLANNED raid={} same target/category (prev={})", p.getId(), prev.getId());
                return null;
            }
        }

        Raid next = raidRepository.save(Raid.builder()
                .target(target)
                .category(category)
                .scheduledAt(null)  // 시간 미정 (Discord 카드에 [🕐 시간 입력] 버튼 노출)
                .status(RaidStatus.PLANNED)
                .build());
        // 본대 파티 자동
        try {
            partyRepository.save(RaidParty.builder()
                    .raidId(next.getId())
                    .channelType(ChannelType.MAIN)
                    .displayOrder(1)
                    .build());
        } catch (Exception ignored) {}
        log.info("createNextAfterDone created: raid={} target={} category={} (prev={})",
                next.getId(),
                target != null ? target.getName() : "null",
                category, prev.getId());
        return next;
    }

    public Raid update(Long id, RaidDto.UpdateRequest req) {
        return updateInternal(id, req).raid();
    }

    /** 자동 다음 레이드 정보까지 반환 (컨트롤러가 syncRaidCard 호출용). */
    public UpdateResult updateInternal(Long id, RaidDto.UpdateRequest req) {
        Raid r = raidRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("레이드 없음: " + id));
        RaidTarget target = null;
        if (req.targetId() != null) {
            target = targetRepository.findById(req.targetId())
                    .orElseThrow(() -> new IllegalArgumentException("대상 없음: " + req.targetId()));
        }
        RaidStatus prevStatus = r.getStatus();
        r.setTarget(target);
        if (req.category() != null) r.setCategory(req.category());
        r.setScheduledAt(req.scheduledAt());
        r.setStatus(req.status());
        r.setMemo(req.memo());
        Raid nextRaid = null;
        if (prevStatus != RaidStatus.DONE && req.status() == RaidStatus.DONE) {
            try { nextRaid = createNextAfterDone(r); } catch (Exception ignored) {}
        }
        return new UpdateResult(r, nextRaid);
    }

    public record UpdateResult(Raid raid, Raid nextRaid) {}

    /** 레이드 + 관련 데이터 삭제. Discord 메시지 ID 리스트 반환 (raid card + 개별 loot cards). */
    public java.util.List<Long> delete(Long id) {
        // 삭제 전에 모든 discordMessageId 수집
        java.util.List<Long> msgIds = new java.util.ArrayList<>();
        raidRepository.findById(id).map(Raid::getDiscordMessageId).ifPresent(msgIds::add);
        java.util.List<RaidLoot> loots = lootRepository.findByRaidId(id);
        for (RaidLoot l : loots) {
            if (l.getDiscordMessageId() != null) msgIds.add(l.getDiscordMessageId());
        }
        // 정산·득템·파티(멤버포함)·투표·참가확정 모두 cascade 삭제
        for (RaidLoot l : loots) {
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
        return msgIds;
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
