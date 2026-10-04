package com.nexus.pdv.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.pdv.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/** Finalização (transacional, idempotente, concorrente) e cancelamento de vendas. */
class SaleFlowTest extends IntegrationTest {

    @Test
    void finalizingSaleDecreasesStockAndCreatesMovement() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Coca-Cola 2L", "10");
        MvcResult sale = sell(tenant.admin(), product, "3", null);
        assertThat(sale.getResponse().getStatus()).isEqualTo(201);
        assertThat((Object) read(sale, "$.status")).isEqualTo("COMPLETED");
        assertThat((Object) read(sale, "$.total")).isEqualTo(30.0);
        assertThat((Integer) read(sale, "$.number")).isEqualTo(1);

        mvc.perform(get("/api/products/" + product).session(tenant.admin()))
                .andExpect(jsonPath("$.currentStock").value(7.0));
        mvc.perform(get("/api/stock/movements").param("productId", product.toString()).session(tenant.admin()))
                .andExpect(jsonPath("$.content[0].type").value("SALE"))
                .andExpect(jsonPath("$.content[0].previousStock").value(10.0))
                .andExpect(jsonPath("$.content[0].newStock").value(7.0));
    }

    @Test
    void insufficientStockRollsBackEverything() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID available = createProduct(tenant.admin(), "Disponível", "10");
        UUID scarce = createProduct(tenant.admin(), "Escasso", "1");
        MvcResult result = mvc.perform(post("/api/sales").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "items", List.of(Map.of("productId", available, "quantity", 2),
                                        Map.of("productId", scarce, "quantity", 5)),
                                "payments", List.of(Map.of("method", "PIX", "amount", new BigDecimal("70.00")))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STOCK_INSUFFICIENT"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("Escasso");

        mvc.perform(get("/api/products/" + available).session(tenant.admin())).andExpect(jsonPath("$.currentStock").value(10.0));
        mvc.perform(get("/api/sales").session(tenant.admin())).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void idempotencyKeyPreventsDuplicateSale() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Produto", "10");
        String key = "pdv-" + UUID.randomUUID();
        MvcResult first = sell(tenant.admin(), product, "2", key);
        MvcResult replay = sell(tenant.admin(), product, "2", key);
        assertThat((Object) read(first, "$.id")).isEqualTo(read(replay, "$.id"));
        mvc.perform(get("/api/products/" + product).session(tenant.admin())).andExpect(jsonPath("$.currentStock").value(8.0));
        mvc.perform(get("/api/sales").session(tenant.admin())).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void concurrentSalesNeverOversellStock() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Último lote", "5");
        List<MockHttpSession> sessions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            sessions.add(login(tenant.adminEmail(), PASSWORD));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            MockHttpSession session = sessions.get(i % sessions.size());
            results.add(pool.submit(() -> sell(session, product, "1", null).getResponse().getStatus()));
        }
        int created = 0;
        int rejected = 0;
        for (Future<Integer> result : results) {
            int status = result.get();
            if (status == 201) {
                created++;
            } else {
                rejected++;
            }
        }
        pool.shutdown();
        assertThat(created).isEqualTo(5);
        assertThat(rejected).isEqualTo(7);
        mvc.perform(get("/api/products/" + product).session(tenant.admin())).andExpect(jsonPath("$.currentStock").value(0.0));
    }

    @Test
    void discountRequiresPermission() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Produto", "10");
        MockHttpSession cashier = newEmployee(tenant.admin(), "CASHIER");
        Map<String, Object> body = Map.of("discount", new BigDecimal("1.00"),
                "items", List.of(Map.of("productId", product, "quantity", 1)),
                "payments", List.of(Map.of("method", "CASH", "amount", new BigDecimal("9.00"))));
        mvc.perform(post("/api/sales").session(cashier).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/sales").session(tenant.admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discount").value(1.0))
                .andExpect(jsonPath("$.total").value(9.0));
    }

    @Test
    void paymentsMustCoverTotalAndChangeOnlyInCash() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Produto", "10");
        mvc.perform(post("/api/sales").session(tenant.admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("items", List.of(Map.of("productId", product, "quantity", 1)),
                                "payments", List.of(Map.of("method", "PIX", "amount", new BigDecimal("5.00")))))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/sales").session(tenant.admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("items", List.of(Map.of("productId", product, "quantity", 1)),
                                "payments", List.of(Map.of("method", "PIX", "amount", new BigDecimal("15.00")))))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/sales").session(tenant.admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("items", List.of(Map.of("productId", product, "quantity", 1)),
                                "payments", List.of(Map.of("method", "PIX", "amount", new BigDecimal("4.00")),
                                        Map.of("method", "CASH", "amount", new BigDecimal("10.00")))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.changeAmount").value(4.0))
                .andExpect(jsonPath("$.payments.length()").value(2));
    }

    @Test
    void cancellationRestoresStockOnceAndKeepsOriginalSale() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Produto", "10");
        String saleId = read(sell(tenant.admin(), product, "4", null), "$.id");

        mvc.perform(post("/api/sales/" + saleId + "/cancel").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", ""))))
                .andExpect(status().isBadRequest());

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/sales/" + saleId + "/cancel").session(tenant.admin()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Cliente desistiu"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELED"))
                    .andExpect(jsonPath("$.cancelReason").value("Cliente desistiu"))
                    .andExpect(jsonPath("$.canceledByName").exists());
        }
        mvc.perform(get("/api/products/" + product).session(tenant.admin())).andExpect(jsonPath("$.currentStock").value(10.0));
        mvc.perform(get("/api/stock/movements").param("productId", product.toString()).param("type", "SALE_CANCELLATION")
                        .session(tenant.admin()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/sales/" + saleId).session(tenant.admin()))
                .andExpect(jsonPath("$.total").value(40.0))
                .andExpect(jsonPath("$.payments[0].status").value("CANCELED"));
    }

    @Test
    void receiptContainsCompanyAndItems() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Produto recibo", "10");
        String saleId = read(sell(tenant.admin(), product, "1", null), "$.id");
        MockHttpSession cashier = newEmployee(tenant.admin(), "CASHIER");
        mvc.perform(get("/api/sales/" + saleId + "/receipt").session(cashier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").exists())
                .andExpect(jsonPath("$.sale.items[0].description").value("Produto recibo"));
    }

    @Test
    void saleNumbersAreSequentialPerTenant() throws Exception {
        TenantFixture a = newTenant("PLUS");
        TenantFixture b = newTenant("PLUS");
        UUID productA = createProduct(a.admin(), "A", "10");
        UUID productB = createProduct(b.admin(), "B", "10");
        assertThat((Integer) read(sell(a.admin(), productA, "1", null), "$.number")).isEqualTo(1);
        assertThat((Integer) read(sell(a.admin(), productA, "1", null), "$.number")).isEqualTo(2);
        assertThat((Integer) read(sell(b.admin(), productB, "1", null), "$.number")).isEqualTo(1);
    }
}
