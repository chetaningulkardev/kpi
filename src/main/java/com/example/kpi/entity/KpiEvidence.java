package com.example.kpi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * KPI Evidence - Attachments and links provided as evidence for KPI achievement
 */
@Entity
@Table(name = "kpi_evidences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiEvidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kpi_id", nullable = false)
    private Kpi kpi;

    @Column(nullable = false)
    private String evidenceType;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String contentUrl;

    @Column(nullable = false)
    private String fileType;

    @Column(nullable = false)
    private Long fileSizeBytes;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private User uploadedBy;

    public enum EvidenceType {
        PDF, EXCEL, WORD, IMAGE, URL, CONFLUENCE, JIRA, OTHER
    }
}

