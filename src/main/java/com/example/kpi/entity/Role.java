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
 * Role entity representing user roles in the system.
 * Supports multiple roles per user and role-based authorization.
 */
@Entity
@Table(name = "roles", uniqueConstraints = {
        @UniqueConstraint(columnNames = "role_name"),
        @UniqueConstraint(columnNames = "role_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Role name cannot be blank")
    @Column(name = "role_name", nullable = false, unique = true)
    private String roleName;

    @Column(name = "role_code", unique = true)
    private String roleCode;

    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_role_id")
    private Role parentRole;

    @OneToMany(mappedBy = "parentRole", fetch = FetchType.LAZY)
    private Set<Role> childRoles = new HashSet<>();

    @ManyToMany(mappedBy = "roles", fetch = jakarta.persistence.FetchType.LAZY)
    private Set<User> users = new HashSet<>();

    @Override
    public String toString() {
        return "Role{" +
                "id=" + id +
                ", roleName='" + roleName + '\'' +
                ", roleCode='" + roleCode + '\'' +
                ", description='" + description + '\'' +
                ", active=" + active +
                '}';
    }
}

