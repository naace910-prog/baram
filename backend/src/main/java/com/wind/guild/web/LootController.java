package com.wind.guild.web;

import com.wind.guild.service.LootService;
import com.wind.guild.web.dto.LootDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/raids/{raidId}/loots")
@RequiredArgsConstructor
public class LootController {

    private final LootService lootService;

    @GetMapping
    public List<LootDto.LootView> list(@PathVariable Long raidId) {
        return lootService.listByRaid(raidId);
    }

    @PostMapping
    public List<LootDto.LootView> create(@PathVariable Long raidId,
                                         @Valid @RequestBody LootDto.UpsertLootRequest req) {
        lootService.upsert(raidId, null, req);
        return lootService.listByRaid(raidId);
    }

    @PutMapping("/{lootId}")
    public List<LootDto.LootView> update(@PathVariable Long raidId, @PathVariable Long lootId,
                                         @Valid @RequestBody LootDto.UpsertLootRequest req) {
        lootService.upsert(raidId, lootId, req);
        return lootService.listByRaid(raidId);
    }

    @DeleteMapping("/{lootId}")
    public ResponseEntity<List<LootDto.LootView>> delete(@PathVariable Long raidId, @PathVariable Long lootId) {
        lootService.delete(lootId);
        return ResponseEntity.ok(lootService.listByRaid(raidId));
    }

    @PostMapping("/{lootId}/distribute")
    public List<LootDto.LootView> distribute(@PathVariable Long raidId, @PathVariable Long lootId,
                                             @Valid @RequestBody LootDto.DistributeRequest req) {
        lootService.distribute(lootId, req.memberIds());
        return lootService.listByRaid(raidId);
    }

    @PostMapping("/{lootId}/shares/{shareId}/paid")
    public List<LootDto.LootView> markPaid(@PathVariable Long raidId,
                                           @PathVariable Long lootId,
                                           @PathVariable Long shareId,
                                           @RequestBody LootDto.MarkPaidRequest req) {
        lootService.markPaid(shareId, req.paid());
        return lootService.listByRaid(raidId);
    }
}
