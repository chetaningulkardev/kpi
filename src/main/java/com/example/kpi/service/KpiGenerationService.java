package com.example.kpi.service;

import com.example.kpi.entity.*;
import com.example.kpi.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * KPI Generation Engine - Automatically generates KPI records from templates for employees in a review cycle
 */
@Service
@Slf4j
@Transactional
public class KpiGenerationService {

    private final KPIRepository kpiRepository;
    private final KpiAssignmentRepository assignmentRepository;
    private final KpiTemplateRepository templateRepository;
    private final KpiTemplateItemRepository templateItemRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final UserRepository userRepository;
    private final UserSquadAssignmentRepository userSquadAssignmentRepository;
    private final UserPortfolioAssignmentRepository userPortfolioAssignmentRepository;
    private final UserTribeAssignmentRepository userTribeAssignmentRepository;

    public KpiGenerationService(
            KPIRepository kpiRepository,
            KpiAssignmentRepository assignmentRepository,
            KpiTemplateRepository templateRepository,
            KpiTemplateItemRepository templateItemRepository,
            ReviewCycleRepository reviewCycleRepository,
            UserRepository userRepository,
            UserSquadAssignmentRepository userSquadAssignmentRepository,
            UserPortfolioAssignmentRepository userPortfolioAssignmentRepository,
            UserTribeAssignmentRepository userTribeAssignmentRepository) {
        this.kpiRepository = kpiRepository;
        this.assignmentRepository = assignmentRepository;
        this.templateRepository = templateRepository;
        this.templateItemRepository = templateItemRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.userRepository = userRepository;
        this.userSquadAssignmentRepository = userSquadAssignmentRepository;
        this.userPortfolioAssignmentRepository = userPortfolioAssignmentRepository;
        this.userTribeAssignmentRepository = userTribeAssignmentRepository;
    }

    public static final class GenerationSummary {
        private final int createdKpis;
        private final int processedUsers;
        private final int failedUsers;
        private final int skippedUsers;

        public GenerationSummary(int createdKpis, int processedUsers, int failedUsers, int skippedUsers) {
            this.createdKpis = createdKpis;
            this.processedUsers = processedUsers;
            this.failedUsers = failedUsers;
            this.skippedUsers = skippedUsers;
        }

        public int getCreatedKpis() { return createdKpis; }
        public int getProcessedUsers() { return processedUsers; }
        public int getFailedUsers() { return failedUsers; }
        public int getSkippedUsers() { return skippedUsers; }
        public boolean hasFailures() { return failedUsers > 0; }
    }

    public static final class UserGenerationResult {
        private final int createdKpis;
        private final int failedItems;
        private final boolean skipped;

        public UserGenerationResult(int createdKpis, int failedItems, boolean skipped) {
            this.createdKpis = createdKpis;
            this.failedItems = failedItems;
            this.skipped = skipped;
        }

        public int getCreatedKpis() { return createdKpis; }
        public int getFailedItems() { return failedItems; }
        public boolean isSkipped() { return skipped; }
    }

    /**
     * Generate KPIs for all employees in a review cycle
     */
    public GenerationSummary generateKpisForReviewCycle(Long reviewCycleId) {
        log.info("Starting KPI generation for review cycle: {}", reviewCycleId);

        // Validate review cycle ID
        if (reviewCycleId == null || reviewCycleId <= 0) {
            log.error("Invalid review cycle ID: {}", reviewCycleId);
            throw new IllegalArgumentException("Review cycle ID must be a positive number");
        }

        ReviewCycle cycle = reviewCycleRepository.findById(reviewCycleId)
                .orElseThrow(() -> {
                    log.error("Review cycle not found: {}", reviewCycleId);
                    return new IllegalArgumentException("Review cycle not found with ID: " + reviewCycleId);
                });

        List<User> allUsers = userRepository.findAll();

        if (allUsers == null || allUsers.isEmpty()) {
            log.warn("No users found in the system");
            cycle.setKpisGenerated(!kpiRepository.findByReviewCycle(cycle).isEmpty());
            reviewCycleRepository.save(cycle);
            return new GenerationSummary(0, 0, 0, 0);
        }

        int processedUsers = 0;
        int skippedUsers = 0;
        int failedUsers = 0;
        int createdKpis = 0;

        for (User user : allUsers) {
            if (user == null || !Boolean.TRUE.equals(user.getActive())) {
                continue;
            }
            processedUsers++;
            try {
                UserGenerationResult result = generateKpisForUser(user, cycle);
                createdKpis += result.getCreatedKpis();
                if (result.isSkipped()) {
                    skippedUsers++;
                }
                if (result.getFailedItems() > 0) {
                    failedUsers++;
                }
            } catch (Exception e) {
                log.error("Error generating KPIs for user: {}", user.getId(), e);
                failedUsers++;
            }
        }

        cycle.setKpisGenerated(!kpiRepository.findByReviewCycle(cycle).isEmpty());
        reviewCycleRepository.save(cycle);
        log.info("KPI generation completed for review cycle: {}. Processed users: {}, Created KPIs: {}, Failed users: {}, Skipped users: {}",
                reviewCycleId, processedUsers, createdKpis, failedUsers, skippedUsers);
        return new GenerationSummary(createdKpis, processedUsers, failedUsers, skippedUsers);
    }

