package com.example.kpi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Evidence submission payload for KPI evidence entries.
 */
@Data
public class EvidenceCreateRequest {
    @NotBlank(message = "title is required")
    private String title;

    private String description;

    @NotBlank(message = "contentUrl is required")
    private String contentUrl;

    private String evidenceType = "URL";
    private String fileType = "URL";
    private Long fileSizeBytes = 0L;
}

