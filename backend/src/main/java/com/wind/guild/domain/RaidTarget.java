package com.wind.guild.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "raid_targets")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class RaidTarget {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 100)
    private String dropItemName;

    @Column(length = 8)
    private String icon;

    @Column(length = 400)
    private String memo;
}
