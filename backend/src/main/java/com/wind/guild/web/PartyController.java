package com.wind.guild.web;

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

    @GetMapping("/api/raids/{raidId}/parties")
    public List<PartyDto.PartyView> list(@PathVariable Long raidId) {
        return service.listByRaid(raidId);
    }

    @PostMapping("/api/raids/{raidId}/parties")
    public PartyDto.PartyView create(@PathVariable Long raidId,
                                     @Valid @RequestBody PartyDto.PartyCreateRequest req) {
        return service.create(raidId, req);
    }

    @PutMapping("/api/parties/{partyId}")
    public PartyDto.PartyView update(@PathVariable Long partyId,
                                     @Valid @RequestBody PartyDto.PartyUpdateRequest req) {
        return service.update(partyId, req);
    }

    @DeleteMapping("/api/parties/{partyId}")
    public ResponseEntity<Void> delete(@PathVariable Long partyId) {
        service.delete(partyId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/parties/{partyId}/members")
    public PartyDto.PartyView replaceMembers(@PathVariable Long partyId,
                                             @Valid @RequestBody PartyDto.MembersReplaceRequest req) {
        return service.replaceMembers(partyId, req);
    }
}
