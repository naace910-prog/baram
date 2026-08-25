package com.wind.guild.web;

import com.wind.guild.repository.RaidPartyRepository;
import com.wind.guild.service.DiscordNotifier;
import com.wind.guild.service.PartyService;
import com.wind.guild.web.dto.PartyDto;
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

    @GetMapping("/api/raids/{raidId}/parties")
    public List<PartyDto.PartyView> list(@PathVariable Long raidId) {
        return service.listByRaid(raidId);
    }

    @PostMapping("/api/raids/{raidId}/parties")
    public PartyDto.PartyView create(@PathVariable Long raidId,
                                     @Valid @RequestBody PartyDto.PartyCreateRequest req) {
        PartyDto.PartyView view = service.create(raidId, req);
        discord.syncRaidCard(raidId, DiscordNotifier.RaidTrigger.PARTY);
        return view;
    }

    @PutMapping("/api/parties/{partyId}")
    public PartyDto.PartyView update(@PathVariable Long partyId,
                                     @Valid @RequestBody PartyDto.PartyUpdateRequest req) {
        PartyDto.PartyView view = service.update(partyId, req);
        partyRepo.findById(partyId).ifPresent(p ->
                discord.syncRaidCard(p.getRaidId(), DiscordNotifier.RaidTrigger.PARTY));
        return view;
    }

    @DeleteMapping("/api/parties/{partyId}")
    public ResponseEntity<Void> delete(@PathVariable Long partyId) {
        Long raidId = partyRepo.findById(partyId).map(p -> p.getRaidId()).orElse(null);
        service.delete(partyId);
        if (raidId != null) discord.syncRaidCard(raidId, DiscordNotifier.RaidTrigger.PARTY);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/parties/{partyId}/members")
    public PartyDto.PartyView replaceMembers(@PathVariable Long partyId,
                                             @Valid @RequestBody PartyDto.MembersReplaceRequest req) {
        PartyDto.PartyView view = service.replaceMembers(partyId, req);
        partyRepo.findById(partyId).ifPresent(p ->
                discord.syncRaidCard(p.getRaidId(), DiscordNotifier.RaidTrigger.PARTY));
        return view;
    }
}
