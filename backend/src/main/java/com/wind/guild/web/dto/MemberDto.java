package com.wind.guild.web.dto;

import com.wind.guild.domain.Member;
import com.wind.guild.domain.MemberRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class MemberDto {

    public record CreateRequest(
            @NotBlank String account,
            @NotBlank String password,
            @NotBlank String nickname,
            @NotNull MemberRole role,
            String discordUserId) {}

    public record UpdateRequest(
            @NotBlank String nickname,
            @NotNull MemberRole role,
            String discordUserId,
            Boolean active) {}

    public record View(
            Long id,
            String account,
            String nickname,
            MemberRole role,
            String discordUserId,
            boolean active,
            boolean starred,
            LocalDateTime joinedAt) {
        public static View of(Member m) {
            return new View(m.getId(), m.getAccount(), m.getNickname(),
                    m.getRole(), m.getDiscordUserId(), m.isActive(), m.isStarred(), m.getJoinedAt());
        }
    }
}
