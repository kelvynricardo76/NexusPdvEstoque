package com.nexus.pdv.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.pdv.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class AuthIntegrationTest extends IntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.security.web.csrf.CookieCsrfTokenRepository csrfTokenRepository;

    @Test
    void loginReturnsSessionViewWithFeaturesAndPermissions() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        mvc.perform(get("/api/auth/me").session(tenant.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TENANT_USER"))
                .andExpect(jsonPath("$.user.email").value(tenant.adminEmail()))
                .andExpect(jsonPath("$.user.role.code").value("TENANT_ADMIN"))
                .andExpect(jsonPath("$.tenantAdmin").value(true))
                .andExpect(jsonPath("$.operable").value(true))
                .andExpect(jsonPath("$.subscription.planCode").value("PLUS"))
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.permissions[?(@ == 'FINANCIAL_READ')]").exists())
                .andExpect(jsonPath("$.limits.MAX_USERS").value(10));
    }

    @Test
    void basicPlanPermissionsExcludeUncontractedModules() throws Exception {
        TenantFixture tenant = newTenant("BASIC");
        mvc.perform(get("/api/auth/me").session(tenant.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[?(@ == 'FINANCIAL_READ')]").doesNotExist())
                .andExpect(jsonPath("$.permissions[?(@ == 'PRODUCT_READ')]").exists());
    }

    @Test
    void invalidCredentialsAreGeneric() throws Exception {
        TenantFixture tenant = newTenant("BASIC");
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", tenant.adminEmail(), "password", "errada123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "naoexiste@teste.com", "password", "errada123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void bruteForceLocksAccountTemporarily() throws Exception {
        TenantFixture tenant = newTenant("BASIC");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", tenant.adminEmail(), "password", "errada" + i))))
                    .andExpect(status().isUnauthorized());
        }
        // Mesmo com a senha correta, fica bloqueado durante a janela.
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", tenant.adminEmail(), "password", PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void unauthenticatedAccessIsRejected() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void csrfCookieIsIssuedAndRequiredForMutations() throws Exception {
        // O csrf() do spring-security-test substitui o repositório do filtro compartilhado; por isso o
        // cookie é verificado diretamente no repositório configurado pela aplicação.
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
        var cookie = response.getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isFalse();
        TenantFixture tenant = newTenant("BASIC");
        mvc.perform(post("/api/categories").session(tenant.admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Sem CSRF"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        TenantFixture tenant = newTenant("BASIC");
        mvc.perform(post("/api/auth/logout").session(tenant.admin()).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(tenant.admin())).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetFlowWithSingleUseToken() throws Exception {
        TenantFixture tenant = newTenant("BASIC");
        mvc.perform(post("/api/auth/forgot-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", tenant.adminEmail()))))
                .andExpect(status().isAccepted());
        String token = resetNotifier.tokenFor(tenant.adminEmail());
        assertThat(token).isNotBlank();

        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "newPassword", "curta"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "newPassword", "NovaSenha2026"))))
                .andExpect(status().isNoContent());
        // Token de uso único.
        mvc.perform(post("/api/auth/reset-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "newPassword", "OutraSenha2026"))))
                .andExpect(status().isBadRequest());
        login(tenant.adminEmail(), "NovaSenha2026");
    }

    @Test
    void forgotPasswordDoesNotRevealUnknownEmails() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ninguem@teste.com"))))
                .andExpect(status().isAccepted());
    }

    @Test
    void disabledUserLosesSessionImmediately() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        Employee cashier = createEmployee(tenant.admin(), "CASHIER");
        MockHttpSession cashierSession = login(cashier.email(), PASSWORD);
        mvc.perform(get("/api/products").session(cashierSession)).andExpect(status().isOk());

        mvc.perform(put("/api/users/" + cashier.id() + "/status").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/products").session(cashierSession)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", cashier.email(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeOwnPasswordRequiresCurrentPassword() throws Exception {
        TenantFixture tenant = newTenant("BASIC");
        mvc.perform(post("/api/auth/change-password").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "errada123", "newPassword", "NovaSenha2026"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/change-password").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", "NovaSenha2026"))))
                .andExpect(status().isNoContent());
        login(tenant.adminEmail(), "NovaSenha2026");
    }
}
