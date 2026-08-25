package com.wind.guild.web.dto;

import com.wind.guild.domain.ChatMessage;
import com.wind.guild.domain.ChatOrigin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class ChatDto {

    public record MessageView(
            Long id,
            String content,
            Long authorMemberId,
            String authorDiscordId,
            String authorNickname,
            ChatOrigin origin,
            LocalDateTime createdAt
    ) {
        public static MessageView of(ChatMessage m) {
            return new MessageView(
                    m.getId(), m.getContent(),
                    m.getAuthorMemberId(), m.getAuthorDiscordId(),
                    m.getAuthorNickname(), m.getOrigin(), m.getCreatedAt());
        }
    }

    public record SendRequest(@NotBlank @Size(max = 2000) String content) {}
}
