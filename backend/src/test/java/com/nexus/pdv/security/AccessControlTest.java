package com.nexus.pdv.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.pdv.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/**
 * Regra de acesso efetivo: TENANT ATIVO + ASSINATURA VÁLIDA + FEATURE + PERMISSÃO.
 * Cobre permissões (seção 54), planos/features (55), limites (56) e escalonamento (57).
 */
class AccessControlTest extends IntegrationTest {

    // ---------- 54. Permissões ----------

    @Test
    void employeeWithoutPermissionGets403UntilAdminGrantsIt() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        Employee cashier = createEmployee(tenant.admin(), "CASHIER");
        MockHttpSession session = login(cashier.email(), PASSWORD);

        mvc.perform(get("/api/financial/summary").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(put("/api/users/" + cashier.id() + "/permissions").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("overrides", Map.of("FINANCIAL_READ", "ALLOW")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectivePermissions[?(@ == 'FINANCIAL_READ')]").exists());

        mvc.perform(get("/api/financial/summary").session(session)).andExpect(status().isOk());
    }

    @Test
    void denyOverrideWinsOverRolePermission() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        Employee cashier = createEmployee(tenant.admin(), "CASHIER");
        MockHttpSession session = login(cashier.email(), PASSWORD);
        mvc.perform(get("/api/products").session(session)).andExpect(status().isOk());

        mvc.perform(put("/api/users/" + cashier.id() + "/permissions").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("overrides", Map.of("PRODUCT_READ", "DENY", "PDV_ACCESS", "DENY")))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void cashierCannotCancelSalesOrSeeCost() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        var product = createProduct(tenant.admin(), "Produto", "5");
        MockHttpSession cashier = newEmployee(tenant.admin(), "CASHIER");
        var sale = sell(cashier, product, "1", null);
        String saleId = read(sale, "$.id");
        mvc.perform(post("/api/sales/" + saleId + "/cancel").session(cashier).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Teste"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/products").session(cashier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].costPrice").doesNotExist());
        mvc.perform(get("/api/products").session(tenant.admin()))
                .andExpect(jsonPath("$.content[0].costPrice").exists());
    }

    // ---------- 55. Planos e features ----------

    @Test
    void basicPlanCannotUseAdvancedReportsButPlusCan() throws Exception {
        TenantFixture basic = newTenant("BASIC");
        mvc.perform(get("/api/reports/TOP_PRODUCTS").session(basic.admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"));
        TenantFixture plus = newTenant("PLUS");
        mvc.perform(get("/api/reports/TOP_PRODUCTS").session(plus.admin())).andExpect(status().isOk());
    }

    @Test
    void permissionWithoutFeatureIsBlocked() throws Exception {
        // Admin do BASIC possui (pelo cargo) todas as permissões, mas o tenant não contratou FINANCIAL.
        TenantFixture basic = newTenant("BASIC");
        mvc.perform(get("/api/financial/summary").session(basic.admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"));
        mvc.perform(get("/api/suppliers").session(basic.admin()))
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"));
    }

    @Test
    void featureWithoutPermissionIsBlocked() throws Exception {
        TenantFixture plus = newTenant("PLUS");
        MockHttpSession stockOperator = newEmployee(plus.admin(), "STOCK_OPERATOR");
        mvc.perform(get("/api/reports/TOP_PRODUCTS").session(stockOperator))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post("/api/sales").session(stockOperator).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminFeatureOverrideGrantsFeatureWithoutChangingPlan() throws Exception {
        TenantFixture basic = newTenant("BASIC");
        MockHttpSession root = superAdmin();
        mvc.perform(put("/api/super-admin/tenants/" + basic.tenantId() + "/feature-overrides").session(root).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("featureCode", "ADVANCED_REPORTS", "enabled", true))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reports/TOP_PRODUCTS").session(basic.admin())).andExpect(status().isOk());

        mvc.perform(put("/api/super-admin/tenants/" + basic.tenantId() + "/feature-overrides").session(root).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new java.util.HashMap<>(Map.of("featureCode", "ADVANCED_REPORTS")))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reports/TOP_PRODUCTS").session(basic.admin())).andExpect(status().isForbidden());
    }

    // ---------- 56. Limites ----------

    @Test
    void userLimitIsEnforcedAndOverrideRaisesIt() throws Exception {
        TenantFixture basic = newTenant("BASIC"); // MAX_USERS = 3 (admin já conta)
        createEmployee(basic.admin(), "CASHIER");
        createEmployee(basic.admin(), "CASHIER");
        String roleId = roleId(basic.admin(), "CASHIER");
        Map<String, Object> fourth = Map.of("name", "Quarto", "email", unique("quarto") + "@teste.com",
                "roleId", roleId, "password", PASSWORD);
        mvc.perform(post("/api/users").session(basic.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(fourth)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));

        mvc.perform(put("/api/super-admin/tenants/" + basic.tenantId() + "/limit-overrides").session(superAdmin())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("limitCode", "MAX_USERS", "value", 5))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/users").session(basic.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(fourth)))
                .andExpect(status().isCreated());
    }

    @Test
    void productLimitIsEnforced() throws Exception {
        TenantFixture basic = newTenant("BASIC");
        mvc.perform(put("/api/super-admin/tenants/" + basic.tenantId() + "/limit-overrides").session(superAdmin())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("limitCode", "MAX_PRODUCTS", "value", 1))))
                .andExpect(status().isOk());
        createProduct(basic.admin(), "Único", "1");
        mvc.perform(post("/api/products").session(basic.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Excedente", "salePrice", 1, "unit", "UN"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
    }

    // ---------- 57. Escalonamento de privilégio ----------

    @Test
    void tenantAdminCannotReachSuperAdminApi() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        mvc.perform(get("/api/super-admin/tenants").session(tenant.admin())).andExpect(status().isForbidden());
        mvc.perform(post("/api/super-admin/admins").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Hacker", "email", "h@x.com", "password", "Senha12345"))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/super-admin/tenants/" + tenant.tenantId() + "/plan").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("planCode", "PLUS"))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/super-admin/tenants/" + tenant.tenantId() + "/feature-overrides").session(tenant.admin())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("featureCode", "MULTI_BRANCH", "enabled", true))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotGrantPermissionsOfUncontractedModules() throws Exception {
        TenantFixture basic = newTenant("BASIC");
        mvc.perform(post("/api/roles").session(basic.admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Financeiro", "permissions", List.of("FINANCIAL_READ")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"));
        Employee cashier = createEmployee(basic.admin(), "CASHIER");
        mvc.perform(put("/api/users/" + cashier.id() + "/permissions").session(basic.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("overrides", Map.of("SUPPLIER_READ", "ALLOW")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"));
    }

    @Test
    void delegatedManagerCannotEscalate() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        // Cargo com poder de gestão de usuários, mas sem acesso financeiro.
        mvc.perform(post("/api/roles").session(tenant.admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Supervisor", "permissions",
                                List.of("USER_READ", "USER_CREATE", "USER_UPDATE", "USER_PERMISSION_MANAGE", "PRODUCT_READ")))))
                .andExpect(status().isCreated());
        var roles = mvc.perform(get("/api/roles").session(tenant.admin())).andReturn();
        List<String> ids = read(roles, "$[?(@.name == 'Supervisor')].id");
        String supervisorRole = ids.get(0);
        String email = unique("supervisor") + "@teste.com";
        mvc.perform(post("/api/users").session(tenant.admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Supervisor", "email", email, "roleId", supervisorRole,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        MockHttpSession supervisor = login(email, PASSWORD);

        // Não pode conceder o cargo Administrador.
        mvc.perform(post("/api/users").session(supervisor).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Novo Admin", "email", unique("x") + "@teste.com",
                                "roleId", roleId(tenant.admin(), "TENANT_ADMIN"), "password", PASSWORD))))
                .andExpect(status().isForbidden());
        // Não pode criar cargo com permissões que não possui.
        mvc.perform(post("/api/roles").session(supervisor).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Poderoso", "permissions", List.of("FINANCIAL_READ")))))
                .andExpect(status().isForbidden());
        // Não pode alterar o próprio acesso.
        var me = mvc.perform(get("/api/auth/me").session(supervisor)).andReturn();
        String myId = read(me, "$.user.id");
        mvc.perform(put("/api/users/" + myId + "/permissions").session(supervisor).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("overrides", Map.of("FINANCIAL_READ", "ALLOW")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void lastActiveAdminCannotBeRemovedAndAdminCannotDisableSelf() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        var me = mvc.perform(get("/api/auth/me").session(tenant.admin())).andReturn();
        String adminId = read(me, "$.user.id");
        mvc.perform(put("/api/users/" + adminId + "/status").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/roles/" + roleId(tenant.admin(), "TENANT_ADMIN")).session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Admin", "permissions", List.of()))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------- Tenant suspenso ----------

    @Test
    void suspendedTenantCannotOperateUntilReactivated() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        MockHttpSession root = superAdmin();
        mvc.perform(post("/api/super-admin/tenants/" + tenant.tenantId() + "/status").session(root).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("action", "SUSPEND", "reason", "Inadimplência"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mvc.perform(get("/api/products").session(tenant.admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
        mvc.perform(get("/api/auth/me").session(tenant.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operable").value(false))
                .andExpect(jsonPath("$.blockReason").value("TENANT_SUSPENDED"));

        mvc.perform(post("/api/super-admin/tenants/" + tenant.tenantId() + "/status").session(root).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("action", "REACTIVATE"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products").session(tenant.admin())).andExpect(status().isOk());
    }

    @Test
    void expiredTrialBlocksOperation() throws Exception {
        TenantFixture tenant = newTenant("BASIC");
        mvc.perform(put("/api/super-admin/tenants/" + tenant.tenantId() + "/subscription").session(superAdmin())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "TRIAL", "trialEndDate",
                                java.time.LocalDate.now().minusDays(2).toString(), "cancelAtPeriodEnd", false))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products").session(tenant.admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"));
    }
}
