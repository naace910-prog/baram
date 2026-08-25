package com.wind.guild.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "raid_parties")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class RaidParty {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raid_id", nullable = false)
    private Long raidId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChannelType channelType;

    @Column
    private Integer channelNumber;

    @Column(length = 200)
    private String memo;

    @Column(name = "mike_member_id")
    private Long mikeMemberId;

    @Column(length = 40)
    private String mikeFreeName;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
