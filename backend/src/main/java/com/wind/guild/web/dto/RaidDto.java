package com.wind.guild.web.dto;

import com.wind.guild.domain.RaidStatus;
import com.wind.guild.domain.VoteType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class RaidDto {

    public record CreateRequest(
            @NotNull Long targetId,
            @NotNull LocalDateTime scheduledAt,
            String memo) {}

    public record UpdateRequest(
            @NotNull Long targetId,
            @NotNull LocalDateTime scheduledAt,
            @NotNull RaidStatus status,
            String memo) {}

    public record VoteRequest(@NotNull VoteType vote) {}

    public record VoteView(Long memberId, String nickname, VoteType vote, LocalDateTime votedAt) {}

    public record AttendeeRequest(@NotNull List<Long> memberIds) {}

    public record ListView(
            Long id,
            Long targetId,
            String targetName,
            String targetIcon,
            String dropItemName,
            LocalDateTime scheduledAt,
            RaidStatus status,
            String memo,
            int yesCount,
            int noCount,
            int maybeCount) {}

    public record DetailView(
            Long id,
            Long targetId,
            String targetName,
            String targetIcon,
            String dropItemName,
            LocalDateTime scheduledAt,
            RaidStatus status,
            String memo,
            List<VoteView> votes,
            List<Long> attendees) {}
}
