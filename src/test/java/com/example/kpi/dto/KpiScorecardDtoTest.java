package com.example.kpi.dto;

import com.example.kpi.entity.KpiScorecard;
import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class KpiScorecardDtoTest {

    private KpiScorecard card(Double score, Double max) {
        ReviewCycle cycle = new ReviewCycle();
        cycle.setCycleCode("Q3-2026");
        cycle.setCycleName("Q3 Review");
        cycle.setStartDate(LocalDate.of(2026, 7, 1));
        cycle.setEndDate(LocalDate.of(2026, 9, 30));

        User user = new User();
        user.setId(1L);

        return KpiScorecard.builder()
                .id(10L)
                .user(user)
                .reviewCycle(cycle)
                .overallScore(score)
                .maxScore(max)
                .status(KpiScorecard.ScorecardStatus.PUBLISHED)
                .build();
    }

    @Test
    void shouldExposeCycleDatesForOrderingAndLabelling() {
        KpiScorecardDto dto = KpiScorecardDto.from(card(4.0, 5.0));

        assertEquals(LocalDate.of(2026, 7, 1), dto.cycleStartDate());
        assertEquals(LocalDate.of(2026, 9, 30), dto.cycleEndDate());
    }

    @Test
    void shouldNormaliseScoreToAPercentage() {
        assertEquals(80.0, KpiScorecardDto.from(card(4.0, 5.0)).scorePercentage());
    }

    @Test
    void shouldRoundPercentageToOneDecimal() {
        // 2/3 of the way up a 5-point scale. Left unrounded this is
        // 66.66666666666667, which renders as noise in a chart tooltip.
        assertEquals(66.7, KpiScorecardDto.from(card(10.0 / 3, 5.0)).scorePercentage());
    }

    @Test
    void shouldReportNoPercentageRatherThanZeroWhenThereIsNoScore() {
        // An unscored card is not a card scoring nothing. Zero here would
        // render as a collapse to the bottom of the chart.
        assertNull(KpiScorecardDto.from(card(null, 5.0)).scorePercentage());
    }

    @Test
    void shouldSurviveAScorecardWithNoWeightedKpis() {
        // maxScore of zero is a configuration problem, not a division to
        // attempt — it would otherwise yield Infinity or NaN and serialise
        // into JSON that the client cannot parse.
        assertNull(KpiScorecardDto.from(card(3.0, 0.0)).scorePercentage());
        assertNull(KpiScorecardDto.from(card(3.0, null)).scorePercentage());
    }

    @Test
    void shouldReturnNullForANullScorecard() {
        assertNull(KpiScorecardDto.from(null));
    }
}

