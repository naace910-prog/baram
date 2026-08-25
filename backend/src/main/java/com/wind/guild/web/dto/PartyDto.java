package com.wind.guild.web.dto;

import com.wind.guild.domain.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class PartyDto {

    public record RoleView(Long id, String name, String icon, int displayOrder, boolean active) {}
    public record RoleUpsertRequest(
            @NotBlank String name, String icon, Integer displayOrder, Boolean active) {}

    public record MemberView(
            Long id, String role, Long memberId, String freeName, String nickname, int displayOrder) {}

    public record PartyView(
            Long id, Long raidId, ChannelType channelType, Integer channelNumber, String memo,
            Long mikeMemberId, String mikeFreeName, String mikeNickname,
            int displayOrder, List<MemberView> members) {}

    public record PartyCreateRequest(
            @NotNull ChannelType channelType, Integer channelNumber, String memo,
            Long mikeMemberId, String mikeFreeName) {}

    public record PartyUpdateRequest(
            @NotNull ChannelType channelType, Integer channelNumber, String memo,
            Long mikeMemberId, String mikeFreeName, Integer displayOrder) {}

    public record MemberEntry(
            @NotBlank String role, Long memberId, String freeName) {}

    public record MembersReplaceRequest(@NotNull List<MemberEntry> members) {}

    public record AutoAssignResult(
            String basis,
            Long previousRaidId,
            LocalDateTime previousScheduledAt,
            int carriedParties,
            int assignedMembers,
            int newcomerCount,
            int droppedFromPrev,
            List<PartyView> parties) {}
}
