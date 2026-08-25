package com.wind.guild.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "raid_party_members")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class RaidPartyMember {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(nullable = false, length = 40)
    private String role;

    @Column(name = "member_id")
    private Long memberId;

    @Column(length = 60)
    private String freeName;

    @Column(nullable = false)
    private int displayOrder;
}
