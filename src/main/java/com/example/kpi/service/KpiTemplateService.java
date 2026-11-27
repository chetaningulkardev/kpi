package com.example.kpi.service;

import com.example.kpi.entity.*;
import com.example.kpi.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * KPI Template Service - Manages KPI template creation, versioning, and assignment
 */
@Service
@Slf4j
@Transactional
public class KpiTemplateService {

    private final KpiTemplateRepository templateRepository;
    private final KpiTemplateItemRepository templateItemRepository;
    private final KpiMasterRepository kpiMasterRepository;
    private final KpiAssignmentRepository assignmentRepository;

    public KpiTemplateService(
            KpiTemplateRepository templateRepository,
            KpiTemplateItemRepository templateItemRepository,
            KpiMasterRepository kpiMasterRepository,
            KpiAssignmentRepository assignmentRepository) {
        this.templateRepository = templateRepository;
        this.templateItemRepository = templateItemRepository;
        this.kpiMasterRepository = kpiMasterRepository;
        this.assignmentRepository = assignmentRepository;
    }

    /**
     * Create a new KPI template - with input validation
     */
    public KpiTemplate createTemplate(String templateName, String templateCode, String description) {
        // Validate inputs
        if (templateName == null || templateName.trim().isEmpty()) {
            log.error("Template name cannot be null or empty");
            throw new IllegalArgumentException("Template name cannot be null or empty");
        }
        if (templateCode == null || templateCode.trim().isEmpty()) {
            log.error("Template code cannot be null or empty");
            throw new IllegalArgumentException("Template code cannot be null or empty");
        }

        log.info("Creating new KPI template: {}", templateCode);

        KpiTemplate template = KpiTemplate.builder()
                .templateName(templateName.trim())
                .templateCode(templateCode.trim())
                .versionNumber(1)
                .description(description)
                .status(KpiTemplate.TemplateStatus.DRAFT)
                .active(false)
                .build();

        return templateRepository.save(template);
    }

    /**
     * Add KPI to template with weight and target - with comprehensive validation
     */
    public KpiTemplateItem addKpiToTemplate(Long templateId, Long kpiMasterId, Double weight, String target) {
        // Validate inputs
        if (templateId == null || templateId <= 0) {
            log.error("Invalid template ID: {}", templateId);
            throw new IllegalArgumentException("Template ID must be a positive number");
        }
        if (kpiMasterId == null || kpiMasterId <= 0) {
            log.error("Invalid KPI Master ID: {}", kpiMasterId);
            throw new IllegalArgumentException("KPI Master ID must be a positive number");
        }
        if (weight == null || weight < 0 || weight > 100) {
            log.error("Invalid weight: {}. Must be between 0-100", weight);
            throw new IllegalArgumentException("Weight must be between 0 and 100, received: " + weight);
        }

        log.info("Adding KPI: {} to template: {} with weight: {}", kpiMasterId, templateId, weight);

        KpiTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> {
                    log.error("Template not found: {}", templateId);
                    return new IllegalArgumentException("Template not found with ID: " + templateId);
                });

        KpiMaster kpiMaster = kpiMasterRepository.findById(kpiMasterId)
                .orElseThrow(() -> {
                    log.error("KPI Master not found: {}", kpiMasterId);
                    return new IllegalArgumentException("KPI Master not found with ID: " + kpiMasterId);
                });

        // Validate weight doesn't exceed total
        Set<KpiTemplateItem> existingItems = template.getTemplateItems();
        if (existingItems == null) {
            existingItems = new HashSet<>();
        }

        Double currentTotal = existingItems.stream()
                .filter(item -> item != null && item.getWeight() != null)
                .mapToDouble(KpiTemplateItem::getWeight)
                .sum();

        if (currentTotal + weight > 100.0 + 0.01) { // Allow small rounding error
            log.error("Adding weight {} would exceed 100%. Current total: {}", weight, currentTotal);
            throw new IllegalStateException("Total weight would exceed 100%. Current: " + currentTotal + ", New: " + weight);
        }

        KpiTemplateItem item = KpiTemplateItem.builder()
                .template(template)
                .kpiMaster(kpiMaster)
                .weight(weight)
                .target(target != null ? target : "")
                .sequenceOrder(existingItems.size() + 1)
                .active(true)
                .build();

