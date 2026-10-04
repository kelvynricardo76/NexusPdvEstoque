package com.nexus.pdv.shared.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.pdv.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class ActuatorAndTracingTest extends IntegrationTest {

    private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Test
    void healthIsPublicAndHasNoDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void generatesRequestIdWhenAbsent() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().string(TraceContext.HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void propagatesSafeIncomingRequestId() throws Exception {
        mvc.perform(get("/actuator/health").header(TraceContext.HEADER, "client-req-12345"))
                .andExpect(header().string(TraceContext.HEADER, "client-req-12345"));
    }

    @Test
    void replacesUnsafeIncomingRequestId() throws Exception {
        mvc.perform(get("/actuator/health").header(TraceContext.HEADER, "bad\nvalue injected"))
                .andExpect(header().string(TraceContext.HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void securityHeadersArePresentOnApiResponses() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void devToolsAreBlockedOutsideDevProfile() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().is4xxClientError());
        mvc.perform(get("/h2-console")).andExpect(status().is4xxClientError());
    }

    @Test
    void metricsRequirePlatformAdmin() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }
}
