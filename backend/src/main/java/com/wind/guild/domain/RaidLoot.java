package com.wind.guild.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "raid_loots")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class RaidLoot {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raid_id", nullable = false)
    private Long raidId;

    @Column(nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false)
    private boolean dropped;

    @Column
    private Long soldPrice;

    @Column
    private LocalDateTime soldAt;

    @Column(length = 300)
    private String memo;
}
