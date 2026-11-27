package com.example.kpi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Generic key/value reference data, keyed by {@code lookupType}.
 * <p>
 * Served to the UI by {@code LookupConfigurationController} at
 * {@code /api/config/lookups/{lookupType}}.
 */
@Entity
@Table(
        name = "lookup_value",
        uniqueConstraints = @UniqueConstraint(name = "uk_lookup_type_code", columnNames = {"lookupType", "code"})
)
public class LookupValue extends AuditableEntity {

    @Column(nullable = false)
    private String lookupType;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String label;

    private String description;

    @Column(length = 4000)
    private String metadataJson;

    public String getLookupType() {
        return lookupType;
    }

    public void setLookupType(String lookupType) {
        this.lookupType = lookupType;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }
}

