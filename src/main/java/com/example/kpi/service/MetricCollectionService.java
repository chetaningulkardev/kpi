package com.example.kpi.service;

import com.example.kpi.entity.Kpi;
import com.example.kpi.entity.KpiMetricBinding;
import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.entity.UserExternalIdentity;
import com.example.kpi.metrics.MetricCollectionException;
import com.example.kpi.metrics.MetricProvider;
import com.example.kpi.metrics.MetricQuery;
import com.example.kpi.metrics.MetricValue;
import com.example.kpi.repository.KPIRepository;
import com.example.kpi.repository.KpiMetricBindingRepository;
import com.example.kpi.repository.UserExternalIdentityRepository;
// Spring Boot 4.1 ships Jackson 3, whose databind lives under `tools.jackson`.
// The `com.fasterxml.jackson.databind` artifact is present but runtime-only
// (pulled in by jjwt), so it is not on the compile classpath.
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Collects an observed value for a KPI and records it as a suggestion.
 *
 * <p>The result is written to the {@code suggestedActual*} fields and never to
 * {@code actualValue}. Accepting it is an explicit act by the employee, which
 * keeps the submitted number something a person stands behind.</p>
 */
@Service
public class MetricCollectionService {

    private static final Logger log = LoggerFactory.getLogger(MetricCollectionService.class);

    private final List<MetricProvider> providers;
    private final KpiMetricBindingRepository bindingRepository;
    private final UserExternalIdentityRepository identityRepository;
    private final KPIRepository kpiRepository;
    private final ObjectMapper objectMapper;

    public MetricCollectionService(List<MetricProvider> providers,
                                   KpiMetricBindingRepository bindingRepository,
                                   UserExternalIdentityRepository identityRepository,
                                   KPIRepository kpiRepository,
                                   ObjectMapper objectMapper) {
        this.providers = providers;
        this.bindingRepository = bindingRepository;
        this.identityRepository = identityRepository;
        this.kpiRepository = kpiRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * The outcome of an attempt, including the reasons it may not have produced
     * a number. Callers show these to the user: "no GitHub username on your
     * profile" is actionable, whereas a bare empty result is not.
     */
    public enum Outcome {
        COLLECTED,
        NOT_AUTOMATED,
        PROVIDER_UNAVAILABLE,
        NO_IDENTITY,
        NO_DATA,
        FAILED
    }

    public record CollectionResult(
            Outcome outcome,
            String message,
            Double value,
            String source,
            String summary,
            String detailUrl,
            LocalDateTime collectedAt
    ) {
        static CollectionResult of(Outcome outcome, String message) {
            return new CollectionResult(outcome, message, null, null, null, null, null);
        }
    }

    /**
     * Attempts to collect this KPI's value from its bound provider.
     *
     * <p>Never throws for an unconfigured or unreachable provider — the caller
     * is a user pressing a button, and a failure to reach GitHub should read as
     * a message rather than an error page.</p>
     */
    @Transactional
    public CollectionResult collectFor(Kpi kpi) {
        Optional<KpiMetricBinding> maybeBinding =
                bindingRepository.findByKpiMasterIdAndActiveTrue(kpi.getKpiMaster().getId());

        if (maybeBinding.isEmpty()) {
            return CollectionResult.of(Outcome.NOT_AUTOMATED,
                    "This KPI is recorded manually.");
        }
        KpiMetricBinding binding = maybeBinding.get();

        Optional<MetricProvider> maybeProvider = providerFor(binding.getProviderCode());
        if (maybeProvider.isEmpty()) {
            // A binding can outlive its provider bean, e.g. after a rename.
            log.warn("KPI {} is bound to unknown provider '{}'", kpi.getId(), binding.getProviderCode());
            return CollectionResult.of(Outcome.PROVIDER_UNAVAILABLE,
                    "No connector is installed for " + binding.getProviderCode() + ".");
        }
        MetricProvider provider = maybeProvider.get();

        if (!provider.isEnabled()) {
            return CollectionResult.of(Outcome.PROVIDER_UNAVAILABLE,
                    binding.getProviderCode() + " is not connected. Ask an administrator to configure it.");
        }

        Optional<UserExternalIdentity> identity = identityRepository
                .findByUserIdAndProviderCodeAndActiveTrue(kpi.getUser().getId(), binding.getProviderCode());
        if (identity.isEmpty()) {
            return CollectionResult.of(Outcome.NO_IDENTITY,
                    "No " + binding.getProviderCode() + " username is linked to this profile.");
        }

        ReviewCycle cycle = kpi.getReviewCycle();
        MetricQuery query = new MetricQuery(
                binding.getMetricKey(),
                identity.get().getExternalHandle(),
                cycle.getStartDate(),
                effectiveEndDate(cycle),
                parseConfig(binding));

        try {
            Optional<MetricValue> value = provider.fetch(query);
            if (value.isEmpty()) {
                return CollectionResult.of(Outcome.NO_DATA,
                        "No matching activity was found for this period.");
            }
            return store(kpi, binding, value.get());

        } catch (MetricCollectionException ex) {
            // An outage must not be presented as a score of zero.
            log.warn("Metric collection failed for KPI {}: {}", kpi.getId(), ex.getMessage());
            return CollectionResult.of(Outcome.FAILED, ex.getMessage());
        }
    }

    /**
     * A cycle that has not ended yet is measured up to today; asking an external
     * system about the future returns nothing useful.
     */
    private LocalDate effectiveEndDate(ReviewCycle cycle) {
        LocalDate today = LocalDate.now();
        return cycle.getEndDate().isAfter(today) ? today : cycle.getEndDate();
    }

    private CollectionResult store(Kpi kpi, KpiMetricBinding binding, MetricValue value) {
        kpi.setSuggestedActualValue(value.value());
        kpi.setSuggestedActualSource(binding.getProviderCode());
        kpi.setSuggestedActualSummary(value.summary());
        kpi.setSuggestedActualUrl(value.detailUrl());
        kpi.setSuggestedActualAt(LocalDateTime.now());

        Kpi saved = kpiRepository.save(kpi);

        return new CollectionResult(
                Outcome.COLLECTED,
                value.summary(),
                saved.getSuggestedActualValue(),
                saved.getSuggestedActualSource(),
                saved.getSuggestedActualSummary(),
                saved.getSuggestedActualUrl(),
                saved.getSuggestedActualAt());
    }

    private Optional<MetricProvider> providerFor(String providerCode) {
        return providers.stream()
                .filter(p -> p.providerCode().equalsIgnoreCase(providerCode))
                .findFirst();
    }

    private Map<String, String> parseConfig(KpiMetricBinding binding) {
        String json = binding.getConfigJson();
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception ex) {
            // Malformed config narrows nothing rather than failing the whole
            // collection; the search simply runs unscoped.
            log.warn("Ignoring unparseable config on metric binding {}: {}", binding.getId(), ex.getMessage());
            return Map.of();
        }
    }
}

