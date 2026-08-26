package com.wind.guild.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;
import java.util.List;

public class LootDto {

    public record UpsertLootRequest(
            Long targetId,
            @NotBlank String itemName,
            boolean dropped,
            Long soldPrice,
            String memo) {}

    public record LootView(
            Long id,
            Long raidId,
            Long targetId,
            String targetName,
            String targetIcon,
            String itemName,
            boolean dropped,
            Long soldPrice,
            LocalDateTime soldAt,
            String memo,
            List<ShareView> shares) {}

    public record ShareView(
            Long id,
            Long memberId,
            String nickname,
            Long share,
            boolean paid,
            LocalDateTime paidAt,
            String paidByNickname,
            boolean received,
            LocalDateTime receivedAt) {}

    public record DistributeRequest(@NotNull List<Long> memberIds, Integer divisor) {}

    public record MarkPaidRequest(@NotNull Long shareId, boolean paid) {}

    public record UpdateShareAmountRequest(@NotNull @Min(0) Long amount) {}

    public record BulkDropEntry(
            @NotNull Long targetId,
            @NotNull @Min(1) Integer quantity,
            Long unitPrice) {}

    public record BulkAddRequest(@NotNull List<BulkDropEntry> drops) {}
}
