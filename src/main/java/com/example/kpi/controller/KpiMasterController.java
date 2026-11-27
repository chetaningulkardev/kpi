package com.example.kpi.controller;

import com.example.kpi.entity.KpiCategory;
import com.example.kpi.entity.KpiMaster;
import com.example.kpi.entity.Role;
import com.example.kpi.repository.KpiCategoryRepository;
import com.example.kpi.repository.KpiFrequencyRepository;
import com.example.kpi.repository.KpiMasterRepository;
import com.example.kpi.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/kpi/masters")
@RequiredArgsConstructor
@Slf4j
public class KpiMasterController {

    private final KpiMasterRepository repository;
    private final KpiCategoryRepository categoryRepository;
    private final KpiFrequencyRepository frequencyRepository;
    private final RoleRepository roleRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<KpiMaster>> findAll(@RequestParam(required = false) Long categoryId,
                                                   @RequestParam(required = false) Long roleId) {
        if (categoryId != null && roleId != null) {
            return categoryRepository.findById(categoryId)
                    .map(cat -> ResponseEntity.ok(repository.findDistinctByCategoryAndRoles_Id(cat, roleId)))
                    .orElse(ResponseEntity.notFound().build());
        }
        if (categoryId != null) {
            return categoryRepository.findById(categoryId)
                    .map(cat -> ResponseEntity.ok(repository.findByCategory(cat)))
                    .orElse(ResponseEntity.notFound().build());
        }
        if (roleId != null) {
            return ResponseEntity.ok(repository.findDistinctByRoles_Id(roleId));
        }
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<KpiMaster>> findActive() {
        return ResponseEntity.ok(repository.findByActiveTrue());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiMaster> findById(@PathVariable Long id) {
        return repository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiMaster> create(@RequestBody Map<String, Object> req) {
        try {
            KpiMaster master = new KpiMaster();
            master.setKpiName((String) req.get("kpiName"));
            master.setKpiCode((String) req.get("kpiCode"));
            master.setDescription((String) req.get("description"));
            master.setMeasurementUnit(req.get("measurementUnit") != null ? (String) req.get("measurementUnit") : "");
            master.setDataSource(req.get("dataSource") != null ? (String) req.get("dataSource") : "Manual");
            master.setDefaultWeight(req.get("defaultWeight") != null
                    ? ((Number) req.get("defaultWeight")).doubleValue() : 10.0);
            master.setActive(req.get("active") == null || (Boolean) req.get("active"));
            master.setKpiType(KpiMaster.KpiType.valueOf(
                    req.get("kpiType") != null ? (String) req.get("kpiType") : "NUMBER"));
            master.setTargetType(KpiMaster.TargetType.valueOf(
                    req.get("targetType") != null ? (String) req.get("targetType") : "NUMBER_TARGET"));
            String frequencyCode = req.get("frequencyCode") != null
                    ? (String) req.get("frequencyCode")
                    : (req.get("frequency") != null ? (String) req.get("frequency") : "MONTHLY");
            master.setFrequency(frequencyRepository.findByFrequencyCode(frequencyCode)
                    .orElseThrow(() -> new IllegalArgumentException("Frequency not found")));
            Long catId = ((Number) req.get("categoryId")).longValue();
            KpiCategory category = categoryRepository.findById(catId).orElseThrow(
                    () -> new IllegalArgumentException("Category not found: " + catId));
            master.setCategory(category);
            master.setRoles(resolveRoles(req.get("roleIds")));
            return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(master));
        } catch (Exception e) {
            log.error("Error creating KPI master", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiMaster> update(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        try {
            KpiMaster m = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("KPI master not found: " + id));
            if (req.containsKey("kpiName")) m.setKpiName((String) req.get("kpiName"));
            if (req.containsKey("description")) m.setDescription((String) req.get("description"));
            if (req.containsKey("measurementUnit")) m.setMeasurementUnit((String) req.get("measurementUnit"));
            if (req.containsKey("dataSource")) m.setDataSource((String) req.get("dataSource"));
            if (req.containsKey("defaultWeight"))
                m.setDefaultWeight(((Number) req.get("defaultWeight")).doubleValue());
            if (req.containsKey("active")) m.setActive((Boolean) req.get("active"));
            if (req.containsKey("kpiType"))
                m.setKpiType(KpiMaster.KpiType.valueOf((String) req.get("kpiType")));
            if (req.containsKey("targetType"))
                m.setTargetType(KpiMaster.TargetType.valueOf((String) req.get("targetType")));
            if (req.containsKey("frequencyCode") || req.containsKey("frequency")) {
                String frequencyCode = req.containsKey("frequencyCode")
                        ? (String) req.get("frequencyCode")
                        : (String) req.get("frequency");
                m.setFrequency(frequencyRepository.findByFrequencyCode(frequencyCode)
                        .orElseThrow(() -> new IllegalArgumentException("Frequency not found")));
            }
            if (req.containsKey("categoryId")) {
                Long catId = ((Number) req.get("categoryId")).longValue();
                m.setCategory(categoryRepository.findById(catId)
                        .orElseThrow(() -> new IllegalArgumentException("Category not found: " + catId)));
            }
            if (req.containsKey("roleIds")) {
                m.setRoles(resolveRoles(req.get("roleIds")));
            }
            return ResponseEntity.ok(repository.save(m));
        } catch (Exception e) {
            log.error("Error updating KPI master: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting KPI master: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    private Set<Role> resolveRoles(Object rawRoleIds) {
        if (rawRoleIds == null) {
            return new LinkedHashSet<>();
        }
        if (!(rawRoleIds instanceof List<?> ids)) {
            throw new IllegalArgumentException("roleIds must be an array of role IDs");
        }

        Set<Long> roleIds = ids.stream()
                .filter(Objects::nonNull)
                .map(value -> {
                    if (value instanceof Number n) {
                        return n.longValue();
                    }
                    return Long.parseLong(String.valueOf(value));
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (roleIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            Set<Long> foundIds = roles.stream().map(Role::getId).collect(Collectors.toSet());
            Set<Long> missing = roleIds.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toSet());
            throw new IllegalArgumentException("Invalid role IDs: " + missing);
        }

        return new LinkedHashSet<>(roles);
    }
}

