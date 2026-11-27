package com.example.kpi.service;

import com.example.kpi.entity.Role;
import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.entity.User;
import com.example.kpi.entity.KpiTemplate;
import com.example.kpi.entity.KpiTemplateItem;
import com.example.kpi.repository.KpiTemplateItemRepository;
import com.example.kpi.repository.KpiTemplateRepository;
import com.example.kpi.repository.RoleRepository;
import com.example.kpi.repository.ReviewCycleRepository;
import com.example.kpi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class KpiAssignmentService {

    private final UserRepository userRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final RoleRepository roleRepository;
    private final KpiTemplateRepository templateRepository;
    private final KpiTemplateItemRepository templateItemRepository;
    private final KpiGenerationService generationService;

    public static final class TemplateReadiness {
        private final Long templateId;
        private final String templateCode;
        private final String templateName;
        private final int activeItemCount;
        private final double activeWeight;
        private final boolean eligible;
        private final List<String> reasons;

        public TemplateReadiness(Long templateId, String templateCode, String templateName,
                                 int activeItemCount, double activeWeight, boolean eligible, List<String> reasons) {
            this.templateId = templateId;
            this.templateCode = templateCode;
            this.templateName = templateName;
            this.activeItemCount = activeItemCount;
            this.activeWeight = activeWeight;
            this.eligible = eligible;
            this.reasons = reasons;
        }

        public Long getTemplateId() { return templateId; }
        public String getTemplateCode() { return templateCode; }
        public String getTemplateName() { return templateName; }
        public int getActiveItemCount() { return activeItemCount; }
        public double getActiveWeight() { return activeWeight; }
        public boolean isEligible() { return eligible; }
        public List<String> getReasons() { return reasons; }
    }

    public static final class RoleGenerationReadiness {
        private final String roleCode;
        private final String cycleCode;
        private final boolean ready;
        private final int activeUsersInRole;
        private final boolean cycleEligible;
        private final List<String> reasons;
        private final List<TemplateReadiness> templates;

        public RoleGenerationReadiness(String roleCode, String cycleCode, boolean ready,
                                       int activeUsersInRole, boolean cycleEligible,
                                       List<String> reasons, List<TemplateReadiness> templates) {
            this.roleCode = roleCode;
            this.cycleCode = cycleCode;
            this.ready = ready;
            this.activeUsersInRole = activeUsersInRole;
            this.cycleEligible = cycleEligible;
            this.reasons = reasons;
            this.templates = templates;
        }

        public String getRoleCode() { return roleCode; }
        public String getCycleCode() { return cycleCode; }
        public boolean isReady() { return ready; }
        public int getActiveUsersInRole() { return activeUsersInRole; }
        public boolean isCycleEligible() { return cycleEligible; }
        public List<String> getReasons() { return reasons; }
        public List<TemplateReadiness> getTemplates() { return templates; }
    }

    public KpiGenerationService.GenerationSummary generateAssignments(String cycleCode) {
        log.info("Generating KPI assignments for cycle: {}", cycleCode);

        if (cycleCode == null || cycleCode.trim().isEmpty()) {
            log.error("Review cycle code cannot be null or empty");
            throw new IllegalArgumentException("Review cycle code cannot be null or empty");
        }

        ReviewCycle cycle = reviewCycleRepository.findByCycleCode(cycleCode)
                .orElseThrow(() -> {
                    log.error("Review cycle not found: {}", cycleCode);
                    return new IllegalArgumentException("Review cycle not found: " + cycleCode);
                });

        // Guardrail: generation is only valid for active cycles.
        if (cycle.getStatus() != ReviewCycle.CycleStatus.OPEN
                && cycle.getStatus() != ReviewCycle.CycleStatus.IN_PROGRESS) {
            throw new IllegalStateException("KPI generation is allowed only for OPEN or IN_PROGRESS review cycles");
        }

        log.info("Found review cycle: {}", cycle.getId());
        KpiGenerationService.GenerationSummary summary = generationService.generateKpisForReviewCycle(cycle.getId());

        log.info("Completed generating KPI assignments for cycle {}. KPIs created: {}, failed users: {}, skipped users: {}",
                cycleCode, summary.getCreatedKpis(), summary.getFailedUsers(), summary.getSkippedUsers());
        return summary;
    }

    public KpiGenerationService.GenerationSummary generateAssignmentsForRole(String roleCode, String cycleCode) {
        log.info("Generating KPI assignments for role: {} in cycle: {}", roleCode, cycleCode);

        if (roleCode == null || roleCode.trim().isEmpty()) {
            log.error("Role code cannot be null or empty");
            throw new IllegalArgumentException("Role code cannot be null or empty");
        }
        if (cycleCode == null || cycleCode.trim().isEmpty()) {
            log.error("Cycle code cannot be null or empty");
            throw new IllegalArgumentException("Cycle code cannot be null or empty");
        }

        RoleGenerationReadiness readiness = evaluateRoleGenerationReadiness(roleCode, cycleCode);
        if (!readiness.isReady()) {
            throw new IllegalStateException(String.join("; ", readiness.getReasons()));
        }

        ReviewCycle cycle = reviewCycleRepository.findByCycleCode(cycleCode)
                .orElseThrow(() -> new IllegalArgumentException("Review cycle not found: " + cycleCode));
        Role role = roleRepository.findByRoleCode(roleCode)
                .or(() -> roleRepository.findByRoleName(roleCode))
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleCode));

        List<User> targetUsers = userRepository.findAll().stream()
                .filter(u -> u != null && u.getActive())
                .filter(u -> u.getRoles() != null && u.getRoles().contains(role))
                .toList();

        if (targetUsers.isEmpty()) {
            return new KpiGenerationService.GenerationSummary(0, 0, 0, 0);
        }

        KpiGenerationService.GenerationSummary summary = generationService.generateKpisForRole(role, cycle);

        log.info("Completed generating KPI assignments for role {} in cycle {}. KPIs created: {}, failed users: {}, skipped users: {}",
                roleCode, cycleCode, summary.getCreatedKpis(), summary.getFailedUsers(), summary.getSkippedUsers());
        return summary;
    }

    public RoleGenerationReadiness evaluateRoleGenerationReadiness(String roleCode, String cycleCode) {
        if (roleCode == null || roleCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Role code cannot be null or empty");
        }
        if (cycleCode == null || cycleCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Cycle code cannot be null or empty");
        }

        Role role = roleRepository.findByRoleCode(roleCode)
                .or(() -> roleRepository.findByRoleName(roleCode))
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleCode));

        ReviewCycle cycle = reviewCycleRepository.findByCycleCode(cycleCode)
                .orElseThrow(() -> new IllegalArgumentException("Review cycle not found: " + cycleCode));

        List<String> reasons = new ArrayList<>();
        boolean cycleEligible = cycle.getStatus() == ReviewCycle.CycleStatus.OPEN
                || cycle.getStatus() == ReviewCycle.CycleStatus.IN_PROGRESS;
        if (!cycleEligible) {
            reasons.add("Cycle must be OPEN or IN_PROGRESS");
        }

        int activeUsersInRole = (int) userRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .filter(u -> {
                    Set<Role> roles = u.getRoles();
                    return roles != null && roles.contains(role);
                })
                .count();
        if (activeUsersInRole == 0) {
            reasons.add("No active users found for role " + roleCode);
        }

        List<KpiTemplate> templates = templateRepository.findByRoleAndStatusAndActiveTrue(role, KpiTemplate.TemplateStatus.ACTIVE);
        if (templates.isEmpty()) {
            reasons.add("No ACTIVE template found for role " + roleCode);
        }

        List<TemplateReadiness> templateChecks = new ArrayList<>();
        for (KpiTemplate template : templates) {
            List<KpiTemplateItem> templateItems = templateItemRepository.findByTemplate(template);

            int activeItemCount = (int) templateItems.stream()
                    .filter(item -> item != null && Boolean.TRUE.equals(item.getActive()))
                    .filter(item -> item.getKpiMaster() != null && Boolean.TRUE.equals(item.getKpiMaster().getActive()))
                    .count();
            double activeWeight = templateItems.stream()
                    .filter(item -> item != null && Boolean.TRUE.equals(item.getActive()))
                    .filter(item -> item.getKpiMaster() != null && Boolean.TRUE.equals(item.getKpiMaster().getActive()))
                    .map(KpiTemplateItem::getWeight)
                    .filter(java.util.Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();

            List<String> templateReasons = new ArrayList<>();
            if (activeItemCount == 0) {
                templateReasons.add("No ACTIVE template items with ACTIVE KPI masters");
            }
            if (Math.abs(activeWeight - 100.0) >= 0.01) {
                templateReasons.add("ACTIVE item weights must total 100 (current " + activeWeight + ")");
            }

            boolean eligible = templateReasons.isEmpty();
            templateChecks.add(new TemplateReadiness(
                    template.getId(),
                    template.getTemplateCode(),
                    template.getTemplateName(),
                    activeItemCount,
                    activeWeight,
                    eligible,
                    templateReasons
            ));
        }

        boolean hasEligibleTemplate = templateChecks.stream().anyMatch(TemplateReadiness::isEligible);
        if (!hasEligibleTemplate) {
            reasons.add("No ACTIVE role template with ACTIVE items and 100% total weight found for role: " + roleCode);
        }

        boolean ready = cycleEligible && activeUsersInRole > 0 && hasEligibleTemplate;
        return new RoleGenerationReadiness(roleCode, cycleCode, ready, activeUsersInRole, cycleEligible, reasons, templateChecks);
    }
}

