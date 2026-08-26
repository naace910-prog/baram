package com.wind.guild.web;

import com.wind.guild.domain.ChannelType;
import com.wind.guild.repository.RaidRepository;
import com.wind.guild.repository.RaidPartyRepository;
import com.wind.guild.service.ChatService;
import com.wind.guild.service.DiscordNotifier;
import com.wind.guild.service.PartyService;
import com.wind.guild.web.dto.PartyDto;

import java.time.format.DateTimeFormatter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PartyController {

    private final PartyService service;
    private final DiscordNotifier discord;
    private final RaidPartyRepository partyRepo;
    private final RaidRepository raidRepo;
    private final ChatService chat;

    private void postPartySummaryToChat(Long raidId, String actionLabel) {
        try {
            var raid = raidRepo.findById(raidId).orElse(null);
            if (raid == null) return;
            var parties = service.listByRaid(raidId);
            StringBuilder sb = new StringBuilder();
            sb.append("🛡️ 파티 편성 ").append(actionLabel).append(" · ")
                    .append(raid.getTarget().getIcon() != null ? raid.getTarget().getIcon() + " " : "")
                    .append(raid.getTarget().getName()).append(" · ")
                    .append(raid.getScheduledAt().format(DateTimeFormatter.ofPattern("MM/dd HH:mm")));
            for (var p : parties) {
                sb.append("\n• ").append(p.channelType() == ChannelType.MAIN ? "본대" : "침략");
                if (p.channelNumber() != null) sb.append(" 채널 ").append(p.channelNumber());
                if (p.memo() != null && !p.memo().isBlank()) sb.append(" · ").append(p.memo());
                // '(N명)' 슬롯 카운트 제거 — 한 사람 여러 역할 시 부풀려짐
            }
            chat.saveSystem(sb.toString());
        } catch (Exception ignored) {}
    }

    @GetMapping("/api/raids/{raidId}/parties")
    public List<PartyDto.PartyView> list(@PathVariable Long raidId) {
        return service.listByRaid(raidId);
    }

    @PostMapping("/api/raids/{raidId}/parties")
    public PartyDto.PartyView create(@PathVariable Long raidId,
                                     @Valid @RequestBody PartyDto.PartyCreateRequest req) {
        PartyDto.PartyView view = service.create(raidId, req);
        discord.syncRaidCard(raidId, DiscordNotifier.RaidTrigger.PARTY);
        postPartySummaryToChat(raidId, "추가");
        return view;
    }

    @PutMapping("/api/parties/{partyId}")
    public PartyDto.PartyView update(@PathVariable Long partyId,
                                     @Valid @RequestBody PartyDto.PartyUpdateRequest req) {
        PartyDto.PartyView view = service.update(partyId, req);
        partyRepo.findById(partyId).ifPresent(p -> {
            discord.syncRaidCard(p.getRaidId(), DiscordNotifier.RaidTrigger.PARTY);
            postPartySummaryToChat(p.getRaidId(), "변경");
        });
        return view;
    }

    @DeleteMapping("/api/parties/{partyId}")
    public ResponseEntity<Void> delete(@PathVariable Long partyId) {
        Long raidId = partyRepo.findById(partyId).map(p -> p.getRaidId()).orElse(null);
        service.delete(partyId);
        if (raidId != null) {
            discord.syncRaidCard(raidId, DiscordNotifier.RaidTrigger.PARTY);
            postPartySummaryToChat(raidId, "삭제");
        }
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/parties/{partyId}/members")
    public PartyDto.PartyView replaceMembers(@PathVariable Long partyId,
                                             @Valid @RequestBody PartyDto.MembersReplaceRequest req) {
        PartyDto.PartyView view = service.replaceMembers(partyId, req);
        partyRepo.findById(partyId).ifPresent(p -> {
            discord.syncRaidCard(p.getRaidId(), DiscordNotifier.RaidTrigger.PARTY);
            postPartySummaryToChat(p.getRaidId(), "저장");
        });
        return view;
    }

    @PostMapping("/api/raids/{raidId}/parties/auto-assign")
    public PartyDto.AutoAssignResult autoAssign(@PathVariable Long raidId) {
        PartyDto.AutoAssignResult result = service.autoAssignFromPrevious(raidId);
        discord.syncRaidCard(raidId, DiscordNotifier.RaidTrigger.PARTY);
        try {
            String msg = String.format("🤖 직전 %s 파티 자동배정 · %d파티 · %d명 승계 · 신규 %d명 · 이탈 %d명",
                    result.basis(), result.carriedParties(), result.assignedMembers(),
                    result.newcomerCount(), result.droppedFromPrev());
            chat.saveSystem(msg);
        } catch (Exception ignored) {}
        return result;
    }
}
