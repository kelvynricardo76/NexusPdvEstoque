package com.nexus.pdv.platform;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.pdv.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class SuperAdminTest extends IntegrationTest {

    @Test
    void superAdminCreatesTenantWhoseAdminCanLogIn() throws Exception {
        MockHttpSession root = superAdmin();
        String adminEmail = unique("novo") + "@cliente.com";
        MvcResult created = mvc.perform(post("/api/super-admin/tenants").session(root).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Papelaria Delta", "planCode", "BASIC", "trialDays", 14,
                                "admin", Map.of("name", "Dona Delta", "email", adminEmail, "password", PASSWORD)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TRIAL"))
                .andExpect(jsonPath("$.subscription.planCode").value("BASIC"))
                .andExpect(jsonPath("$.usage.activeUsers").value(1))
                .andReturn();
        String tenantId = read(created, "$.id");

        MockHttpSession admin = login(adminEmail, PASSWORD);
        mvc.perform(get("/api/auth/me").session(admin)).andExpect(jsonPath("$.tenant.id").value(tenantId));

        mvc.perform(get("/api/super-admin/tenants").param("q", "Papelaria Delta").session(root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(tenantId));
        mvc.perform(get("/api/super-admin/tenants/" + tenantId + "/users").session(root))
                .andExpect(jsonPath("$[0].email").value(adminEmail));
        mvc.perform(get("/api/super-admin/tenants/" + tenantId + "/audit").session(root))
                .andExpect(jsonPath("$.content[?(@.action == 'TENANT_CREATE')]").exists());
    }

    @Test
    void duplicateAdminEmailIsRejected() throws Exception {
        TenantFixture existing = newTenant("BASIC");
        mvc.perform(post("/api/super-admin/tenants").session(superAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Outra", "planCode", "BASIC",
                                "admin", Map.of("name", "X", "email", existing.adminEmail(), "password", PASSWORD)))))
                .andExpect(status().isConflict());
    }

    @Test
    void changingPlanChangesEntitlementsImmediately() throws Exception {
        TenantFixture tenant = newTenant("BASIC");
        mvc.perform(get("/api/financial/summary").session(tenant.admin())).andExpect(status().isForbidden());
        mvc.perform(put("/api/super-admin/tenants/" + tenant.tenantId() + "/plan").session(superAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("planCode", "PLUS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.planCode").value("PLUS"));
        mvc.perform(get("/api/financial/summary").session(tenant.admin())).andExpect(status().isOk());
    }

    @Test
    void planEditorRequiresConfirmationWhenReducingRightsOfTenantsInUse() throws Exception {
        MockHttpSession root = superAdmin();
        String code = "T" + unique("P").replaceAll("[^A-Z0-9]", "").toUpperCase();
        code = code.length() > 30 ? code.substring(0, 30) : code;
        Map<String, Object> plan = Map.of("code", code, "name", "Teste", "monthlyPrice", new BigDecimal("10.00"),
                "annualPrice", new BigDecimal("100.00"), "active", true, "displayOrder", 99,
                "features", Set.of("PDV", "SALES", "PRODUCTS", "STOCK", "REPORTS"),
                "limits", Map.of("MAX_USERS", 2, "MAX_PRODUCTS", 50));
        MvcResult created = mvc.perform(post("/api/super-admin/plans").session(root).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(plan)))
                .andExpect(status().isCreated()).andReturn();
        String planId = read(created, "$.id");

        TenantFixture tenant = newTenant("BASIC");
        mvc.perform(put("/api/super-admin/tenants/" + tenant.tenantId() + "/plan").session(root).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("planCode", code))))
                .andExpect(status().isOk());

        Map<String, Object> reduced = new java.util.HashMap<>(plan);
        reduced.put("features", Set.of("PDV", "SALES", "PRODUCTS", "STOCK"));
        mvc.perform(put("/api/super-admin/plans/" + planId).session(root).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(reduced)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFIRMATION_REQUIRED"));
        mvc.perform(put("/api/super-admin/plans/" + planId).param("confirmImpact", "true").session(root).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(reduced)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantCount").value(1));
        mvc.perform(get("/api/reports/SALES").session(tenant.admin()))
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"));
    }

    @Test
    void dashboardAndCatalogEndpointsWork() throws Exception {
        newTenant("PLUS");
        MockHttpSession root = superAdmin();
        mvc.perform(get("/api/super-admin/dashboard").session(root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTenants").isNumber())
                .andExpect(jsonPath("$.monthlyRecurringRevenue").isNumber())
                .andExpect(jsonPath("$.planDistribution[?(@.planCode == 'PLUS')]").exists());
        mvc.perform(get("/api/super-admin/features").session(root))
                .andExpect(jsonPath("$[?(@.code == 'FINANCIAL')].plans[0]").value("PLUS"));
        mvc.perform(get("/api/super-admin/limits").session(root)).andExpect(jsonPath("$.length()").value(6));
        mvc.perform(get("/api/super-admin/subscriptions").param("planCode", "PLUS").session(root))
                .andExpect(status().isOk());
    }

    @Test
    void billingInvoicePaymentReactivatesSubscriptionAndIsIdempotent() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        MockHttpSession root = superAdmin();
        mvc.perform(put("/api/super-admin/tenants/" + tenant.tenantId() + "/subscription").session(root).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "PAST_DUE", "cancelAtPeriodEnd", false))))
                .andExpect(status().isOk());
        MvcResult invoice = mvc.perform(post("/api/super-admin/billing/invoices").session(root).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("tenantId", tenant.tenantId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(149.90))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String invoiceId = read(invoice, "$.id");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/super-admin/billing/invoices/" + invoiceId + "/pay").session(root).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAID"));
        }
        mvc.perform(get("/api/super-admin/tenants/" + tenant.tenantId()).session(root))
                .andExpect(jsonPath("$.subscription.status").value("ACTIVE"));
        // Billing não aparece no financeiro do cliente.
        mvc.perform(get("/api/financial/entries").session(tenant.admin()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void superAdminCannotDisableOwnAccessAndCanManageAdmins() throws Exception {
        MockHttpSession root = superAdmin();
        MvcResult me = mvc.perform(get("/api/super-admin/auth/me").session(root)).andReturn();
        String myId = read(me, "$.id");
        mvc.perform(put("/api/super-admin/admins/" + myId + "/status").session(root).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/super-admin/admins").session(root).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Operador", "email", unique("op") + "@nexus.dev",
                                "password", "Senha12345"))))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/super-admin/admins").session(root)).andExpect(jsonPath("$.length()").isNumber());
    }

    @Test
    void platformSettingsAndAudit() throws Exception {
        MockHttpSession root = superAdmin();
        mvc.perform(put("/api/super-admin/settings").session(root).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("defaultTrialDays", 21, "pastDueGraceDays", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultTrialDays").value(21));
        mvc.perform(get("/api/super-admin/audit").param("action", "LOGIN").session(root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mvc.perform(put("/api/super-admin/settings").session(root).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("defaultTrialDays", 14, "pastDueGraceDays", 7))));
    }

    @Test
    void tenantUserCannotLogIntoSuperAdmin() throws Exception {
        TenantFixture tenant = newTenant("BASIC");
        mvc.perform(post("/api/super-admin/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", tenant.adminEmail(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/super-admin/dashboard").session(tenant.admin())).andExpect(status().isForbidden());
    }
}