        return templateItemRepository.save(item);
    }

    /**
     * Remove KPI from template - with validation
     */
    public void removeKpiFromTemplate(Long templateItemId) {
        if (templateItemId == null || templateItemId <= 0) {
            log.error("Invalid template item ID: {}", templateItemId);
            throw new IllegalArgumentException("Template item ID must be a positive number");
        }

        log.info("Removing template item: {}", templateItemId);

        KpiTemplateItem item = templateItemRepository.findById(templateItemId)
                .orElseThrow(() -> {
                    log.error("Template item not found: {}", templateItemId);
                    return new IllegalArgumentException("Template item not found with ID: " + templateItemId);
                });

        templateItemRepository.deleteById(templateItemId);
        log.debug("Template item deleted: {}", templateItemId);
    }

    /**
     * Validate template weights sum to 100%
     */
    public boolean validateTemplate(KpiTemplate template) {
        if (template == null) {
            log.error("Template is null in validateTemplate");
            return false;
        }
        return template.isWeightValid();
    }

    /**
     * Activate a template - with comprehensive validation
     */
    public void activateTemplate(Long templateId) {
        if (templateId == null || templateId <= 0) {
            log.error("Invalid template ID: {}", templateId);
            throw new IllegalArgumentException("Template ID must be a positive number");
        }

        log.info("Activating template: {}", templateId);

        KpiTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> {
                    log.error("Template not found: {}", templateId);
                    return new IllegalArgumentException("Template not found with ID: " + templateId);
                });

        List<KpiTemplateItem> items = templateItemRepository.findByTemplate(template);
        if (items == null || items.isEmpty()) {
            log.error("Template has no items. Cannot activate.");
            throw new IllegalStateException("Template must have at least one KPI item to be activated");
        }

        double totalWeight = items.stream()
                .filter(Objects::nonNull)
                .map(KpiTemplateItem::getWeight)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        if (Math.abs(totalWeight - 100.0) >= 0.01) {
            log.error("Template weights do not total 100%. Current total: {}", totalWeight);
            throw new IllegalStateException("Template weights do not total 100%. Current total: " + totalWeight);
        }

        template.setStatus(KpiTemplate.TemplateStatus.ACTIVE);
        template.setActive(true);

        templateRepository.save(template);
        log.info("Template activated successfully: {}", templateId);
    }

    /**
     * Create a new version of an existing template
     */
    public KpiTemplate cloneTemplate(Long templateId) {
        if (templateId == null || templateId <= 0) {
            log.error("Invalid template ID: {}", templateId);
            throw new IllegalArgumentException("Template ID must be a positive number");
        }

        log.info("Creating new version for template: {}", templateId);

        KpiTemplate original = templateRepository.findById(templateId)
                .orElseThrow(() -> {
                    log.error("Template not found: {}", templateId);
                    return new IllegalArgumentException("Template not found with ID: " + templateId);
                });

        List<KpiTemplate> allVersions = templateRepository
                .findByTemplateCodeOrderByVersionNumberDesc(original.getTemplateCode());

        if (allVersions == null) {
            allVersions = new ArrayList<>();
        }

        Integer nextVersion = allVersions.stream()
                .filter(Objects::nonNull)
                .mapToInt(KpiTemplate::getVersionNumber)
                .max()
                .orElse(0) + 1;

        KpiTemplate newVersion = KpiTemplate.builder()
                .templateName(original.getTemplateName())
                .templateCode(original.getTemplateCode())
                .versionNumber(nextVersion)
                .description(original.getDescription())
                .status(KpiTemplate.TemplateStatus.DRAFT)
                .role(original.getRole())
                .employeeLevel(original.getEmployeeLevel())
                .active(false)
                .build();

        KpiTemplate saved = templateRepository.save(newVersion);

        // Clone all template items
        Set<KpiTemplateItem> originalItems = original.getTemplateItems();
        if (originalItems != null && !originalItems.isEmpty()) {
            for (KpiTemplateItem item : originalItems) {
                if (item == null) continue;

                KpiTemplateItem newItem = KpiTemplateItem.builder()
                        .template(saved)
                        .kpiMaster(item.getKpiMaster())
                        .weight(item.getWeight())
                        .employeeLevel(item.getEmployeeLevel())
                        .target(item.getTarget())
                        .sequenceOrder(item.getSequenceOrder())
                        .active(item.getActive())
                        .build();

                templateItemRepository.save(newItem);
            }
        }

        log.info("Created new template version: {} V{}", original.getTemplateCode(), nextVersion);
        return saved;
    }

    /**
     * Archive a template
     */
    public void archiveTemplate(Long templateId) {
        if (templateId == null || templateId <= 0) {
            log.error("Invalid template ID: {}", templateId);
            throw new IllegalArgumentException("Template ID must be a positive number");
        }

        log.info("Archiving template: {}", templateId);

        KpiTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> {
                    log.error("Template not found: {}", templateId);
                    return new IllegalArgumentException("Template not found with ID: " + templateId);
                });

        template.setStatus(KpiTemplate.TemplateStatus.ARCHIVED);
        template.setActive(false);

        templateRepository.save(template);
        log.info("Template archived successfully: {}", templateId);
    }

    /**
     * Copy template to a specific role or level
     */
    public KpiTemplate copyTemplateForRoleAndLevel(Long templateId, Role role, EmployeeLevel level) {
        if (templateId == null || templateId <= 0) {
            log.error("Invalid template ID: {}", templateId);
            throw new IllegalArgumentException("Template ID must be a positive number");
        }

        log.info("Copying template: {} for role: {} and level: {}", templateId,
                role != null ? role.getId() : "null",
                level != null ? level.getId() : "null");

        KpiTemplate original = templateRepository.findById(templateId)
                .orElseThrow(() -> {
                    log.error("Template not found: {}", templateId);
                    return new IllegalArgumentException("Template not found with ID: " + templateId);
                });

        String uniqueCode = original.getTemplateCode() + "_" + UUID.randomUUID().toString().substring(0, 4);
        KpiTemplate copy = createTemplate(
                original.getTemplateName(),
                uniqueCode,
                original.getDescription()
        );

        copy.setRole(role);
        copy.setEmployeeLevel(level);
        copy = templateRepository.save(copy);

        // Copy all items
        Set<KpiTemplateItem> originalItems = original.getTemplateItems();
        if (originalItems != null && !originalItems.isEmpty()) {
            for (KpiTemplateItem item : originalItems) {
                if (item == null) continue;

                KpiTemplateItem newItem = KpiTemplateItem.builder()
                        .template(copy)
                        .kpiMaster(item.getKpiMaster())
                        .weight(item.getWeight())
                        .employeeLevel(level)
                        .target(item.getTarget())
                        .sequenceOrder(item.getSequenceOrder())
                        .active(item.getActive())
                        .build();

                templateItemRepository.save(newItem);
            }
        }

        log.info("Template copied successfully for role: {} and level: {}",
                role != null ? role.getId() : "null", level != null ? level.getId() : "null");
        return copy;
    }

    /**
     * Get template with all its items
     */
    public KpiTemplate getTemplateWithItems(Long templateId) {
        if (templateId == null || templateId <= 0) {
            log.error("Invalid template ID: {}", templateId);
            throw new IllegalArgumentException("Template ID must be a positive number");
        }

        KpiTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> {
                    log.error("Template not found: {}", templateId);
                    return new IllegalArgumentException("Template not found with ID: " + templateId);
                });

        // Eagerly load template items
        List<KpiTemplateItem> items = templateItemRepository.findByTemplateOrderBySequenceOrder(template);
        if (items == null) {
            items = new ArrayList<>();
        }
        // Keep the managed collection intact to avoid orphan-removal side effects.
        template.getTemplateItems().size();

        return template;
    }

    /**
     * Assign template to a role - with validation
     */
    public void assignTemplateToRole(Long templateId, Role role, Integer priority) {
        if (templateId == null || templateId <= 0) {
            log.error("Invalid template ID: {}", templateId);
            throw new IllegalArgumentException("Template ID must be a positive number");
        }
        if (role == null) {
            log.error("Role is null");
            throw new IllegalArgumentException("Role cannot be null");
        }
        if (priority == null || priority < 0) {
            log.error("Invalid priority: {}", priority);
            throw new IllegalArgumentException("Priority must be a non-negative number");
        }

        log.info("Assigning template: {} to role: {} with priority: {}", templateId, role.getId(), priority);

        KpiTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> {
                    log.error("Template not found: {}", templateId);
                    return new IllegalArgumentException("Template not found with ID: " + templateId);
                });

        KpiAssignment assignment = KpiAssignment.builder()
                .template(template)
                .assignmentType(KpiAssignment.AssignmentType.ROLE)
                .role(role)
                .priority(priority)
                .active(true)
                .build();

        assignmentRepository.save(assignment);
        log.info("Template assigned to role successfully: {}", role.getId());
    }
}