    /**
     * Generate KPIs for a specific user in a review cycle
     */
    public UserGenerationResult generateKpisForUser(User user, ReviewCycle cycle) {
        // Validate inputs
        if (user == null) {
            log.error("User is null");
            throw new IllegalArgumentException("User cannot be null");
        }
        if (cycle == null) {
            log.error("Review cycle is null");
            throw new IllegalArgumentException("Review cycle cannot be null");
        }

        log.info("Generating KPIs for user: {} in cycle: {}", user.getId(), cycle.getId());

        // Get applicable template based on priority rules
        KpiTemplate template = resolveApplicableTemplate(user);
        if (template == null) {
            log.warn("No applicable KPI template found for user: {}", user.getId());
            return new UserGenerationResult(0, 0, true);
        }

        // Get all KPI items from the template via repository to avoid ConcurrentModificationException
        List<KpiTemplateItem> loadedItems = templateItemRepository.findByTemplate(template);
        if (loadedItems == null || loadedItems.isEmpty()) {
            log.warn("No KPI items found in template: {} for user: {}", template.getId(), user.getId());
            return new UserGenerationResult(0, 0, true);
        }

        List<KpiTemplateItem> templateItems = loadedItems.stream()
                .filter(item -> item != null
                        && Boolean.TRUE.equals(item.getActive())
                        && item.getKpiMaster() != null
                        && Boolean.TRUE.equals(item.getKpiMaster().getActive()))
                .sorted(Comparator.comparingInt(item -> item.getSequenceOrder() != null ? item.getSequenceOrder() : Integer.MAX_VALUE))
                .collect(Collectors.toList());

        if (templateItems.isEmpty()) {
            log.warn("No ACTIVE template items with ACTIVE KPI masters found in template: {} for user: {}", template.getId(), user.getId());
            return new UserGenerationResult(0, 0, true);
        }

        Map<Long, Kpi> existingByMasterId = kpiRepository.findByReviewCycleAndUser(cycle, user).stream()
                .filter(k -> k != null && k.getKpiMaster() != null && k.getKpiMaster().getId() != null)
                .collect(Collectors.toMap(k -> k.getKpiMaster().getId(), k -> k, (left, right) -> left));

        int createdCount = 0;
        int failedItems = 0;

        // Generate KPI record for each template item
        for (KpiTemplateItem item : templateItems) {
            try {
                KpiMaster master = item.getKpiMaster();
                if (master.getId() == null) {
                    continue;
                }

                Kpi existing = existingByMasterId.get(master.getId());
                if (existing != null) {
                    syncExistingPendingKpi(existing, template, master, item);
                    continue;
                }

                Kpi kpi = Kpi.builder()
                        .reviewCycle(cycle)
                        .user(user)
                        .kpiMaster(master)
                        .template(template)
                        .kpiName(master.getKpiName())
                        .kpiCode(master.getKpiCode())
                        .categoryName(master.getCategory() != null ? master.getCategory().getCategoryName() : "UNCATEGORIZED")
                        .weight(item.getWeight() != null ? item.getWeight() : 0.0)
                        .target(item.getTarget() != null ? item.getTarget() : "")
                        .frequency(master.getFrequency() != null ? master.getFrequency().getFrequencyCode() : "YEARLY")
                        .snapshotData(buildSnapshotData(master, item))
                        .build();

                kpiRepository.save(kpi);
                existingByMasterId.put(master.getId(), kpi);
                createdCount++;
                log.debug("Created KPI: {} for user: {} in cycle: {}",
                        kpi.getKpiCode(), user.getId(), cycle.getId());
            } catch (DataIntegrityViolationException e) {
                // Idempotent safety for concurrent/rerun generation calls.
                log.debug("Duplicate KPI prevented for user {} and template item {}", user.getId(), item.getId());
            } catch (Exception e) {
                log.error("Error creating KPI for template item: {}", item.getId(), e);
                failedItems++;
            }
        }

        return new UserGenerationResult(createdCount, failedItems, false);
    }

