package com.wind.guild.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "raid_votes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"raid_id", "member_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class RaidVote {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raid_id", nullable = false)
    private Long raidId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VoteType vote;

    @Column(nullable = false)
    private LocalDateTime votedAt;

    @PrePersist @PreUpdate
    void stamp() {
        votedAt = LocalDateTime.now();
    }
}
