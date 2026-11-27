package com.example.kpi.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private MockHttpServletResponse run(String inboundHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (inboundHeader != null) {
            request.addHeader(CorrelationIdFilter.HEADER, inboundHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response;
    }

    @Test
    @DisplayName("Generates an id when the caller supplies none")
    void generatesIdWhenAbsent() throws Exception {
        MockHttpServletResponse response = run(null);
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
    }

    @Test
    @DisplayName("Reuses a well-formed inbound id so a call chain stays traceable")
    void reusesInboundId() throws Exception {
        MockHttpServletResponse response = run("abc123-request-id");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc123-request-id");
    }

    @Test
    @DisplayName("Rejects an id containing a newline, which would allow log injection")
    void rejectsNewlineInInboundId() throws Exception {
        // The value lands in every log line for the request; a newline would let
        // the caller forge entries.
        MockHttpServletResponse response = run("good\nFAKE ERROR forged log line");

        assertThat(response.getHeader(CorrelationIdFilter.HEADER))
                .doesNotContain("FAKE ERROR")
                .doesNotContain("\n");
    }

    @Test
    @DisplayName("Rejects an over-long inbound id")
    void rejectsOverlongInboundId() throws Exception {
        String tooLong = "a".repeat(200);
        assertThat(run(tooLong).getHeader(CorrelationIdFilter.HEADER)).isNotEqualTo(tooLong);
    }

    @Test
    @DisplayName("Clears the MDC afterwards so a pooled thread does not leak the id")
    void clearsMdcAfterRequest() throws Exception {
        run("abc123");

        // Servlet threads are reused; a stale value would mislabel the next
        // unrelated request.
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Clears the MDC even when the chain throws")
    void clearsMdcOnFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain exploding = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        try {
            filter.doFilter(request, response, exploding);
        } catch (Exception expected) {
            // The failure itself is not what is being tested.
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}

