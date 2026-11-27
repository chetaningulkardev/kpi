package com.example.kpi.service;

import com.example.kpi.entity.*;
import com.example.kpi.metrics.MetricCollectionException;
import com.example.kpi.metrics.MetricProvider;
import com.example.kpi.metrics.MetricQuery;
import com.example.kpi.metrics.MetricValue;
import com.example.kpi.repository.KPIRepository;
import com.example.kpi.repository.KpiMetricBindingRepository;
import com.example.kpi.repository.UserExternalIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricCollectionServiceTest {

    @Mock private KpiMetricBindingRepository bindingRepository;
    @Mock private UserExternalIdentityRepository identityRepository;
    @Mock private KPIRepository kpiRepository;
    @Mock private MetricProvider provider;

    private MetricCollectionService service;
    private Kpi kpi;

    @BeforeEach
    void setUp() {
        when(provider.providerCode()).thenReturn("GITHUB");
        when(provider.isEnabled()).thenReturn(true);
        when(provider.supportedMetrics()).thenReturn(Set.of("pull_requests_merged"));

        service = new MetricCollectionService(
                List.of(provider), bindingRepository, identityRepository, kpiRepository, new ObjectMapper());

        kpi = buildKpi();
        when(kpiRepository.save(any(Kpi.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Kpi buildKpi() {
        User user = new User();
        user.setId(7L);
        user.setEmail("dev@example.invalid");

        KpiMaster master = new KpiMaster();
        master.setId(3L);

        ReviewCycle cycle = new ReviewCycle();
        cycle.setId(2L);
        cycle.setStartDate(LocalDate.of(2026, 1, 1));
        cycle.setEndDate(LocalDate.of(2026, 3, 31));

        Kpi k = new Kpi();
        k.setId(11L);
        k.setUser(user);
        k.setKpiMaster(master);
        k.setReviewCycle(cycle);
        k.setActualValue(99.0);
        return k;
    }

    private KpiMetricBinding binding(String configJson) {
        KpiMetricBinding b = new KpiMetricBinding();
        b.setId(1L);
        b.setKpiMaster(kpi.getKpiMaster());
        b.setProviderCode("GITHUB");
        b.setMetricKey("pull_requests_merged");
        b.setConfigJson(configJson);
        b.setActive(true);
        return b;
    }

    private void givenBindingAndIdentity(String configJson) {
        when(bindingRepository.findByKpiMasterIdAndActiveTrue(3L)).thenReturn(Optional.of(binding(configJson)));

        UserExternalIdentity identity = new UserExternalIdentity();
        identity.setExternalHandle("octocat");
        identity.setProviderCode("GITHUB");
        when(identityRepository.findByUserIdAndProviderCodeAndActiveTrue(7L, "GITHUB"))
                .thenReturn(Optional.of(identity));
    }

    private MetricValue value(double v) {
        return new MetricValue(v, "pull requests", "12 pull requests for @octocat",
                "https://github.com/search?q=x", Instant.now());
    }

    @Test
    @DisplayName("A collected value is stored as a suggestion")
    void storesSuggestion() {
        givenBindingAndIdentity(null);
        when(provider.fetch(any())).thenReturn(Optional.of(value(12)));

        var result = service.collectFor(kpi);

        assertThat(result.outcome()).isEqualTo(MetricCollectionService.Outcome.COLLECTED);
        assertThat(result.value()).isEqualTo(12.0);
        assertThat(kpi.getSuggestedActualValue()).isEqualTo(12.0);
        assertThat(kpi.getSuggestedActualSource()).isEqualTo("GITHUB");
        assertThat(kpi.getSuggestedActualAt()).isNotNull();
    }

    @Test
    @DisplayName("Collection never overwrites the employee's own actual value")
    void doesNotTouchActualValue() {
        // The whole point of the separation: an API's definition drifts and a
        // handle can be mismapped, but the person is accountable for what they
        // submitted. Accepting a suggestion has to be their act.
        givenBindingAndIdentity(null);
        when(provider.fetch(any())).thenReturn(Optional.of(value(12)));

        service.collectFor(kpi);

        assertThat(kpi.getActualValue()).isEqualTo(99.0);
    }

    @Test
    @DisplayName("A KPI with no binding is reported as manual, and no provider is called")
    void noBindingMeansManual() {
        when(bindingRepository.findByKpiMasterIdAndActiveTrue(3L)).thenReturn(Optional.empty());

        var result = service.collectFor(kpi);

        assertThat(result.outcome()).isEqualTo(MetricCollectionService.Outcome.NOT_AUTOMATED);
        verify(provider, never()).fetch(any());
        verify(kpiRepository, never()).save(any());
    }

    @Test
    @DisplayName("An unmapped user gets an actionable message, not a zero")
    void missingIdentityIsReported() {
        when(bindingRepository.findByKpiMasterIdAndActiveTrue(3L)).thenReturn(Optional.of(binding(null)));
        when(identityRepository.findByUserIdAndProviderCodeAndActiveTrue(7L, "GITHUB"))
                .thenReturn(Optional.empty());

        var result = service.collectFor(kpi);

        assertThat(result.outcome()).isEqualTo(MetricCollectionService.Outcome.NO_IDENTITY);
        assertThat(result.message()).contains("GITHUB");
        verify(provider, never()).fetch(any());
    }

    @Test
    @DisplayName("A disabled provider is reported rather than called")
    void disabledProviderIsReported() {
        givenBindingAndIdentity(null);
        when(provider.isEnabled()).thenReturn(false);

        var result = service.collectFor(kpi);

        assertThat(result.outcome()).isEqualTo(MetricCollectionService.Outcome.PROVIDER_UNAVAILABLE);
        verify(provider, never()).fetch(any());
    }

    @Test
    @DisplayName("A binding naming an uninstalled provider does not fail the request")
    void unknownProviderIsReported() {
        KpiMetricBinding orphan = binding(null);
        orphan.setProviderCode("JIRA");
        when(bindingRepository.findByKpiMasterIdAndActiveTrue(3L)).thenReturn(Optional.of(orphan));

        var result = service.collectFor(kpi);

        assertThat(result.outcome()).isEqualTo(MetricCollectionService.Outcome.PROVIDER_UNAVAILABLE);
        assertThat(result.message()).contains("JIRA");
    }

    @Test
    @DisplayName("An outage is a failure, never a suggestion of zero")
    void providerFailureIsReported() {
        givenBindingAndIdentity(null);
        when(provider.fetch(any())).thenThrow(new MetricCollectionException("Could not reach GitHub"));

        var result = service.collectFor(kpi);

        assertThat(result.outcome()).isEqualTo(MetricCollectionService.Outcome.FAILED);
        assertThat(kpi.getSuggestedActualValue()).isNull();
        verify(kpiRepository, never()).save(any());
    }

    @Test
    @DisplayName("No activity found is distinct from a failure")
    void emptyResultIsNoData() {
        givenBindingAndIdentity(null);
        when(provider.fetch(any())).thenReturn(Optional.empty());

        var result = service.collectFor(kpi);

        assertThat(result.outcome()).isEqualTo(MetricCollectionService.Outcome.NO_DATA);
        verify(kpiRepository, never()).save(any());
    }

    @Test
    @DisplayName("The query window comes from the review cycle")
    void windowComesFromCycle() {
        givenBindingAndIdentity(null);
        when(provider.fetch(any())).thenReturn(Optional.of(value(1)));

        service.collectFor(kpi);

        ArgumentCaptor<MetricQuery> captor = ArgumentCaptor.forClass(MetricQuery.class);
        verify(provider).fetch(captor.capture());
        assertThat(captor.getValue().from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(captor.getValue().to()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(captor.getValue().externalHandle()).isEqualTo("octocat");
    }

    @Test
    @DisplayName("An open cycle is measured only up to today")
    void openCycleIsMeasuredToToday() {
        // Asking an external system about the future returns nothing useful,
        // and a window ending in 2027 reads as a bug to anyone debugging it.
        kpi.getReviewCycle().setEndDate(LocalDate.now().plusMonths(6));
        givenBindingAndIdentity(null);
        when(provider.fetch(any())).thenReturn(Optional.of(value(1)));

        service.collectFor(kpi);

        ArgumentCaptor<MetricQuery> captor = ArgumentCaptor.forClass(MetricQuery.class);
        verify(provider).fetch(captor.capture());
        assertThat(captor.getValue().to()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("Binding config is passed to the provider")
    void configIsParsedAndPassed() {
        givenBindingAndIdentity("{\"org\":\"acme\"}");
        when(provider.fetch(any())).thenReturn(Optional.of(value(1)));

        service.collectFor(kpi);

        ArgumentCaptor<MetricQuery> captor = ArgumentCaptor.forClass(MetricQuery.class);
        verify(provider).fetch(captor.capture());
        assertThat(captor.getValue().config()).containsEntry("org", "acme");
    }

    @Test
    @DisplayName("Unparseable config narrows nothing rather than failing collection")
    void malformedConfigIsIgnored() {
        givenBindingAndIdentity("{not json");
        when(provider.fetch(any())).thenReturn(Optional.of(value(5)));

        var result = service.collectFor(kpi);

        assertThat(result.outcome()).isEqualTo(MetricCollectionService.Outcome.COLLECTED);
        ArgumentCaptor<MetricQuery> captor = ArgumentCaptor.forClass(MetricQuery.class);
        verify(provider).fetch(captor.capture());
        assertThat(captor.getValue().config()).isEmpty();
    }

    @Test
    @DisplayName("Provider code matching is case-insensitive")
    void providerLookupIgnoresCase() {
        KpiMetricBinding lower = binding(null);
        lower.setProviderCode("github");
        when(bindingRepository.findByKpiMasterIdAndActiveTrue(3L)).thenReturn(Optional.of(lower));
        UserExternalIdentity identity = new UserExternalIdentity();
        identity.setExternalHandle("octocat");
        when(identityRepository.findByUserIdAndProviderCodeAndActiveTrue(7L, "github"))
                .thenReturn(Optional.of(identity));
        when(provider.fetch(any())).thenReturn(Optional.of(value(3)));

        var result = service.collectFor(kpi);

        assertThat(result.outcome()).isEqualTo(MetricCollectionService.Outcome.COLLECTED);
    }

    @Test
    @DisplayName("MetricQuery config is defensively copied and never null")
    void metricQueryConfigIsSafe() {
        MetricQuery nullConfig = new MetricQuery("k", "h", LocalDate.now(), LocalDate.now(), null);
        assertThat(nullConfig.config()).isEmpty();
        assertThat(nullConfig.configValue("missing")).isNull();

        Map<String, String> mutable = new java.util.HashMap<>(Map.of("org", "acme"));
        MetricQuery copied = new MetricQuery("k", "h", LocalDate.now(), LocalDate.now(), mutable);
        mutable.put("org", "changed");
        assertThat(copied.configValue("org")).isEqualTo("acme");
    }
}

