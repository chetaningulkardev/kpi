package com.example.kpi.controller;

import com.example.kpi.entity.Kpi;
import com.example.kpi.entity.User;
import com.example.kpi.metrics.MetricProvider;
import com.example.kpi.service.KpiReviewWorkflowService;
import com.example.kpi.service.MetricCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Collecting observed KPI values from connected systems.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class MetricCollectionController {

    private final MetricCollectionService metricCollectionService;
    private final KpiReviewWorkflowService workflowService;
    private final List<MetricProvider> providers;

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth != null ? auth.getName() : null;
        return workflowService.findAuthenticatedUserByEmail(email);
    }

    /**
     * Asks the bound provider for this KPI's current value.
     *
     * <p>Returns 200 with an outcome even when nothing was collected: "no
     * GitHub username is linked" is a normal answer the user can act on, not a
     * client error. Only an access violation fails the request, and that is
     * raised by the workflow service as it is everywhere else.</p>
     */
    @PostMapping("/api/kpi/{kpiId}/collect-metric")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MetricCollectionService.CollectionResult> collect(@PathVariable Long kpiId) {
        User me = currentUser();
        Kpi kpi = workflowService.resolveAccessibleKpi(kpiId, me);

        MetricCollectionService.CollectionResult result = metricCollectionService.collectFor(kpi);
        log.info("Metric collection for KPI {} by {}: {}", kpiId, me.getEmail(), result.outcome());

        return ResponseEntity.ok(result);
    }

    /**
     * Which connectors exist and whether they are usable. Lets the UI say
     * "GitHub is not connected" instead of offering a button that always fails.
     */
    @GetMapping("/api/metrics/providers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProviderStatus>> providers() {
        return ResponseEntity.ok(providers.stream()
                .map(p -> new ProviderStatus(
                        p.providerCode(),
                        p.isEnabled(),
                        p.supportedMetrics().stream().sorted().toList()))
                .sorted(Comparator.comparing(ProviderStatus::providerCode))
                .toList());
    }

    public record ProviderStatus(String providerCode, boolean enabled, List<String> supportedMetrics) {}
}

