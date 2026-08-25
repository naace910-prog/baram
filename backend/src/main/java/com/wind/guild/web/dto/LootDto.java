package com.wind.guild.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class LootDto {

    public record UpsertLootRequest(
            @NotBlank String itemName,
            boolean dropped,
            Long soldPrice,
            String memo) {}

    public record LootView(
            Long id,
            Long raidId,
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
            LocalDateTime paidAt) {}

    public record DistributeRequest(@NotNull List<Long> memberIds) {}

    public record MarkPaidRequest(@NotNull Long shareId, boolean paid) {}
}
