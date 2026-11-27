package com.example.kpi.controller;

import com.example.kpi.entity.Kpi;
import com.example.kpi.service.KPIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/kpis")
@RequiredArgsConstructor
@Slf4j
public class KPIController {

    private final KPIService kpiService;

    @GetMapping
    public ResponseEntity<List<Kpi>> getAllKPIs() {
        log.debug("Fetching all KPIs");
        return ResponseEntity.ok(kpiService.getAllKPIs());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Kpi> getKPIById(@PathVariable Long id) {
        log.debug("Fetching KPI by ID: {}", id);
        Optional<Kpi> kpi = kpiService.getKPIById(id);
        return kpi.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Kpi>> getKPIsByStatus(@PathVariable String status) {
        log.debug("Fetching KPIs by status: {}", status);
        try {
            Kpi.KpiStatus kpiStatus = Kpi.KpiStatus.valueOf(status.toUpperCase());
            return ResponseEntity.ok(kpiService.getKPIsByStatus(kpiStatus));
        } catch (IllegalArgumentException e) {
            log.error("Invalid KPI status: {}", status);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping
    public ResponseEntity<Kpi> createKPI(@RequestBody Kpi kpi) {
        log.info("Creating new KPI: {}", kpi.getKpiCode());
        try {
            Kpi savedKPI = kpiService.saveKPI(kpi);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedKPI);
        } catch (Exception e) {
            log.error("Error creating KPI", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Kpi> updateKPI(@PathVariable Long id, @RequestBody Kpi kpiDetails) {
        log.info("Updating KPI: {}", id);
        try {
            Kpi updatedKPI = kpiService.updateKPI(id, kpiDetails);
            if (updatedKPI != null) {
                return ResponseEntity.ok(updatedKPI);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error updating KPI: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKPI(@PathVariable Long id) {
        log.info("Deleting KPI: {}", id);
        try {
            kpiService.deleteKPI(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting KPI: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
