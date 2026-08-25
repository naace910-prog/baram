package com.wind.guild.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages",
        indexes = {
                @Index(name = "idx_chat_created", columnList = "createdAt DESC"),
                @Index(name = "idx_chat_discord_msg", columnList = "discordMessageId")
        })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ChatMessage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column
    private Long authorMemberId;

    @Column(length = 60)
    private String authorDiscordId;

    @Column(nullable = false, length = 60)
    private String authorNickname;

    @Column(nullable = false)
    private boolean authorStarred;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatOrigin origin;

    @Column
    private Long discordMessageId;

    @Column(length = 30)
    private String actionType;

    @Column
    private Long actionRefId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
