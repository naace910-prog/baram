package com.wind.guild.web;

import com.wind.guild.config.SessionKeys;
import com.wind.guild.domain.Raid;
import com.wind.guild.repository.MemberRepository;
import com.wind.guild.repository.RaidRepository;
import com.wind.guild.service.ChatService;
import com.wind.guild.service.DiscordNotifier;
import com.wind.guild.service.RaidScheduler;
import com.wind.guild.service.RaidService;
import com.wind.guild.service.WebPushService;
import com.wind.guild.web.dto.RaidDto;

import java.time.format.DateTimeFormatter;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/raids")
@RequiredArgsConstructor
public class RaidController {

    private final RaidService raidService;
    private final DiscordNotifier discord;
    private final WebPushService push;
    private final ChatService chat;
    private final MemberRepository memberRepo;
    private final RaidRepository raidRepo;
    private final RaidScheduler scheduler;

    @GetMapping
    public List<RaidDto.ListView> list() {
        return raidService.list();
    }

    @GetMapping("/{id}")
    public RaidDto.DetailView get(@PathVariable Long id) {
        return raidService.get(id);
    }

    @PostMapping
    public RaidDto.DetailView create(@Valid @RequestBody RaidDto.CreateRequest req, HttpSession session) {
        String role = (String) session.getAttribute(SessionKeys.MEMBER_ROLE);
        if (!"MASTER".equals(role) && !"VICE".equals(role)) {
            throw new IllegalStateException("문주(부문주)만 레이드 등록 가능합니다");
        }
        Raid r = raidService.create(req);
        discord.syncRaidCard(r.getId(), DiscordNotifier.RaidTrigger.CREATED);
        String label;
        String icon;
        String dropItem;
        if (r.getTarget() != null) {
            label = r.getTarget().getName();
            icon = r.getTarget().getIcon() != null ? r.getTarget().getIcon() + " " : "";
            dropItem = r.getTarget().getDropItemName();
        } else if (r.getCategory() == com.wind.guild.domain.RaidCategory.FANG) {
            label = "어금니 레이드"; icon = "🐲 "; dropItem = "흑/묵/감/진룡 어금니";
        } else {
            label = "레이드"; icon = ""; dropItem = "";
        }
        String time = r.getScheduledAt() != null
                ? r.getScheduledAt().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
                : "⏳ 시간 미정";
        push.sendToAll("🆕 새 레이드: " + label, time + " · " + dropItem, "/raids/" + r.getId());
        chat.saveSystem("🆕 새 레이드 등록: " + icon + label + " · " + time
                + (r.getMemo() != null && !r.getMemo().isBlank() ? "\n💬 " + r.getMemo() : ""),
                "RAID_VOTE", r.getId());
        return raidService.get(r.getId());
    }

    @PutMapping("/{id}")
    public RaidDto.DetailView update(@PathVariable Long id, @Valid @RequestBody RaidDto.UpdateRequest req) {
        var result = raidService.updateInternal(id, req);
        discord.syncRaidCard(id, DiscordNotifier.RaidTrigger.STATUS);
        // 수동 완료 시 자동 생성된 다음 raid Discord 카드 발송 (스케줄러 경로와 동일 처리)
        if (result.nextRaid() != null) {
            discord.syncRaidCard(result.nextRaid().getId(), DiscordNotifier.RaidTrigger.CREATED);
            try {
                var next = result.nextRaid();
                String label = next.getTarget() != null ? next.getTarget().getName()
                        : (next.getCategory() != null ? next.getCategory().name() : "레이드");
                chat.saveSystem("🆕 다음 레이드 자동 등록 · " + label + " · 시간 미정 (Discord 카드 [🕐 시간 입력] 으로 설정)");
            } catch (Exception ignored) {}
        }
        return raidService.get(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        java.util.List<Long> discordMsgIds = raidService.delete(id);
        for (Long mid : discordMsgIds) discord.deleteRaidCard(mid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/votes")
    public RaidDto.DetailView vote(
            @PathVariable("id") Long raidId,
            @Valid @RequestBody RaidDto.VoteRequest req,
            HttpSession session) {
        Long memberId = (Long) session.getAttribute(SessionKeys.MEMBER_ID);
        if (memberId == null) throw new IllegalStateException("로그인이 필요합니다");
        raidService.vote(raidId, memberId, req.vote());
        discord.syncRaidCard(raidId, DiscordNotifier.RaidTrigger.VOTE);
        try {
            String nick = memberRepo.findById(memberId).map(m -> m.getNickname()).orElse("?");
            String label = switch (req.vote()) {
                case YES -> "✅ 참가";
                case NO -> "❌ 불참";
                case MAYBE -> "❓ 미정";
            };
            var raid = raidService.get(raidId);
            String raidLabel = raid.targetName() != null ? raid.targetName()
                    : (raid.category() != null
                        ? (raid.category().name().equals("FANG") ? "🐲 어금니 레이드" : "💀 해골왕 레이드")
                        : "레이드");
            chat.saveSystem("🗳️ " + nick + " " + label + " · " + raidLabel);
        } catch (Exception ignored) {}
        return raidService.get(raidId);
    }

    @PutMapping("/{id}/attendees")
    public RaidDto.DetailView setAttendees(
            @PathVariable("id") Long raidId,
            @Valid @RequestBody RaidDto.AttendeeRequest req) {
        raidService.setAttendees(raidId, req.memberIds());
        discord.syncRaidCard(raidId, DiscordNotifier.RaidTrigger.ATTENDEES);
        return raidService.get(raidId);
    }

    @PostMapping("/{id}/force-discord-card")
    public RaidDto.DetailView forceDiscordCard(@PathVariable("id") Long raidId, HttpSession session) {
        String role = (String) session.getAttribute(SessionKeys.MEMBER_ROLE);
        if (!"MASTER".equals(role) && !"VICE".equals(role)) {
            throw new IllegalStateException("문주/부문주만 실행 가능합니다");
        }
        // discordMessageId 초기화 → syncRaidCard 가 새 메시지 발송
        raidRepo.findById(raidId).ifPresent(r -> {
            r.setDiscordMessageId(null);
            raidRepo.save(r);
        });
        discord.syncRaidCard(raidId, DiscordNotifier.RaidTrigger.CREATED);
        return raidService.get(raidId);
    }

    @PostMapping("/{id}/send-pre30")
    public RaidDto.DetailView sendPre30Manual(@PathVariable("id") Long raidId, HttpSession session) {
        String role = (String) session.getAttribute(SessionKeys.MEMBER_ROLE);
        if (!"MASTER".equals(role) && !"VICE".equals(role)) {
            throw new IllegalStateException("문주/부문주만 실행 가능합니다");
        }
        Raid r = raidRepo.findById(raidId)
                .orElseThrow(() -> new IllegalArgumentException("레이드 없음: " + raidId));
        scheduler.dispatchPre30(r, /*manual*/ true);
        return raidService.get(raidId);
    }
}
