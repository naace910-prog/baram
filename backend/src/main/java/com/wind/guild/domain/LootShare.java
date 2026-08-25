package com.wind.guild.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "loot_shares",
        uniqueConstraints = @UniqueConstraint(columnNames = {"loot_id", "member_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class LootShare {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loot_id", nullable = false)
    private Long lootId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long share;

    @Column(nullable = false)
    private boolean paid;

    @Column
    private LocalDateTime paidAt;

    @Column(name = "paid_by")
    private Long paidBy;

    @Column(nullable = false)
    private boolean received;

    @Column
    private LocalDateTime receivedAt;
}
