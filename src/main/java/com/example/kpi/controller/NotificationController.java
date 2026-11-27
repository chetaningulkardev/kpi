package com.example.kpi.controller;

import com.example.kpi.dto.NotificationDto;
import com.example.kpi.entity.User;
import com.example.kpi.service.KpiReviewWorkflowService;
import com.example.kpi.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final KpiReviewWorkflowService workflowService;

    private User currentUser() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String email = auth != null ? auth.getName() : null;
        return workflowService.findAuthenticatedUserByEmail(email);
    }

    @GetMapping("/api/notifications")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getMyNotifications(
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "newest") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        User me = currentUser();
        LocalDateTime fromAt = parseStartOfDay(fromDate);
        LocalDateTime toAt = parseEndOfDay(toDate);

        var result = notificationService.searchForUser(me.getId(), read, normalizeType(type), fromAt, toAt, page, size, sortBy);
        List<NotificationDto> items = result.getContent().stream()
                .map(NotificationDto::from)
                .toList();
        Map<String, Object> payload = new HashMap<>();
        payload.put("items", items);
        payload.put("page", result.getNumber());
        payload.put("size", result.getSize());
        payload.put("totalItems", result.getTotalElements());
        payload.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/api/notifications/types")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getNotificationTypes() {
        User me = currentUser();
        return ResponseEntity.ok(notificationService.getAvailableTypes(me.getId()));
    }

    @GetMapping("/api/notifications/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        User me = currentUser();
        Map<String, Long> payload = new HashMap<>();
        payload.put("unreadCount", notificationService.getUnreadCount(me.getId()));
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/api/notifications/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> markRead(@PathVariable Long id) {
        User me = currentUser();
        notificationService.markRead(id, me.getId());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/api/notifications/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        User me = currentUser();
        int changed = notificationService.markAllRead(me.getId());
        return ResponseEntity.ok(Map.of("updated", changed));
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return type.trim();
    }

    private LocalDateTime parseStartOfDay(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        return LocalDate.parse(date.trim()).atStartOfDay();
    }

    private LocalDateTime parseEndOfDay(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        return LocalDate.parse(date.trim()).atTime(23, 59, 59, 999_000_000);
    }
}

