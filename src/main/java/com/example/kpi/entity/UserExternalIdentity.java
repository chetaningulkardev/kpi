package com.example.kpi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A person's identity in an external system.
 *
 * <p>Nobody's GitHub login reliably matches their corporate email, so a metric
 * cannot be attributed without an explicit mapping. Kept as its own table
 * rather than a column on {@code users} so a second provider needs no schema
 * change, and so an unmapped user is distinguishable from one mapped to
 * nothing.</p>
 */
@Entity
@Table(
        name = "user_external_identities",
        uniqueConstraints = {
                // One handle per provider per person: two would make the
                // collected number silently depend on row order.
                @UniqueConstraint(
                        name = "uk_user_external_identity",
                        columnNames = {"user_id", "provider_code"})
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserExternalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Matches {@link com.example.kpi.metrics.MetricProvider#providerCode()}. */
    @Column(name = "provider_code", nullable = false, length = 50)
    private String providerCode;

    /** The login, username or account id in that system. */
    @Column(name = "external_handle", nullable = false)
    private String externalHandle;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

