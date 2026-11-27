package com.example.kpi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Assignment of a User to a Squad (many-to-many with metadata).
 */
@Entity
@Table(name = "user_squad_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSquadAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "squad_id", nullable = false)
    private Squad squad;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "primary_assignment", nullable = false)
    private Boolean primaryAssignment = false;
}