    /**
     * Resolve applicable template for a user based on priority rules
     * Priority: Employee > Squad > Portfolio > Tribe > Role
     */
    private KpiTemplate resolveApplicableTemplate(User user) {
        if (user == null) {
            log.error("User is null in resolveApplicableTemplate");
            return null;
        }

        // 1. Check Employee-level assignment (highest priority)
        KpiTemplate employeeTemplate = pickBestEligibleTemplateFromAssignments(
                assignmentRepository.findByUserAndActive(user, true), KpiAssignment.AssignmentType.EMPLOYEE);
        if (employeeTemplate != null) {
            log.debug("Found employee-level template assignment for user: {}", user.getId());
            return employeeTemplate;
        }

        // 2. Check Squad-level assignment
        Set<Long> userSquadIds = userSquadAssignmentRepository.findByUserIdAndActiveTrue(user.getId()).stream()
                .map(UserSquadAssignment::getSquad)
                .filter(Objects::nonNull)
                .map(Squad::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!userSquadIds.isEmpty()) {
            List<KpiAssignment> squadAssignments = assignmentRepository.findByAssignmentTypeAndActive(
                    KpiAssignment.AssignmentType.SQUAD, true).stream()
                    .filter(a -> a != null && a.getSquad() != null && a.getSquad().getId() != null)
                    .filter(a -> userSquadIds.contains(a.getSquad().getId()))
                    .toList();
            KpiTemplate squadTemplate = pickBestEligibleTemplateFromAssignments(squadAssignments, null);
            if (squadTemplate != null) {
                log.debug("Found squad-level template assignment for user: {}", user.getId());
                return squadTemplate;
            }
        }

        // 3. Check Portfolio-level assignment
        Set<Long> userPortfolioIds = userPortfolioAssignmentRepository.findByUserIdAndActiveTrue(user.getId()).stream()
                .map(UserPortfolioAssignment::getPortfolio)
                .filter(Objects::nonNull)
                .map(Portfolio::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!userPortfolioIds.isEmpty()) {
            List<KpiAssignment> portfolioAssignments = assignmentRepository.findByAssignmentTypeAndActive(
                    KpiAssignment.AssignmentType.PORTFOLIO, true).stream()
                    .filter(a -> a != null && a.getPortfolio() != null && a.getPortfolio().getId() != null)
                    .filter(a -> userPortfolioIds.contains(a.getPortfolio().getId()))
                    .toList();
            KpiTemplate portfolioTemplate = pickBestEligibleTemplateFromAssignments(portfolioAssignments, null);
            if (portfolioTemplate != null) {
                log.debug("Found portfolio-level template assignment for user: {}", user.getId());
                return portfolioTemplate;
            }
        }

        // 4. Check Tribe-level assignment
        Set<Long> userTribeIds = userTribeAssignmentRepository.findByUserIdAndActiveTrue(user.getId()).stream()
                .map(UserTribeAssignment::getTribe)
                .filter(Objects::nonNull)
                .map(Tribe::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!userTribeIds.isEmpty()) {
            List<KpiAssignment> tribeAssignments = assignmentRepository.findByAssignmentTypeAndActive(
                    KpiAssignment.AssignmentType.TRIBE, true).stream()
                    .filter(a -> a != null && a.getTribe() != null && a.getTribe().getId() != null)
                    .filter(a -> userTribeIds.contains(a.getTribe().getId()))
                    .toList();
            KpiTemplate tribeTemplate = pickBestEligibleTemplateFromAssignments(tribeAssignments, null);
            if (tribeTemplate != null) {
                log.debug("Found tribe-level template assignment for user: {}", user.getId());
                return tribeTemplate;
            }
        }

        // 5. Check Role-level KpiAssignment (lowest priority)
        Set<Role> userRoles = user.getRoles();
        if (userRoles != null && !userRoles.isEmpty()) {
            List<KpiAssignment> roleAssignments = userRoles.stream()
                    .filter(Objects::nonNull)
                    .flatMap(role -> assignmentRepository.findByRoleAndActive(role, true).stream())
                    .toList();
            KpiTemplate roleTemplate = pickBestEligibleTemplateFromAssignments(roleAssignments, KpiAssignment.AssignmentType.ROLE);
            if (roleTemplate != null) {
                log.debug("Found role-level template assignment for user: {}", user.getId());
                return roleTemplate;
            }
        }

        // 6. Fallback: find ACTIVE templates directly linked to any of the user's roles
        //    (used when no KpiAssignment records exist but templates have role_id set)
        if (userRoles != null && !userRoles.isEmpty()) {
            for (Role userRole : userRoles) {
                if (userRole == null) continue;
                List<KpiTemplate> directRoleTemplates = templateRepository.findByRoleAndStatusAndActiveTrue(
                        userRole, KpiTemplate.TemplateStatus.ACTIVE);
                if (!directRoleTemplates.isEmpty()) {
                    KpiTemplate best = directRoleTemplates.stream()
                            .filter(this::isTemplateEligibleForGeneration)
                            .sorted(Comparator.comparingInt((KpiTemplate t) -> t.getVersionNumber() != null ? t.getVersionNumber() : 0).reversed())
                            .findFirst()
                            .orElse(null);
                    if (best == null) {
                        continue;
                    }
                    log.debug("Found direct role template for user: {} with role: {}", user.getId(), userRole.getId());
                    return best;
                }
            }
        }

        log.warn("No applicable template found for user: {}", user.getId());
        return null;
    }

