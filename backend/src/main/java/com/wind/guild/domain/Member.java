package com.wind.guild.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "members", uniqueConstraints = @UniqueConstraint(columnNames = "account"))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Member {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String account;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 40)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Column(length = 60)
    private String discordUserId;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    void onCreate() {
        if (joinedAt == null) joinedAt = LocalDateTime.now();
    }
}
