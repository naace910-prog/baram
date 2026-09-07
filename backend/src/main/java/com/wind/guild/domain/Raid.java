package com.wind.guild.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "raids")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Raid {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id")
    private RaidTarget target;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RaidCategory category;

    @Column
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RaidStatus status;

    @Column(length = 500)
    private String memo;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private Long discordMessageId;

    @Column
    private boolean pre30Sent;

    @Column(nullable = false)
    private boolean partyFreshSent;

    @Column(nullable = false)
    private boolean lootFreshSent;

    @Column(nullable = false)
    private boolean distFreshSent;

    /** 완료(DONE) 카드를 새 메시지로 1회 발송했는지. edit 만 하면 채팅 위로 묻혀 안 보임. */
    @Column(nullable = false)
    private boolean doneFreshSent;

    @Column(nullable = false)
    private boolean staleDistAlerted;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = RaidStatus.PLANNED;
    }
}
