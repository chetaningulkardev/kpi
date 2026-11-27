package com.example.kpi.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards the optional filters on {@link NotificationRepository#searchByRecipient}.
 * <p>
 * Every filter in that query is nullable, and PostgreSQL cannot infer the type
 * of a bind parameter that only ever appears in {@code ? is null}. Without an
 * explicit cast it rejects the whole statement — either with "could not
 * determine data type of parameter", or, once it guesses {@code bytea} for the
 * argument of {@code upper()}, with "function upper(bytea) does not exist".
 * <p>
 * That made the notification list and the header's bell dropdown fail with a
 * 400 for the unfiltered request the UI issues by default, so these cases walk
 * each filter through both its null and non-null form. The assertion is that
 * the query executes at all; the filtering semantics are covered elsewhere.
 * Note that this has to run against a real PostgreSQL — the bug is specific to
 * its parameter type inference and would not reproduce on H2.
 */
@SpringBootTest
class NotificationRepositorySearchTest {

    private static final long NO_SUCH_RECIPIENT = -1L;

    @Autowired
    private NotificationRepository notificationRepository;

    private void search(Boolean read, String type, LocalDateTime fromAt, LocalDateTime toAt) {
        notificationRepository.searchByRecipient(
                NO_SUCH_RECIPIENT,
                read,
                type,
                fromAt,
                toAt,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdDate"))
        );
    }

    @Test
    @DisplayName("an unfiltered search runs — this is what the UI asks for by default")
    void allFiltersOmitted() {
        assertThatCode(() -> search(null, null, null, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("each filter runs on its own, whether supplied or omitted")
    void eachFilterIndependently() {
        assertThatCode(() -> search(true, null, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> search(false, null, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> search(null, "KPI_SUBMITTED", null, null)).doesNotThrowAnyException();
        assertThatCode(() -> search(null, null, LocalDateTime.now().minusYears(1), null))
                .doesNotThrowAnyException();
        assertThatCode(() -> search(null, null, null, LocalDateTime.now().plusYears(1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("all filters supplied together still runs")
    void allFiltersSupplied() {
        assertThatCode(() -> search(
                false,
                "KPI_SUBMITTED",
                LocalDateTime.now().minusYears(1),
                LocalDateTime.now().plusYears(1)
        )).doesNotThrowAnyException();
    }
}