    /**
     * Bulk generate KPIs for specific role and review cycle
     */
    public GenerationSummary generateKpisForRole(Role role, ReviewCycle cycle) {
        if (role == null) {
            log.error("Role is null");
            throw new IllegalArgumentException("Role cannot be null");
        }
        if (cycle == null) {
            log.error("Review cycle is null");
            throw new IllegalArgumentException("Review cycle cannot be null");
        }

        log.info("Generating KPIs for role: {} in cycle: {}", role.getId(), cycle.getId());

        List<User> usersWithRole = userRepository.findAll().stream()
                .filter(u -> u != null && u.getActive() && u.getRoles() != null && u.getRoles().contains(role))
                .collect(Collectors.toList());

        if (usersWithRole.isEmpty()) {
            log.warn("No active users found with role: {}", role.getId());
            return new GenerationSummary(0, 0, 0, 0);
        }

        int createdKpis = 0;
        int failedUsers = 0;
        int skippedUsers = 0;

        for (User user : usersWithRole) {
            try {
                UserGenerationResult result = generateKpisForUser(user, cycle);
                createdKpis += result.getCreatedKpis();
                if (result.isSkipped()) {
                    skippedUsers++;
                }
                if (result.getFailedItems() > 0) {
                    failedUsers++;
                }
            } catch (Exception e) {
                log.error("Error generating KPIs for user: {} in role: {}", user.getId(), role.getId(), e);
                failedUsers++;
            }
        }

        log.info("KPI generation completed for role: {} in cycle: {}. Users processed: {}, Created KPIs: {}, Failed users: {}, Skipped users: {}",
                 role.getId(), cycle.getId(), usersWithRole.size(), createdKpis, failedUsers, skippedUsers);
        return new GenerationSummary(createdKpis, usersWithRole.size(), failedUsers, skippedUsers);
    }

