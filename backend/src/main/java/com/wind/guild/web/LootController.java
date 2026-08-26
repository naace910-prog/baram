package com.wind.guild.web;

import com.wind.guild.config.SessionKeys;
import com.wind.guild.repository.LootShareRepository;
import com.wind.guild.repository.MemberRepository;
import com.wind.guild.repository.RaidLootRepository;
import com.wind.guild.repository.RaidTargetRepository;
import com.wind.guild.service.ChatService;
import com.wind.guild.service.DiscordNotifier;
import com.wind.guild.service.LootService;
import com.wind.guild.service.WebPushService;
import com.wind.guild.web.dto.LootDto;
import jakarta.servlet.http.HttpSession;
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
    private final RaidLootRepository lootRepo;
    private final MemberRepository memberRepo;
    private final RaidTargetRepository targetRepo;
    private final ChatService chat;
    private static final DecimalFormat MONEY = new DecimalFormat("#,###");

    @GetMapping
    public List<LootDto.LootView> list(@PathVariable Long raidId) {
        return lootService.listByRaid(raidId);
    }

    @PostMapping
    public List<LootDto.LootView> create(@PathVariable Long raidId,
                                         @Valid @RequestBody LootDto.UpsertLootRequest req) {
        lootService.upsert(raidId, null, req);
        discord.syncRaidCardCategoryAware(raidId, DiscordNotifier.RaidTrigger.LOOT);
        try {
            chat.saveSystem("💰 득템 등록 · " + req.itemName()
                    + (req.dropped() ? "" : " (노드랍)")
                    + (req.soldPrice() != null && req.soldPrice() > 0 ? " · " + MONEY.format(req.soldPrice()) + "전" : ""));
        } catch (Exception ignored) {}
        return lootService.listByRaid(raidId);
    }

    @PostMapping("/bulk")
    public List<LootDto.LootView> bulkAdd(@PathVariable Long raidId,
                                          @Valid @RequestBody LootDto.BulkAddRequest req) {
        lootService.bulkAdd(raidId, req);
        discord.syncRaidCardCategoryAware(raidId, DiscordNotifier.RaidTrigger.LOOT);
        try {
            StringBuilder sb = new StringBuilder("💰 드랍 일괄등록 · ");
            long totalPrice = 0;
            int totalQty = 0;
            var parts = new java.util.ArrayList<String>();
            for (var e : req.drops()) {
                String tname = targetRepo.findById(e.targetId())
                        .map(x -> (x.getIcon() != null ? x.getIcon() + " " : "") + x.getName())
                        .orElse("#" + e.targetId());
                parts.add(tname + " " + e.quantity() + "개");
                totalQty += e.quantity();
                if (e.unitPrice() != null && e.unitPrice() > 0) totalPrice += e.unitPrice() * e.quantity();
            }
            sb.append(String.join(", ", parts))
                    .append(" · 총 ").append(totalQty).append("개");
            if (totalPrice > 0) sb.append(" · ").append(MONEY.format(totalPrice)).append("전");
            chat.saveSystem(sb.toString());
        } catch (Exception ignored) {}
        return lootService.listByRaid(raidId);
    }

    @PutMapping("/{lootId}")
    public List<LootDto.LootView> update(@PathVariable Long raidId, @PathVariable Long lootId,
                                         @Valid @RequestBody LootDto.UpsertLootRequest req) {
        lootService.upsert(raidId, lootId, req);
        discord.syncRaidCard(raidId, DiscordNotifier.RaidTrigger.LOOT);
        return lootService.listByRaid(raidId);
    }

    @DeleteMapping("/{lootId}")
    public ResponseEntity<List<LootDto.LootView>> delete(@PathVariable Long raidId, @PathVariable Long lootId) {
        lootService.delete(lootId);
        discord.syncRaidCard(raidId, DiscordNotifier.RaidTrigger.LOOT);
        return ResponseEntity.ok(lootService.listByRaid(raidId));
    }

    @PostMapping("/{lootId}/distribute")
    public List<LootDto.LootView> distribute(@PathVariable Long raidId, @PathVariable Long lootId,
                                             @Valid @RequestBody LootDto.DistributeRequest req,
                                             HttpSession session) {
        Long actorId = (Long) session.getAttribute(SessionKeys.MEMBER_ID);
        lootService.distribute(lootId, req.memberIds(), req.divisor(), actorId);
        discord.syncLootCard(lootId, DiscordNotifier.LootTrigger.DISTRIBUTED);
        // 분배는 EDIT (여러 드랍 분배 시 스팸 방지 · 최초 카드는 유지)
        discord.syncRaidCard(raidId, DiscordNotifier.RaidTrigger.LOOT);
        try {
            var loot = lootRepo.findById(lootId).orElse(null);
            if (loot != null && loot.getSoldPrice() != null) {
                int divisor = (req.divisor() != null && req.divisor() > 0)
                        ? req.divisor() : req.memberIds().size();
                long per = loot.getSoldPrice() / Math.max(1, divisor);
                String extLabel = divisor > req.memberIds().size()
                        ? " (외부인원 " + (divisor - req.memberIds().size()) + "명 포함)" : "";
                chat.saveSystem("💰 " + loot.getItemName() + " " + MONEY.format(loot.getSoldPrice()) + "전 분배"
                        + " · " + divisor + "명" + extLabel + " · 1인 " + MONEY.format(per) + "전");
            }
        } catch (Exception ignored) {}
        return lootService.listByRaid(raidId);
    }

    @PutMapping("/{lootId}/shares/{shareId}")
    public List<LootDto.LootView> updateShareAmount(@PathVariable Long raidId,
                                                    @PathVariable Long lootId,
                                                    @PathVariable Long shareId,
                                                    @Valid @RequestBody LootDto.UpdateShareAmountRequest req) {
        lootService.updateShareAmount(shareId, req.amount());
        discord.syncLootCard(lootId, DiscordNotifier.LootTrigger.PAID_CHANGED);
        return lootService.listByRaid(raidId);
    }

    @PostMapping("/{lootId}/shares/{shareId}/paid")
    public List<LootDto.LootView> markPaid(@PathVariable Long raidId,
                                           @PathVariable Long lootId,
                                           @PathVariable Long shareId,
                                           @RequestBody LootDto.MarkPaidRequest req,
                                           HttpSession session) {
        String role = (String) session.getAttribute(SessionKeys.MEMBER_ROLE);
        if (!"MASTER".equals(role) && !"VICE".equals(role)) {
            throw new IllegalStateException("문주/부문주만 지급 처리 가능합니다");
        }
        Long actorId = (Long) session.getAttribute(SessionKeys.MEMBER_ID);
        lootService.markPaid(shareId, req.paid(), actorId);
        discord.syncLootCard(lootId, DiscordNotifier.LootTrigger.PAID_CHANGED);
        // 지급 토글은 사람별로 자주 발생 → EDIT (스팸 방지)
        discord.syncRaidCard(raidId, DiscordNotifier.RaidTrigger.LOOT);
        if (req.paid()) {
            shareRepo.findById(shareId).ifPresent(s -> {
                push.sendToMember(s.getMemberId(),
                        "💰 정산 완료",
                        MONEY.format(s.getShare()) + "전 정산되었습니다",
                        "/raids/" + raidId);
                String nick = memberRepo.findById(s.getMemberId()).map(m -> m.getNickname()).orElse("?");
                chat.saveSystem("💵 정산 완료: " + nick + " · " + MONEY.format(s.getShare()) + "전");
            });
        }
        return lootService.listByRaid(raidId);
    }
}
