package com.example.kpi.config;

import com.example.kpi.entity.KpiScorecard;
import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.entity.User;
import com.example.kpi.repository.KpiScorecardRepository;
import com.example.kpi.service.KpiScoringService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DemoDataSeederScorecardTest {
    private final KpiScorecardRepository scorecards = mock(KpiScorecardRepository.class);
    private final KpiScoringService scoring = mock(KpiScoringService.class);
    private final DemoDataSeeder seeder = new DemoDataSeeder(
            null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, scorecards, null, null, scoring, null);

    @Test
    void createsOnlyMissingScorecards() {
        ReviewCycle cycle = new ReviewCycle();
        User existing = new User();
        User missing = new User();
        when(scorecards.findByReviewCycleAndUser(cycle, existing)).thenReturn(Optional.of(new KpiScorecard()));
        when(scorecards.findByReviewCycleAndUser(cycle, missing)).thenReturn(Optional.empty());
        when(scoring.generateScorecard(missing, cycle, null)).thenReturn(new KpiScorecard());

        seeder.seedMissingScorecards(cycle, Map.of("existing", existing, "missing", missing));

        verify(scoring).generateScorecard(missing, cycle, null);
        verifyNoMoreInteractions(scoring);
    }

    @Test
    void failsIfMissingScorecardCannotBeGenerated() {
        ReviewCycle cycle = new ReviewCycle();
        User user = new User();
        when(scorecards.findByReviewCycleAndUser(cycle, user)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> seeder.seedMissingScorecards(cycle, Map.of("user", user)));
    }
}
