package com.nexus.pdv.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.pdv.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * TESTE CRÍTICO DE TENANT: dados do Tenant A são invisíveis e intocáveis para o Tenant B,
 * inclusive por IDs indiretos em relacionamentos.
 */
class TenantIsolationTest extends IntegrationTest {

    private TenantFixture tenantA;
    private TenantFixture tenantB;
    private UUID productA;

    @BeforeEach
    void setUp() throws Exception {
        tenantA = newTenant("PLUS");
        tenantB = newTenant("PLUS");
        productA = createProduct(tenantA.admin(), "Produto do Tenant A", "10");
    }

    @Test
    void tenantBCannotReadProductOfTenantA() throws Exception {
        mvc.perform(get("/api/products/" + productA).session(tenantB.admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        MvcResult list = mvc.perform(get("/api/products").session(tenantB.admin())).andExpect(status().isOk()).andReturn();
        List<String> ids = read(list, "$.content[*].id");
        assertThat(ids).doesNotContain(productA.toString());
    }

    @Test
    void tenantBCannotUpdateOrDisableProductOfTenantA() throws Exception {
        mvc.perform(put("/api/products/" + productA).session(tenantB.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Invadido", "salePrice", 1, "unit", "UN"))))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/products/" + productA + "/active").session(tenantB.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/products/" + productA).session(tenantA.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Produto do Tenant A"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void tenantBCannotSellProductOfTenantA() throws Exception {
        MvcResult result = sell(tenantB.admin(), productA, "1", null);
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        mvc.perform(get("/api/products/" + productA).session(tenantA.admin()))
                .andExpect(jsonPath("$.currentStock").value(10.0));
    }

    @Test
    void tenantBCannotChangeStockOfTenantA() throws Exception {
        mvc.perform(post("/api/stock/entries").session(tenantB.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("productId", productA, "quantity", 5))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/stock/adjustments").session(tenantB.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("productId", productA, "newQuantity", 0, "reason", "Invasão"))))
                .andExpect(status().isNotFound());
        MvcResult movements = mvc.perform(get("/api/stock/movements").param("productId", productA.toString())
                        .session(tenantB.admin()))
                .andExpect(status().isOk()).andReturn();
        List<Object> content = read(movements, "$.content");
        assertThat(content).isEmpty();
    }

    @Test
    void indirectIdsFromAnotherTenantAreRejected() throws Exception {
        MvcResult category = mvc.perform(post("/api/categories").session(tenantA.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "Categoria A"))))
                .andExpect(status().isCreated()).andReturn();
        String categoryA = read(category, "$.id");
        // Produto do B apontando para categoria do A.
        mvc.perform(post("/api/products").session(tenantB.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "X", "salePrice", 1, "unit", "UN", "categoryId", categoryA))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        MvcResult customer = mvc.perform(post("/api/customers").session(tenantA.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "Cliente A"))))
                .andExpect(status().isCreated()).andReturn();
        String customerA = read(customer, "$.id");
        UUID productB = createProduct(tenantB.admin(), "Produto B", "5");
        // Venda do B com cliente do A.
        mvc.perform(post("/api/sales").session(tenantB.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("customerId", customerA,
                                "items", List.of(Map.of("productId", productB, "quantity", 1)),
                                "payments", List.of(Map.of("method", "PIX", "amount", new BigDecimal("10.00")))))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/customers/" + customerA).session(tenantB.admin())).andExpect(status().isNotFound());
    }

    @Test
    void salesUsersFinancialAndAuditAreIsolated() throws Exception {
        MvcResult sale = sell(tenantA.admin(), productA, "1", null);
        assertThat(sale.getResponse().getStatus()).isEqualTo(201);
        String saleA = read(sale, "$.id");
        mvc.perform(get("/api/sales/" + saleA).session(tenantB.admin())).andExpect(status().isNotFound());
        mvc.perform(get("/api/sales/" + saleA + "/receipt").session(tenantB.admin())).andExpect(status().isNotFound());
        mvc.perform(post("/api/sales/" + saleA + "/cancel").session(tenantB.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Tentativa"))))
                .andExpect(status().isNotFound());

        Employee employeeA = createEmployee(tenantA.admin(), "CASHIER");
        mvc.perform(get("/api/users/" + employeeA.id()).session(tenantB.admin())).andExpect(status().isNotFound());
        mvc.perform(put("/api/users/" + employeeA.id() + "/status").session(tenantB.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isNotFound());

        MvcResult entry = mvc.perform(post("/api/financial/entries").session(tenantA.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "PAYABLE", "description", "Aluguel A", "amount", 100,
                                "dueDate", java.time.LocalDate.now().toString()))))
                .andExpect(status().isCreated()).andReturn();
        mvc.perform(get("/api/financial/entries/" + read(entry, "$.id")).session(tenantB.admin()))
                .andExpect(status().isNotFound());

        MvcResult audit = mvc.perform(get("/api/audit").session(tenantB.admin())).andExpect(status().isOk()).andReturn();
        List<String> tenants = read(audit, "$.content[*].tenantId");
        assertThat(tenants).allMatch(id -> id.equals(tenantB.tenantId().toString()));
    }

    @Test
    void tenantIdSentByClientIsIgnored() throws Exception {
        // Mesmo enviando tenantId de outro tenant, o registro é criado no tenant da sessão.
        MvcResult created = mvc.perform(post("/api/categories").session(tenantB.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Cat B", "tenantId", tenantA.tenantId()))))
                .andExpect(status().isCreated()).andReturn();
        String id = read(created, "$.id");
        mvc.perform(get("/api/categories/" + id).session(tenantA.admin())).andExpect(status().isNotFound());
        mvc.perform(get("/api/categories/" + id).session(tenantB.admin())).andExpect(status().isOk());
    }
}
