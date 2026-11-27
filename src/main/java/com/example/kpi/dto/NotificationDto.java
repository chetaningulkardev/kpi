package com.example.kpi.dto;

import com.example.kpi.entity.Notification;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private String title;
    private String message;
    private String type;
    private String entityType;
    private Long entityId;
    private Boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public static NotificationDto from(Notification n) {
        return new NotificationDto(
                n.getId(),
                n.getTitle(),
                n.getMessage(),
                n.getType(),
                n.getEntityType(),
                n.getEntityId(),
                Boolean.TRUE.equals(n.getRead()),
                n.getReadAt(),
                n.getCreatedDate()
        );
    }
}

