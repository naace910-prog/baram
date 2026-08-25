package com.wind.guild.web;

import com.wind.guild.config.SessionKeys;
import com.wind.guild.domain.Raid;
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
        String title = "🆕 새 레이드: " + r.getTarget().getName();
        String body = r.getScheduledAt().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
                + " · " + r.getTarget().getDropItemName();
        push.sendToAll(title, body, "/raids/" + r.getId());
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
