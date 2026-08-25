package com.wind.guild.web;

import com.wind.guild.config.SessionKeys;
import com.wind.guild.domain.Raid;
import com.wind.guild.repository.MemberRepository;
import com.wind.guild.service.ChatService;
import com.wind.guild.service.DiscordNotifier;
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

    @GetMapping
    public List<RaidDto.ListView> list() {
        return raidService.list();
    }

    @GetMapping("/{id}")
    public RaidDto.DetailView get(@PathVariable Long id) {
        return raidService.get(id);
    }

    @PostMapping
    public RaidDto.DetailView create(@Valid @RequestBody RaidDto.CreateRequest req) {
        Raid r = raidService.create(req);
        discord.syncRaidCard(r.getId(), DiscordNotifier.RaidTrigger.CREATED);
        String icon = r.getTarget().getIcon() != null ? r.getTarget().getIcon() + " " : "";
        String time = r.getScheduledAt().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));
        push.sendToAll("🆕 새 레이드: " + r.getTarget().getName(),
                time + " · " + r.getTarget().getDropItemName(), "/raids/" + r.getId());
        chat.saveSystem("🆕 새 레이드 등록: " + icon + r.getTarget().getName() + " · " + time
                + (r.getMemo() != null && !r.getMemo().isBlank() ? "\n💬 " + r.getMemo() : ""),
                "RAID_VOTE", r.getId());
        return raidService.get(r.getId());
    }

    @PutMapping("/{id}")
    public RaidDto.DetailView update(@PathVariable Long id, @Valid @RequestBody RaidDto.UpdateRequest req) {
        raidService.update(id, req);
        discord.syncRaidCard(id, DiscordNotifier.RaidTrigger.STATUS);
        return raidService.get(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        raidService.delete(id);
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
            chat.saveSystem("🗳️ " + nick + " " + label + " · " + raid.targetName());
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
}
