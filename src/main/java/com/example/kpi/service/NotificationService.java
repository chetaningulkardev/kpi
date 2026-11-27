package com.example.kpi.service;

import com.example.kpi.entity.Notification;
import com.example.kpi.entity.User;
import com.example.kpi.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailNotificationService emailService;

    public Notification create(User recipient, String title, String message, String type, String entityType, Long entityId) {
        if (recipient == null || recipient.getId() == null) {
            return null;
        }
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setTitle(title == null ? "Notification" : title.trim());
        notification.setMessage(message == null ? "" : message.trim());
        notification.setType(type == null ? "INFO" : type.trim().toUpperCase());
        notification.setEntityType(entityType == null ? null : entityType.trim());
        notification.setEntityId(entityId);
        notification.setRead(false);
        notification.setReadAt(null);
        Notification saved = notificationRepository.save(notification);

        // Best-effort mirror to email; delivery runs off-thread and never throws,
        // so an SMTP outage cannot roll back the in-app notification.
        if (emailService != null) {
            emailService.sendAsync(saved);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unused")
    public List<Notification> getRecentForUser(Long userId) {
        return searchForUser(userId, null, null, null, null, 0, 50, "newest").getContent();
    }

    @Transactional(readOnly = true)
    public Page<Notification> searchForUser(
            Long userId,
            Boolean read,
            String type,
            LocalDateTime fromAt,
            LocalDateTime toAt,
            int page,
            int size,
            String sortBy
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, resolveSort(sortBy));
        return notificationRepository.searchByRecipient(userId, read, type, fromAt, toAt, pageable);
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableTypes(Long userId) {
        return notificationRepository.findDistinctTypesByRecipientId(userId);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unused")
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @SuppressWarnings("unused")
    public void markRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new SecurityException("Not allowed to mark this notification");
        }
        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    @SuppressWarnings("unused")
    public int markAllRead(Long userId) {
        List<Notification> notifications = notificationRepository.findTop50ByRecipientIdOrderByCreatedDateDesc(userId);
        int changed = 0;
        for (Notification n : notifications) {
            if (!Boolean.TRUE.equals(n.getRead())) {
                n.setRead(true);
                n.setReadAt(LocalDateTime.now());
                changed++;
            }
        }
        if (changed > 0) {
            notificationRepository.saveAll(notifications);
        }
        return changed;
    }

    private Sort resolveSort(String sortBy) {
        String key = sortBy == null ? "newest" : sortBy.trim().toLowerCase();
        return switch (key) {
            case "oldest" -> Sort.by(Sort.Order.asc("createdDate"));
            case "type" -> Sort.by(Sort.Order.asc("type"), Sort.Order.desc("createdDate"));
            default -> Sort.by(Sort.Order.desc("createdDate"));
        };
    }
}

