package com.example.kpi.service;

import com.example.kpi.entity.KpiMaster;
import com.example.kpi.entity.KpiTemplate;
import com.example.kpi.entity.KpiTemplateItem;
import com.example.kpi.entity.Role;
import com.example.kpi.repository.KpiMasterRepository;
import com.example.kpi.repository.KpiTemplateItemRepository;
import com.example.kpi.repository.KpiTemplateRepository;
import com.example.kpi.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * KPI Template Configuration Service - wraps KpiTemplateService for configuration endpoints
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class KpiTemplateConfigurationService {

    private final KpiTemplateRepository kpiTemplateRepository;
    private final KpiTemplateService kpiTemplateService;
    private final KpiTemplateItemRepository kpiTemplateItemRepository;
    private final KpiMasterRepository kpiMasterRepository;
    private final RoleRepository roleRepository;

    public List<KpiTemplate> findAll() {
        // Exclude ARCHIVED templates (treated as deleted in the admin UI).
        log.debug("Fetching all non-archived KPI templates");
        return kpiTemplateRepository.findByStatusNot(KpiTemplate.TemplateStatus.ARCHIVED);
    }

    public KpiTemplate create(String templateName, String templateCode, String description, Long roleId) {
        log.info("Creating new KPI template: {}", templateCode);
        KpiTemplate created = kpiTemplateService.createTemplate(templateName, templateCode, description);
        if (roleId != null && roleId > 0) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new IllegalArgumentException("Role not found"));
            created.setRole(role);
        }
        return kpiTemplateRepository.save(created);
    }

    public KpiTemplate update(Long id, String templateName, String description, Long roleId) {
        if (id == null || id <= 0) {
            log.error("Invalid template ID: {}", id);
            throw new IllegalArgumentException("Template ID must be a positive number");
        }

        log.info("Updating KPI template: {}", id);

        KpiTemplate existing = kpiTemplateRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Template not found: {}", id);
                    return new IllegalArgumentException("Template not found");
                });

        if (templateName != null && !templateName.trim().isEmpty()) {
            existing.setTemplateName(templateName.trim());
        }
        if (description != null) {
            existing.setDescription(description);
        }
        if (roleId != null) {
            if (roleId > 0) {
                Role role = roleRepository.findById(roleId)
                        .orElseThrow(() -> new IllegalArgumentException("Role not found"));
                existing.setRole(role);
            } else {
                existing.setRole(null);
            }
        }

        return kpiTemplateRepository.save(existing);
    }

    public void delete(Long id) {
        if (id == null || id <= 0) {
            log.error("Invalid template ID: {}", id);
            throw new IllegalArgumentException("Template ID must be a positive number");
        }

        log.info("Archiving KPI template: {}", id);
        kpiTemplateService.archiveTemplate(id);
    }

    @Transactional(readOnly = true)
    public KpiTemplate findById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Template ID must be a positive number");
        }

        KpiTemplate template = kpiTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));

        return template;
    }

    public KpiTemplateItem addTemplateItem(Long templateId, Long kpiMasterId, Double weight, String target) {
        if (templateId == null || templateId <= 0) {
            throw new IllegalArgumentException("Template ID must be a positive number");
        }
        if (kpiMasterId == null || kpiMasterId <= 0) {
            throw new IllegalArgumentException("KPI master ID must be a positive number");
        }
        if (weight == null || weight < 0 || weight > 100) {
            throw new IllegalArgumentException("Weight must be between 0 and 100");
        }

        KpiTemplate template = kpiTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        KpiMaster master = kpiMasterRepository.findById(kpiMasterId)
                .orElseThrow(() -> new IllegalArgumentException("KPI Master not found"));

        if (template.getRole() != null) {
            Long roleId = template.getRole().getId();
            boolean supportsRole = kpiMasterRepository.countRoleMapping(kpiMasterId, roleId) > 0;
            if (!supportsRole) {
                throw new IllegalStateException("Selected KPI Master is not mapped to template role");
            }
        }

        // Prevent duplicates within same template.
        boolean alreadyExists = kpiTemplateItemRepository.findByTemplate(template).stream()
                .anyMatch(item -> item.getKpiMaster() != null
                        && item.getKpiMaster().getId().equals(master.getId())
                        && Boolean.TRUE.equals(item.getActive()));
        if (alreadyExists) {
            throw new IllegalStateException("KPI Master already exists in template");
        }

        int nextSequence = kpiTemplateItemRepository.findByTemplate(template).stream()
                .map(KpiTemplateItem::getSequenceOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        double currentWeight = kpiTemplateItemRepository.findByTemplateAndActiveTrue(template).stream()
                .map(KpiTemplateItem::getWeight)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        if (currentWeight + weight > 100.01) {
            throw new IllegalStateException("Total template weight cannot exceed 100");
        }

        KpiTemplateItem item = KpiTemplateItem.builder()
                .template(template)
                .kpiMaster(master)
                .weight(weight)
                .target(target == null ? "" : target)
                .sequenceOrder(nextSequence)
                .active(true)
                .build();

        return kpiTemplateItemRepository.save(item);
    }

    public void removeTemplateItem(Long templateId, Long itemId) {
        if (templateId == null || templateId <= 0 || itemId == null || itemId <= 0) {
            throw new IllegalArgumentException("Template ID and Item ID must be positive numbers");
        }

        KpiTemplate template = kpiTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        KpiTemplateItem item = kpiTemplateItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Template item not found"));

        if (item.getTemplate() == null || !template.getId().equals(item.getTemplate().getId())) {
            throw new IllegalStateException("Template item does not belong to template");
        }

        kpiTemplateItemRepository.delete(item);
    }

    public void activateTemplate(Long templateId) {
        kpiTemplateService.activateTemplate(templateId);
    }

    public KpiTemplate cloneTemplate(Long templateId) {
        return kpiTemplateService.cloneTemplate(templateId);
    }

    public void archiveTemplate(Long templateId) {
        kpiTemplateService.archiveTemplate(templateId);
    }
}
