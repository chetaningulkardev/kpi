package com.example.kpi.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Tribe is the top-level organizational unit.
 * Tribe -> Portfolio -> Squad -> Members.
 */
@Entity
@Table(name = "tribes", uniqueConstraints = {
        @UniqueConstraint(columnNames = "tribe_name"),
        @UniqueConstraint(columnNames = "tribe_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Tribe extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Tribe name cannot be blank")
    @Column(name = "tribe_name", nullable = false, unique = true)
    private String tribeName;

    @NotBlank(message = "Tribe code cannot be blank")
    @Column(name = "tribe_code", nullable = false, unique = true)
    private String tribeCode;

    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "tribe_leads",
            joinColumns = @JoinColumn(name = "tribe_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> leads = new HashSet<>();

    @OneToMany(mappedBy = "tribe", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Portfolio> portfolios = new HashSet<>();

    @OneToMany(mappedBy = "tribe", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserTribeAssignment> tribeAssignments = new HashSet<>();
}

