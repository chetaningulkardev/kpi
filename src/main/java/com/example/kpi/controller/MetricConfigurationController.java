package com.example.kpi.controller;

import com.example.kpi.entity.KpiMaster;
import com.example.kpi.entity.KpiMetricBinding;
import com.example.kpi.entity.User;
import com.example.kpi.entity.UserExternalIdentity;
import com.example.kpi.metrics.MetricProvider;
import com.example.kpi.repository.KpiMasterRepository;
import com.example.kpi.repository.KpiMetricBindingRepository;
import com.example.kpi.repository.UserExternalIdentityRepository;
import com.example.kpi.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Administration of automated measurement: which KPI is measured by which
 * provider, and who each person is in that provider's system.
 */
@RestController
@RequestMapping("/api/config/metrics")
@RequiredArgsConstructor
@Slf4j
public class MetricConfigurationController {

    private final KpiMetricBindingRepository bindingRepository;
    private final UserExternalIdentityRepository identityRepository;
    private final KpiMasterRepository kpiMasterRepository;
    private final UserRepository userRepository;
    private final List<MetricProvider> providers;

    // ── Bindings ────────────────────────────────────────────────────────────

    @GetMapping("/bindings")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMINISTRATOR')")
    public ResponseEntity<List<BindingDto>> listBindings() {
        return ResponseEntity.ok(bindingRepository.findAll().stream().map(BindingDto::from).toList());
    }

    @PostMapping("/bindings")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMINISTRATOR')")
    public ResponseEntity<BindingDto> upsertBinding(@Valid @RequestBody BindingRequest request) {
        KpiMaster master = kpiMasterRepository.findById(request.kpiMasterId())
                .orElseThrow(() -> new IllegalArgumentException("No such KPI: " + request.kpiMasterId()));

        validateProviderAndMetric(request.providerCode(), request.metricKey());

        // Upsert rather than insert: a KPI may have only one binding, so a
        // second POST is plainly an edit rather than a mistake.
        KpiMetricBinding binding = bindingRepository.findByKpiMasterId(master.getId())
                .orElseGet(KpiMetricBinding::new);

        binding.setKpiMaster(master);
        binding.setProviderCode(request.providerCode().toUpperCase());
        binding.setMetricKey(request.metricKey());
        binding.setConfigJson(request.configJson());
        binding.setActive(request.active() == null || request.active());

        return ResponseEntity.ok(BindingDto.from(bindingRepository.save(binding)));
    }

    @DeleteMapping("/bindings/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMINISTRATOR')")
    public ResponseEntity<Void> deleteBinding(@PathVariable Long id) {
        bindingRepository.deleteById(id);
        // Removing a binding returns the KPI to manual entry; previously
        // collected suggestions are left alone as a record of what was seen.
        return ResponseEntity.noContent().build();
    }

    /**
     * Rejects a binding that names a provider or metric nothing can answer.
     * Without this the mistake surfaces months later, as an empty result on the
     * one day someone needs the number.
     */
    private void validateProviderAndMetric(String providerCode, String metricKey) {
        Optional<MetricProvider> provider = providers.stream()
                .filter(p -> p.providerCode().equalsIgnoreCase(providerCode))
                .findFirst();

        if (provider.isEmpty()) {
            throw new IllegalArgumentException("No connector is installed for " + providerCode);
        }
        if (!provider.get().supportedMetrics().contains(metricKey)) {
            throw new IllegalArgumentException(
                    providerCode + " does not measure '" + metricKey + "'. Supported: "
                            + String.join(", ", provider.get().supportedMetrics()));
        }
    }

    // ── External identities ─────────────────────────────────────────────────

    @GetMapping("/identities/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMINISTRATOR')")
    public ResponseEntity<List<IdentityDto>> listIdentities(@PathVariable Long userId) {
        return ResponseEntity.ok(identityRepository.findByUserId(userId).stream().map(IdentityDto::from).toList());
    }

    @PostMapping("/identities")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMINISTRATOR')")
    public ResponseEntity<IdentityDto> upsertIdentity(@Valid @RequestBody IdentityRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("No such user: " + request.userId()));

        String providerCode = request.providerCode().toUpperCase();

        UserExternalIdentity identity = identityRepository
                .findByUserIdAndProviderCodeAndActiveTrue(user.getId(), providerCode)
                .orElseGet(UserExternalIdentity::new);

        identity.setUser(user);
        identity.setProviderCode(providerCode);
        identity.setExternalHandle(request.externalHandle().trim());
        identity.setActive(request.active() == null || request.active());

        log.info("Linked {} identity '{}' to user {}", providerCode, identity.getExternalHandle(), user.getId());
        return ResponseEntity.ok(IdentityDto.from(identityRepository.save(identity)));
    }

    @DeleteMapping("/identities/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMINISTRATOR')")
    public ResponseEntity<Void> deleteIdentity(@PathVariable Long id) {
        identityRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Payloads ────────────────────────────────────────────────────────────

    public record BindingRequest(
            @NotNull Long kpiMasterId,
            @NotBlank String providerCode,
            @NotBlank String metricKey,
            String configJson,
            Boolean active) {}

    public record BindingDto(
            Long id, Long kpiMasterId, String kpiName, String providerCode,
            String metricKey, String configJson, Boolean active) {

        static BindingDto from(KpiMetricBinding b) {
            return new BindingDto(
                    b.getId(),
                    b.getKpiMaster() != null ? b.getKpiMaster().getId() : null,
                    b.getKpiMaster() != null ? b.getKpiMaster().getKpiName() : null,
                    b.getProviderCode(), b.getMetricKey(), b.getConfigJson(), b.getActive());
        }
    }

    public record IdentityRequest(
            @NotNull Long userId,
            @NotBlank String providerCode,
            @NotBlank String externalHandle,
            Boolean active) {}

    public record IdentityDto(Long id, Long userId, String providerCode, String externalHandle, Boolean active) {

        static IdentityDto from(UserExternalIdentity i) {
            return new IdentityDto(
                    i.getId(),
                    i.getUser() != null ? i.getUser().getId() : null,
                    i.getProviderCode(), i.getExternalHandle(), i.getActive());
        }
    }
}

