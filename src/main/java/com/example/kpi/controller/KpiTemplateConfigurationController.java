package com.example.kpi.controller;

import com.example.kpi.entity.KpiTemplate;
import com.example.kpi.entity.KpiTemplateItem;
import com.example.kpi.repository.KpiTemplateItemRepository;
import com.example.kpi.service.KpiTemplateConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/config/kpi-templates")
@RequiredArgsConstructor
@Slf4j
public class KpiTemplateConfigurationController {

    private final KpiTemplateConfigurationService service;
    private final KpiTemplateItemRepository templateItemRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> findAll() {
        log.debug("Fetching all KPI templates");
        List<KpiTemplate> templates = service.findAll();
        List<Map<String, Object>> response = templates.stream()
                .map(t -> toTemplateResponse(t, false))
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiTemplate> create(@RequestBody Map<String, Object> req) {
        String templateName = req.get("templateName") == null ? null : String.valueOf(req.get("templateName"));
        String templateCode = req.get("templateCode") == null ? null : String.valueOf(req.get("templateCode"));
        String description = req.get("description") == null ? null : String.valueOf(req.get("description"));
        Long roleId = req.get("roleId") == null ? null : ((Number) req.get("roleId")).longValue();

        log.info("Creating new KPI template: {}", templateCode);
        try {
            if (templateName == null || templateCode == null) {
                return ResponseEntity.badRequest().build();
            }
            KpiTemplate created = service.create(templateName, templateCode, description, roleId);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("Error creating KPI template", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiTemplate> update(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        String templateName = req.get("templateName") == null ? null : String.valueOf(req.get("templateName"));
        String description = req.get("description") == null ? null : String.valueOf(req.get("description"));
        Long roleId = req.get("roleId") == null ? null : ((Number) req.get("roleId")).longValue();

        log.info("Updating KPI template: {}", id);
        try {
            KpiTemplate updated = service.update(id, templateName, description, roleId);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Error updating KPI template: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting KPI template: {}", id);
        try {
            service.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting KPI template: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> findById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(toTemplateResponse(service.findById(id), true));
        } catch (IllegalArgumentException e) {
            // The only way findById rejects its argument is an unknown id, so
            // this is a 404 rather than a failure worth logging.
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error loading KPI template: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    private Map<String, Object> toTemplateResponse(KpiTemplate t, boolean includeItems) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", t.getId());
        dto.put("templateName", t.getTemplateName());
        dto.put("templateCode", t.getTemplateCode());
        dto.put("versionNumber", t.getVersionNumber());
        dto.put("description", t.getDescription());
        dto.put("status", t.getStatus() != null ? t.getStatus().name() : null);
        dto.put("active", t.getActive());
        dto.put("createdAt", t.getCreatedAt());
        dto.put("updatedAt", t.getUpdatedAt());

        if (t.getRole() != null) {
            Map<String, Object> role = new LinkedHashMap<>();
            role.put("id", t.getRole().getId());
            role.put("roleName", t.getRole().getRoleName());
            role.put("roleCode", t.getRole().getRoleCode());
            dto.put("role", role);
        } else {
            dto.put("role", null);
        }

        List<Map<String, Object>> items = new ArrayList<>();
        if (includeItems) {
            List<KpiTemplateItem> dbItems = templateItemRepository.findByTemplateOrderBySequenceOrder(t);
            for (KpiTemplateItem item : dbItems) {
                Map<String, Object> itemDto = new LinkedHashMap<>();
                itemDto.put("id", item.getId());
                itemDto.put("weight", item.getWeight());
                itemDto.put("target", item.getTarget());
                itemDto.put("targetValue", item.getTarget());
                itemDto.put("sequenceOrder", item.getSequenceOrder());
                itemDto.put("active", item.getActive());

                if (item.getKpiMaster() != null) {
                    Map<String, Object> km = new LinkedHashMap<>();
                    km.put("id", item.getKpiMaster().getId());
                    km.put("kpiCode", item.getKpiMaster().getKpiCode());
                    km.put("kpiName", item.getKpiMaster().getKpiName());
                    itemDto.put("kpiMaster", km);
                    itemDto.put("kpiCode", item.getKpiMaster().getKpiCode());
                    itemDto.put("kpiName", item.getKpiMaster().getKpiName());
                }

                items.add(itemDto);
            }
        }
        dto.put("templateItems", items);
        return dto;
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiTemplateItem> addItem(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        try {
            Long kpiMasterId = req.get("kpiMasterId") == null ? null : ((Number) req.get("kpiMasterId")).longValue();
            Double weight = req.get("weight") == null ? null : ((Number) req.get("weight")).doubleValue();
            String target = req.get("target") == null ? "" : String.valueOf(req.get("target"));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(service.addTemplateItem(id, kpiMasterId, weight, target));
        } catch (Exception e) {
            log.error("Error adding item to template: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        try {
            service.removeTemplateItem(id, itemId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error removing template item {} from template {}", itemId, id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> activate(@PathVariable Long id) {
        try {
            service.activateTemplate(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error activating template: {}", id, e);
            String message = (e.getMessage() == null || e.getMessage().isBlank())
                    ? "Failed to activate template"
                    : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("error", message));
        }
    }

    @PostMapping("/{id}/clone")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiTemplate> cloneTemplate(@PathVariable Long id) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.cloneTemplate(id));
        } catch (Exception e) {
            log.error("Error cloning template: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        try {
            service.archiveTemplate(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error archiving template: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