    private String buildSnapshotData(KpiMaster master, KpiTemplateItem item) {
        String category = master.getCategory() != null ? master.getCategory().getCategoryName() : "UNCATEGORIZED";
        String definition = safe(master.getDescription());
        String frequency = master.getFrequency() != null ? master.getFrequency().getFrequencyCode() : "YEARLY";
        String dataSource = safe(master.getDataSource());
        String target = safe(item.getTarget());
        Double weight = item.getWeight() != null ? item.getWeight() : 0.0;

        // Snapshot preserves the exact KPI row used at generation time for audit/history.
        return "{"
                + "\"category\":\"" + safe(category) + "\","
                + "\"kpi\":\"" + safe(master.getKpiName()) + "\","
                + "\"definition\":\"" + definition + "\","
                + "\"targetThreshold\":\"" + target + "\","
                + "\"frequency\":\"" + safe(frequency) + "\","
                + "\"dataSource\":\"" + dataSource + "\","
                + "\"weight\":" + weight
                + "}";
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Keep pending generated KPI rows aligned with latest template values (weight/target/etc.) on reruns.
     */
    private void syncExistingPendingKpi(Kpi existing, KpiTemplate template, KpiMaster master, KpiTemplateItem item) {
        if (existing.getStatus() != Kpi.KpiStatus.PENDING) {
            return;
        }

        Double weight = item.getWeight() != null ? item.getWeight() : 0.0;
        String target = item.getTarget() != null ? item.getTarget() : "";
        String frequency = master.getFrequency() != null ? master.getFrequency().getFrequencyCode() : "YEARLY";
        String categoryName = master.getCategory() != null ? master.getCategory().getCategoryName() : "UNCATEGORIZED";
        String snapshot = buildSnapshotData(master, item);

        boolean changed = false;
        if (!Objects.equals(existing.getTemplate(), template)) {
            existing.setTemplate(template);
            changed = true;
        }
        if (!Objects.equals(existing.getKpiName(), master.getKpiName())) {
            existing.setKpiName(master.getKpiName());
            changed = true;
        }
        if (!Objects.equals(existing.getKpiCode(), master.getKpiCode())) {
            existing.setKpiCode(master.getKpiCode());
            changed = true;
        }
        if (!Objects.equals(existing.getCategoryName(), categoryName)) {
            existing.setCategoryName(categoryName);
            changed = true;
        }
        if (!Objects.equals(existing.getWeight(), weight)) {
            existing.setWeight(weight);
            changed = true;
        }
        if (!Objects.equals(existing.getTarget(), target)) {
            existing.setTarget(target);
            changed = true;
        }
        if (!Objects.equals(existing.getFrequency(), frequency)) {
            existing.setFrequency(frequency);
            changed = true;
        }
        if (!Objects.equals(existing.getSnapshotData(), snapshot)) {
            existing.setSnapshotData(snapshot);
            changed = true;
        }

        if (changed) {
            kpiRepository.save(existing);
            log.debug("Synced existing pending KPI {} with latest template values", existing.getId());
        }
    }

    private KpiTemplate pickBestEligibleTemplateFromAssignments(List<KpiAssignment> assignments, KpiAssignment.AssignmentType type) {
        if (assignments == null || assignments.isEmpty()) {
            return null;
        }
        return assignments.stream()
                .filter(Objects::nonNull)
                .filter(a -> type == null || a.getAssignmentType() == type)
                .sorted(Comparator
                        .comparingInt((KpiAssignment a) -> a.getPriority() != null ? a.getPriority() : Integer.MAX_VALUE)
                        .thenComparing(a -> a.getId() != null ? a.getId() : Long.MAX_VALUE))
                .map(KpiAssignment::getTemplate)
                .filter(this::isTemplateEligibleForGeneration)
                .findFirst()
                .orElse(null);
    }

    private boolean isTemplateEligibleForGeneration(KpiTemplate template) {
        if (template == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(template.getActive()) || template.getStatus() != KpiTemplate.TemplateStatus.ACTIVE) {
            return false;
        }
        // Use repository to avoid ConcurrentModificationException on PersistentSet
        List<KpiTemplateItem> items = templateItemRepository.findByTemplate(template);
        if (items == null || items.isEmpty()) {
            return false;
        }
        List<KpiTemplateItem> activeItems = items.stream()
                .filter(Objects::nonNull)
                .filter(i -> Boolean.TRUE.equals(i.getActive()))
                .filter(i -> i.getKpiMaster() != null && Boolean.TRUE.equals(i.getKpiMaster().getActive()))
                .toList();
        if (activeItems.isEmpty()) {
            return false;
        }
        double activeWeight = activeItems.stream()
                .map(KpiTemplateItem::getWeight)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        return Math.abs(activeWeight - 100.0) < 0.01;
    }
}

