package com.example.kpi.dto;

import com.example.kpi.entity.KpiEvidence;

import java.time.LocalDateTime;

/**
 * Flat evidence response DTO to prevent recursive JSON from JPA entities.
 */
public record KpiEvidenceViewDto(
        Long id,
        String title,
        String description,
        String contentUrl,
        String evidenceType,
        String fileType,
        Long fileSizeBytes,
        LocalDateTime uploadedAt,
        UserRef uploadedBy
) {
    public record UserRef(Long id, String firstName, String lastName, String email) {}

    public static KpiEvidenceViewDto from(KpiEvidence evidence) {
        if (evidence == null) {
            return null;
        }
        UserRef uploader = evidence.getUploadedBy() == null
                ? null
                : new UserRef(
                        evidence.getUploadedBy().getId(),
                        evidence.getUploadedBy().getFirstName(),
                        evidence.getUploadedBy().getLastName(),
                        evidence.getUploadedBy().getEmail()
                );

        return new KpiEvidenceViewDto(
                evidence.getId(),
                evidence.getTitle(),
                evidence.getDescription(),
                evidence.getContentUrl(),
                evidence.getEvidenceType(),
                evidence.getFileType(),
                evidence.getFileSizeBytes(),
                evidence.getUploadedAt(),
                uploader
        );
    }
}

