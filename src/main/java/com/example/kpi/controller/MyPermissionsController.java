package com.example.kpi.controller;

import com.example.kpi.dto.MyPermissionsDto;
import com.example.kpi.entity.Role;
import com.example.kpi.entity.User;
import com.example.kpi.service.AccessControlService;
import com.example.kpi.service.KpiReviewWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Exposes the caller's effective capabilities so the UI never has to guess from
 * role names.
 */
@RestController
@RequiredArgsConstructor
public class MyPermissionsController {

    private final KpiReviewWorkflowService workflowService;
    private final AccessControlService accessControlService;

    @GetMapping("/api/me/permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MyPermissionsDto> myPermissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User me = workflowService.findAuthenticatedUserByEmail(auth != null ? auth.getName() : null);

        List<String> roles = me.getRoles() == null ? List.of() : me.getRoles().stream()
                .filter(Objects::nonNull)
                .map(this::roleCode)
                .filter(code -> !code.isBlank())
                .distinct()
                .sorted()
                .toList();

        AccessControlService.ReviewScope scope = accessControlService.resolveScope(me);
        boolean admin = accessControlService.isAdmin(me);

        Set<Long> squadIds = accessControlService.accessibleSquadIds(me);
        Set<Long> userIds = accessControlService.accessibleUserIds(me);

        // Anyone whose scope reaches beyond themselves can review, so a Tech Focal
        // or Portfolio Lead is included without being named anywhere.
        boolean canReview = admin || scope != AccessControlService.ReviewScope.SELF;
        boolean canManageScorecards = auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> Set.of("ROLE_ADMIN", "ROLE_MANAGER")
                        .contains(authority.getAuthority()));

        MyPermissionsDto dto = new MyPermissionsDto(
                me.getId(),
                me.getEmail(),
                me.getFullName(),
                roles,
                scope.name(),
                admin,
                canReview,
                canReview,
                canManageScorecards,
                admin,
                squadIds,
                userIds.size()
        );
        return ResponseEntity.ok(dto);
    }

    private String roleCode(Role role) {
        String raw = role.getRoleCode() != null && !role.getRoleCode().isBlank()
                ? role.getRoleCode()
                : role.getRoleName();
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }
}
