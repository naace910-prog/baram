package com.wind.guild.web.dto;

import com.wind.guild.domain.RaidCategory;
import com.wind.guild.domain.RaidStatus;
import com.wind.guild.domain.VoteType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class RaidDto {

    public record CreateRequest(
            RaidCategory category,
            Long targetId,
            LocalDateTime scheduledAt,  // nullable: 시간 미정 raid 지원
            String memo) {}

    public record UpdateRequest(
            RaidCategory category,
            Long targetId,
            LocalDateTime scheduledAt,  // nullable
            @NotNull RaidStatus status,
            String memo) {}

    public record VoteRequest(@NotNull VoteType vote) {}

    public record VoteView(Long memberId, String nickname, VoteType vote, LocalDateTime votedAt) {}

    public record AttendeeRequest(@NotNull List<Long> memberIds) {}

    public record ListView(
            Long id,
            RaidCategory category,
            Long targetId,
            String targetName,
            String targetIcon,
            String dropItemName,
            LocalDateTime scheduledAt,
            RaidStatus status,
            String memo,
            int yesCount,
            int noCount,
            int maybeCount,
            List<VoteView> votes,
            List<Long> attendees) {}

    public record DetailView(
            Long id,
            RaidCategory category,
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
