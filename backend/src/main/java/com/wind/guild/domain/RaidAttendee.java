package com.wind.guild.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "raid_attendees",
        uniqueConstraints = @UniqueConstraint(columnNames = {"raid_id", "member_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class RaidAttendee {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raid_id", nullable = false)
    private Long raidId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;
}
