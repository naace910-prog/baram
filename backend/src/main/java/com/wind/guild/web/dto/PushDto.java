package com.wind.guild.web.dto;

import jakarta.validation.constraints.NotBlank;

public class PushDto {

    public record SubscribeRequest(
            @NotBlank String endpoint,
            @NotBlank String p256dh,
            @NotBlank String auth
    ) {}

    public record UnsubscribeRequest(@NotBlank String endpoint) {}
}
