package com.wind.guild.web.dto;

import com.wind.guild.domain.MemberRole;
import jakarta.validation.constraints.NotBlank;

public class AuthDto {

    public record LoginRequest(
            @NotBlank String account,
            @NotBlank String password) {}

    public record LoginResponse(
            Long memberId,
            String account,
            String nickname,
            MemberRole role) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank String newPassword) {}

    public record ChangeNicknameRequest(@NotBlank String nickname) {}
}
