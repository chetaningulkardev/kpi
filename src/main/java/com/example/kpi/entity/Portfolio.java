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
 * Portfolio belongs to a Tribe and groups multiple Squads.
 */
@Entity
@Table(name = "portfolios", uniqueConstraints = {
        @UniqueConstraint(columnNames = "portfolio_name"),
        @UniqueConstraint(columnNames = "portfolio_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Portfolio extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Portfolio name cannot be blank")
    @Column(name = "portfolio_name", nullable = false, unique = true)
    private String portfolioName;

    @NotBlank(message = "Portfolio code cannot be blank")
    @Column(name = "portfolio_code", nullable = false, unique = true)
    private String portfolioCode;

    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tribe_id")
    private Tribe tribe;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "portfolio_leads",
            joinColumns = @JoinColumn(name = "portfolio_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> leads = new HashSet<>();

    @OneToMany(mappedBy = "portfolio", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Squad> squads = new HashSet<>();

    @OneToMany(mappedBy = "portfolio", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserPortfolioAssignment> portfolioAssignments = new HashSet<>();
}

