package com.example.kpi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.Instant;

/**
 * Audit base carrying an identifier, {@code Instant} timestamps and soft-delete
 * flags, maintained by JPA lifecycle callbacks.
 * <p>
 * Not interchangeable with {@link BaseEntity}, despite the similar purpose.
 * {@code BaseEntity} declares no identifier, uses {@code LocalDateTime}
 * {@code created_date}/{@code updated_date} columns plus {@code created_by}/
 * {@code updated_by}, and is populated by Spring Data's
 * {@code AuditingEntityListener}. This one owns the {@code id} and adds
 * {@code active}/{@code deleted}, which is what {@link LookupValue}'s
 * soft-delete API needs.
 * <p>
 * Only {@code LookupValue} extends this. New entities should extend
 * {@code BaseEntity} unless they specifically need soft deletion.
 */
@MappedSuperclass
public abstract class AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean deleted = false;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}

