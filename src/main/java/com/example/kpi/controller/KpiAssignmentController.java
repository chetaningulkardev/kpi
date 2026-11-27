package com.example.kpi.controller;

import com.example.kpi.entity.*;
import com.example.kpi.repository.*;
import com.example.kpi.service.KpiAssignmentService;
import com.example.kpi.service.KpiGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
@Slf4j
public class KpiAssignmentController {

    private final KpiAssignmentService assignmentService;
    private final RoleRepository roleRepository;
    private final KpiTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final KPIRepository kpiRepository;

    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> generateAssignments(@RequestParam String cycleCode) {
        log.info("Generating assignments for cycle: {}", cycleCode);
        try {
            KpiGenerationService.GenerationSummary summary = assignmentService.generateAssignments(cycleCode);
            String status = summary.hasFailures() ? "PARTIAL_SUCCESS" : "SUCCESS";
            return ResponseEntity.ok(Map.of(
                    "cycleCode", cycleCode,
                    "createdAssignments", summary.getCreatedKpis(),
                    "processedUsers", summary.getProcessedUsers(),
                    "skippedUsers", summary.getSkippedUsers(),
                    "failedUsers", summary.getFailedUsers(),
                    "status", status
            ));
        } catch (Exception e) {
            log.error("Error generating assignments", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "cycleCode", cycleCode,
                    "error", e.getMessage() != null ? e.getMessage() : "Unknown error",
                    "status", "FAILED"
            ));
        }
    }

    @PostMapping("/generate-by-role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> generateAssignmentsByRole(
            @RequestParam String roleCode,
            @RequestParam String cycleCode
    ) {
        log.info("Generating assignments for role: {} in cycle: {}", roleCode, cycleCode);
        try {
            KpiGenerationService.GenerationSummary summary = assignmentService.generateAssignmentsForRole(roleCode, cycleCode);
            String status = summary.hasFailures() ? "PARTIAL_SUCCESS" : "SUCCESS";
            return ResponseEntity.ok(Map.of(
                    "roleCode", roleCode,
                    "cycleCode", cycleCode,
                    "createdAssignments", summary.getCreatedKpis(),
                    "processedUsers", summary.getProcessedUsers(),
                    "skippedUsers", summary.getSkippedUsers(),
                    "failedUsers", summary.getFailedUsers(),
                    "status", status
            ));
        } catch (Exception e) {
            log.error("Error generating assignments for role", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "roleCode", roleCode,
                    "cycleCode", cycleCode,
                    "error", e.getMessage() != null ? e.getMessage() : "Unknown error",
                    "status", "FAILED"
            ));
        }
    }

    @GetMapping("/readiness-by-role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRoleReadiness(
            @RequestParam String roleCode,
            @RequestParam String cycleCode
    ) {
        try {
            KpiAssignmentService.RoleGenerationReadiness readiness =
                    assignmentService.evaluateRoleGenerationReadiness(roleCode, cycleCode);

            List<Map<String, Object>> templateChecks = readiness.getTemplates().stream()
                    .map(t -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("templateId", t.getTemplateId());
                        m.put("templateCode", t.getTemplateCode());
                        m.put("templateName", t.getTemplateName());
                        m.put("activeItemCount", t.getActiveItemCount());
                        m.put("activeWeight", t.getActiveWeight());
                        m.put("eligible", t.isEligible());
                        m.put("reasons", t.getReasons());
                        return m;
                    })
                    .toList();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("roleCode", readiness.getRoleCode());
            response.put("cycleCode", readiness.getCycleCode());
            response.put("ready", readiness.isReady());
            response.put("cycleEligible", readiness.isCycleEligible());
            response.put("activeUsersInRole", readiness.getActiveUsersInRole());
            response.put("reasons", readiness.getReasons());
            response.put("templates", templateChecks);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error checking readiness for role {} in cycle {}", roleCode, cycleCode, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "roleCode", roleCode,
                    "cycleCode", cycleCode,
                    "ready", false,
                    "status", "FAILED",
                    "error", e.getMessage() != null ? e.getMessage() : "Unknown error"
            ));
        }
    }

    /**
     * Stats endpoint — overview of roles with templates, user counts, and cycle KPI counts.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<Role> allRoles = roleRepository.findAll();
        List<User> activeUsers = userRepository.findByActiveTrue();
        List<ReviewCycle> cycles = reviewCycleRepository.findAll();

        // Role stats
        List<Map<String, Object>> roleStats = allRoles.stream()
                .map(role -> {
                    List<KpiTemplate> allTemplates = templateRepository.findByRole(role);
                    long activeTemplates = allTemplates.stream()
                            .filter(t -> t.getStatus() == KpiTemplate.TemplateStatus.ACTIVE && Boolean.TRUE.equals(t.getActive()))
                            .count();
                    long userCount = activeUsers.stream()
                            .filter(u -> u.getRoles() != null && u.getRoles().stream()
                                    .anyMatch(r -> r.getId().equals(role.getId())))
                            .count();
                    List<String> templateNames = allTemplates.stream()
                            .filter(t -> Boolean.TRUE.equals(t.getActive()))
                            .map(KpiTemplate::getTemplateName)
                            .collect(Collectors.toList());

                    Map<String, Object> stat = new LinkedHashMap<>();
                    stat.put("roleId", role.getId());
                    stat.put("roleCode", role.getRoleCode());
                    stat.put("roleName", role.getRoleName());
                    stat.put("userCount", userCount);
                    stat.put("totalTemplates", allTemplates.size());
                    stat.put("activeTemplates", activeTemplates);
                    stat.put("readyToGenerate", activeTemplates > 0 && userCount > 0);
                    stat.put("templateNames", templateNames);
                    return stat;
                })
                .collect(Collectors.toList());

        // Cycle stats
        List<Map<String, Object>> cycleStats = cycles.stream()
                .map(cycle -> {
                    long kpiCount = kpiRepository.findByReviewCycle(cycle).size();
                    Map<String, Object> stat = new LinkedHashMap<>();
                    stat.put("cycleId", cycle.getId());
                    stat.put("cycleCode", cycle.getCycleCode());
                    stat.put("cycleName", cycle.getCycleName());
                    stat.put("status", cycle.getStatus());
                    stat.put("kpisGenerated", cycle.getKpisGenerated());
                    stat.put("kpiCount", kpiCount);
                    stat.put("startDate", cycle.getStartDate());
                    stat.put("endDate", cycle.getEndDate());
                    return stat;
                })
                .collect(Collectors.toList());

        long totalActiveUsers = activeUsers.size();
        long rolesWithTemplate = roleStats.stream()
                .filter(s -> (long) s.get("activeTemplates") > 0)
                .count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalActiveUsers", totalActiveUsers);
        response.put("totalRoles", allRoles.size());
        response.put("rolesWithActiveTemplate", rolesWithTemplate);
        response.put("roleStats", roleStats);
        response.put("cycleStats", cycleStats);
        return ResponseEntity.ok(response);
    }

    /**
     * Preview — which users will get KPIs generated for a given cycle (before actually generating).
     */
    @GetMapping("/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> previewGeneration(@RequestParam String cycleCode) {
        try {
            ReviewCycle cycle = reviewCycleRepository.findByCycleCode(cycleCode)
                    .orElseThrow(() -> new IllegalArgumentException("Cycle not found: " + cycleCode));

            List<User> activeUsers = userRepository.findByActiveTrue();
            List<Map<String, Object>> userPreviews = new ArrayList<>();

            for (User user : activeUsers) {
                Set<Role> userRoles = user.getRoles();
                if (userRoles == null || userRoles.isEmpty()) continue;

                List<String> roleCodes = userRoles.stream()
                        .filter(Objects::nonNull)
                        .map(Role::getRoleCode)
                        .collect(Collectors.toList());

                // Check if any role has an active template
                boolean hasTemplate = userRoles.stream()
                        .filter(Objects::nonNull)
                        .anyMatch(role -> {
                            List<KpiTemplate> templates = templateRepository.findByRoleAndStatusAndActiveTrue(
                                    role, KpiTemplate.TemplateStatus.ACTIVE);
                            return !templates.isEmpty();
                        });

                long existingKpis = kpiRepository.countByReviewCycleAndUser(cycle, user);

                Map<String, Object> preview = new LinkedHashMap<>();
                preview.put("userId", user.getId());
                preview.put("name", user.getFirstName() + " " + user.getLastName());
                preview.put("email", user.getEmail());
                preview.put("roles", roleCodes);
                preview.put("hasTemplate", hasTemplate);
                preview.put("existingKpiCount", existingKpis);
                preview.put("willGenerate", hasTemplate && existingKpis == 0);
                userPreviews.add(preview);
            }

            long willGenerate = userPreviews.stream().filter(p -> (boolean) p.get("willGenerate")).count();
            long alreadyGenerated = userPreviews.stream().filter(p -> (long) p.get("existingKpiCount") > 0).count();
            long noTemplate = userPreviews.stream().filter(p -> !(boolean) p.get("hasTemplate")).count();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("cycleCode", cycleCode);
            response.put("cycleName", cycle.getCycleName());
            response.put("cycleStatus", cycle.getStatus());
            response.put("totalUsers", userPreviews.size());
            response.put("willGenerate", willGenerate);
            response.put("alreadyGenerated", alreadyGenerated);
            response.put("noTemplate", noTemplate);
            response.put("users", userPreviews);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error previewing generation for cycle: {}", cycleCode, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error"));
        }
    }
}
