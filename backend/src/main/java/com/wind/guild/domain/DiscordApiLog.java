package com.wind.guild.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "discord_api_logs", indexes = {
        @Index(name = "idx_dcl_created", columnList = "created_at DESC"),
        @Index(name = "idx_dcl_success", columnList = "success")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class DiscordApiLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** send / edit / delete / other */
    @Column(nullable = false, length = 20)
    private String op;

    /** raidCard / raidCardFresh / raidPre30 / lootCard / alert / raidDelete / lootDelete 등 */
    @Column(nullable = false, length = 40)
    private String kind;

    /** raid id 또는 loot id */
    @Column(name = "ref_id")
    private Long refId;

    /** 트리거 (CREATED/STATUS/…) 또는 null */
    @Column(length = 30)
    private String trigger;

    /** 발송된 discord message id (성공 시) */
    @Column(name = "discord_message_id")
    private Long discordMessageId;

    @Column(nullable = false)
    private boolean success;

    /** 실패 시 err.toString 앞부분 */
    @Column(length = 500)
    private String error;

    /** 걸린 밀리초 (send 요청 → 콜백까지) */
    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
