package com.wind.guild.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "party_roles", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class PartyRole {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(length = 8)
    private String icon;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;
}
