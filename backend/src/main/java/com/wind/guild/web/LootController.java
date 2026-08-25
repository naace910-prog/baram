package com.wind.guild.web;

import com.wind.guild.repository.LootShareRepository;
import com.wind.guild.service.DiscordNotifier;
import com.wind.guild.service.LootService;
import com.wind.guild.service.WebPushService;
import com.wind.guild.web.dto.LootDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.DecimalFormat;
import java.util.List;

@RestController
@RequestMapping("/api/raids/{raidId}/loots")
@RequiredArgsConstructor
public class LootController {

    private final LootService lootService;
    private final DiscordNotifier discord;
    private final WebPushService push;
    private final LootShareRepository shareRepo;
    private static final DecimalFormat MONEY = new DecimalFormat("#,###");

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
        discord.syncLootCard(lootId, DiscordNotifier.LootTrigger.DISTRIBUTED);
        discord.syncRaidCard(raidId, DiscordNotifier.RaidTrigger.VOTE);
        return lootService.listByRaid(raidId);
    }

    @PostMapping("/{lootId}/shares/{shareId}/paid")
    public List<LootDto.LootView> markPaid(@PathVariable Long raidId,
                                           @PathVariable Long lootId,
                                           @PathVariable Long shareId,
                                           @RequestBody LootDto.MarkPaidRequest req) {
        lootService.markPaid(shareId, req.paid());
        discord.syncLootCard(lootId, DiscordNotifier.LootTrigger.PAID_CHANGED);
        discord.syncRaidCard(raidId, DiscordNotifier.RaidTrigger.VOTE);
        // 정산 완료 됐으면 본인에게 push
        if (req.paid()) {
            shareRepo.findById(shareId).ifPresent(s -> push.sendToMember(
                    s.getMemberId(),
                    "💰 정산 완료",
                    MONEY.format(s.getShare()) + "전 정산되었습니다",
                    "/raids/" + raidId));
        }
        return lootService.listByRaid(raidId);
    }
}
