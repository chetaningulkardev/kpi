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
 * Team entity representing organizational teams.
 * Teams group users and provide organizational structure.
 */
@Entity
@Table(name = "teams", uniqueConstraints = {
        @UniqueConstraint(columnNames = "team_name"),
        @UniqueConstraint(columnNames = "team_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Team extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Team name cannot be blank")
    @Column(name = "team_name", nullable = false, unique = true)
    private String teamName;

    @NotBlank(message = "Team code cannot be blank")
    @Column(name = "team_code", nullable = false, unique = true)
    private String teamCode;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_lead_id")
    private User teamLead;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @OneToMany(mappedBy = "team", fetch = FetchType.LAZY)
    private Set<User> members = new HashSet<>();

    @Override
    public String toString() {
        return "Team{" +
                "id=" + id +
                ", teamName='" + teamName + '\'' +
                ", teamCode='" + teamCode + '\'' +
                ", description='" + description + '\'' +
                ", active=" + active +
                '}';
    }
}

