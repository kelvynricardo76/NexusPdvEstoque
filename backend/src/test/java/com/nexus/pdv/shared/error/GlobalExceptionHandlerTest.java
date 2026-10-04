package com.nexus.pdv.shared.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.pdv.shared.web.TraceContext;
import com.nexus.pdv.support.IntegrationTest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Formato padrão de erro. O controller de prova fica sob /api/super-admin para usar um principal
 * simulado de plataforma sem depender de tenant.
 */
@Import(GlobalExceptionHandlerTest.ProbeConfig.class)
class GlobalExceptionHandlerTest extends IntegrationTest {

    private static final RequestPostProcessor ADMIN = user("probe").roles("PLATFORM_ADMIN");

    @Test
    void businessExceptionUsesStandardFormat() throws Exception {
        mvc.perform(get("/api/super-admin/test-probe/business").with(ADMIN))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").value("Este recurso não está disponível no seu plano."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.fields").doesNotExist());
    }

    @Test
    void traceIdInBodyMatchesResponseHeader() throws Exception {
        mvc.perform(get("/api/super-admin/test-probe/business").with(ADMIN).header(TraceContext.HEADER, "trace-abc-123"))
                .andExpect(header().string(TraceContext.HEADER, "trace-abc-123"))
                .andExpect(jsonPath("$.traceId").value("trace-abc-123"));
    }

    @Test
    void beanValidationReturnsFieldViolations() throws Exception {
        mvc.perform(post("/api/super-admin/test-probe/validate").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"quantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.length()").value(2))
                .andExpect(jsonPath("$.fields[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.fields[?(@.field == 'quantity')]").exists());
    }

    @Test
    void malformedJsonIsValidationError() throws Exception {
        mvc.perform(post("/api/super-admin/test-probe/validate").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownRouteIsResourceNotFound() throws Exception {
        mvc.perform(get("/api/super-admin/does-not-exist").with(ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unsupportedMethodIsReported() throws Exception {
        mvc.perform(post("/api/super-admin/test-probe/business").with(ADMIN).with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/super-admin/test-probe/validate").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"quantity\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void unexpectedErrorDoesNotLeakInternals() throws Exception {
        mvc.perform(get("/api/super-admin/test-probe/boom").with(ADMIN))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Ocorreu um erro inesperado."))
                .andExpect(content().string(not(containsString("secret-internal-detail"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at com.nexus"))));
    }

    @TestConfiguration
    static class ProbeConfig {
        @org.springframework.context.annotation.Bean
        ErrorProbeController errorProbeController() {
            return new ErrorProbeController();
        }
    }

    @RestController
    @RequestMapping("/api/super-admin/test-probe")
    static class ErrorProbeController {

        record ProbeRequest(@NotBlank String name, @Positive int quantity) {
        }

        @GetMapping("/business")
        void business() {
            throw new BusinessException(ErrorCode.FEATURE_NOT_AVAILABLE);
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody ProbeRequest request) {
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret-internal-detail");
        }
    }
}
