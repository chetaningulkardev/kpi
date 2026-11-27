package com.example.kpi.service;

import com.example.kpi.entity.Kpi;
import com.example.kpi.repository.KPIRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * KPI Service - provides basic KPI operations and queries
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class KPIService {

    private final KPIRepository kpiRepository;

    public List<Kpi> getAllKPIs() {
        log.debug("Fetching all KPIs");
        return kpiRepository.findAll();
    }

    public Optional<Kpi> getKPIById(Long id) {
        if (id == null || id <= 0) {
            log.error("Invalid KPI ID: {}", id);
            return Optional.empty();
        }
        log.debug("Fetching KPI by ID: {}", id);
        return kpiRepository.findById(id);
    }

    public List<Kpi> getKPIsByStatus(Kpi.KpiStatus status) {
        if (status == null) {
            log.error("Status cannot be null");
            throw new IllegalArgumentException("Status cannot be null");
        }
        log.debug("Fetching KPIs by status: {}", status);
        return kpiRepository.findByStatus(status);
    }

    public Kpi saveKPI(Kpi kpi) {
        if (kpi == null) {
            log.error("KPI cannot be null");
            throw new IllegalArgumentException("KPI cannot be null");
        }
        log.info("Saving KPI: {}", kpi.getKpiCode());
        return kpiRepository.save(kpi);
    }

    public void deleteKPI(Long id) {
        if (id == null || id <= 0) {
            log.error("Invalid KPI ID: {}", id);
            throw new IllegalArgumentException("KPI ID must be a positive number");
        }
        log.info("Deleting KPI: {}", id);
        kpiRepository.deleteById(id);
    }

    public Kpi updateKPI(Long id, Kpi kpiDetails) {
        if (id == null || id <= 0) {
            log.error("Invalid KPI ID: {}", id);
            throw new IllegalArgumentException("KPI ID must be a positive number");
        }
        if (kpiDetails == null) {
            log.error("KPI details cannot be null");
            throw new IllegalArgumentException("KPI details cannot be null");
        }

        log.info("Updating KPI: {}", id);

        Optional<Kpi> kpi = kpiRepository.findById(id);
        if (kpi.isPresent()) {
            Kpi existingKPI = kpi.get();
            if (kpiDetails.getKpiName() != null) {
                existingKPI.setKpiName(kpiDetails.getKpiName());
            }
            if (kpiDetails.getComments() != null) {
                existingKPI.setComments(kpiDetails.getComments());
            }
            if (kpiDetails.getActualValue() != null) {
                existingKPI.setActualValue(kpiDetails.getActualValue());
            }
            if (kpiDetails.getTarget() != null) {
                existingKPI.setTarget(kpiDetails.getTarget());
            }
            if (kpiDetails.getStatus() != null) {
                existingKPI.setStatus(kpiDetails.getStatus());
            }
            return kpiRepository.save(existingKPI);
        }
        log.warn("KPI not found for update: {}", id);
        return null;
    }
}

