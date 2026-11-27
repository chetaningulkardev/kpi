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
 * Squad belongs to a Portfolio and contains team members.
 * Every Squad must have at least one Squad Lead.
 */
@Entity
@Table(name = "squads", uniqueConstraints = {
        @UniqueConstraint(columnNames = "squad_name"),
        @UniqueConstraint(columnNames = "squad_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Squad extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Squad name cannot be blank")
    @Column(name = "squad_name", nullable = false, unique = true)
    private String squadName;

    @NotBlank(message = "Squad code cannot be blank")
    @Column(name = "squad_code", nullable = false, unique = true)
    private String squadCode;

    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "squad_leads",
            joinColumns = @JoinColumn(name = "squad_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> leads = new HashSet<>();

    @OneToMany(mappedBy = "squad", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserSquadAssignment> squadAssignments = new HashSet<>();
}

