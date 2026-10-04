package com.nexus.pdv.sale;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.pdv.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

/** Devoluções parciais/totais: estorno proporcional, estoque, idempotência, permissão e isolamento. */
class SaleReturnTest extends IntegrationTest {

    @Test
    void partialReturnRestocksAndRefundsProportionally() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Refrigerante", "10");
        String saleId = read(sell(tenant.admin(), product, "4", null), "$.id");

        doReturn(tenant.admin(), saleId, List.of(item(1, "1", null)), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.refundStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.refundedTotal").value(10.0))
                .andExpect(jsonPath("$.items[0].returnedQuantity").value(1.0))
                .andExpect(jsonPath("$.returns.length()").value(1))
                .andExpect(jsonPath("$.returns[0].items[0].restocked").value(true));

        mvc.perform(get("/api/products/" + product).session(tenant.admin())).andExpect(jsonPath("$.currentStock").value(7.0));
        mvc.perform(get("/api/stock/movements").param("productId", product.toString()).param("type", "RETURN")
                        .session(tenant.admin()))
                .andExpect(jsonPath("$.totalElements").value(1));

        // Restante: devolução total fecha exatamente no valor da venda.
        doReturn(tenant.admin(), saleId, List.of(item(1, "3", null)), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundStatus").value("FULL"))
                .andExpect(jsonPath("$.refundedTotal").value(40.0));

        // Nada mais a devolver.
        doReturn(tenant.admin(), saleId, List.of(item(1, "1", null)), null)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void refundIncludesProportionalSaleDiscountAndSumsExactly() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Produto", "10");
        // 3 x 10,00 com 1,00 de desconto na venda = 29,00 pagos.
        String saleId = read(mvc.perform(post("/api/sales").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("discount", new BigDecimal("1.00"),
                                "items", List.of(Map.of("productId", product, "quantity", 3)),
                                "payments", List.of(Map.of("method", "PIX", "amount", new BigDecimal("29.00")))))))
                .andExpect(status().isCreated()).andReturn(), "$.id");

        doReturn(tenant.admin(), saleId, List.of(item(1, "1", null)), null)
                .andExpect(jsonPath("$.refundedTotal").value(9.67));
        doReturn(tenant.admin(), saleId, List.of(item(1, "1", null)), null)
                .andExpect(jsonPath("$.refundedTotal").value(19.34));
        doReturn(tenant.admin(), saleId, List.of(item(1, "1", null)), null)
                .andExpect(jsonPath("$.refundedTotal").value(29.0))
                .andExpect(jsonPath("$.refundStatus").value("FULL"));
    }

    @Test
    void damagedItemIsRefundedWithoutRestocking() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Frágil", "10");
        String saleId = read(sell(tenant.admin(), product, "2", null), "$.id");
        doReturn(tenant.admin(), saleId, List.of(item(1, "1", false)), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundedTotal").value(10.0))
                .andExpect(jsonPath("$.returns[0].items[0].restocked").value(false));
        mvc.perform(get("/api/products/" + product).session(tenant.admin())).andExpect(jsonPath("$.currentStock").value(8.0));
    }

    @Test
    void invalidReturnsAreRejected() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Produto", "10");
        String saleId = read(sell(tenant.admin(), product, "2", null), "$.id");

        doReturn(tenant.admin(), saleId, List.of(item(1, "3", null)), null).andExpect(status().isUnprocessableEntity());
        doReturn(tenant.admin(), saleId, List.of(item(9, "1", null)), null).andExpect(status().isBadRequest());
        doReturn(tenant.admin(), saleId, List.of(item(1, "0.5", null)), null).andExpect(status().isBadRequest());
        doReturn(tenant.admin(), saleId, List.of(item(1, "1", null), item(1, "1", null)), null)
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/sales/" + saleId + "/returns").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "", "refundMethod", "CASH", "items", List.of(item(1, "1", null))))))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/products/" + product).session(tenant.admin())).andExpect(jsonPath("$.currentStock").value(8.0));
    }

    @Test
    void idempotencyKeyPreventsDuplicateReturn() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Produto", "10");
        String saleId = read(sell(tenant.admin(), product, "3", null), "$.id");
        String key = "ret-" + UUID.randomUUID();
        doReturn(tenant.admin(), saleId, List.of(item(1, "1", null)), key).andExpect(status().isOk());
        doReturn(tenant.admin(), saleId, List.of(item(1, "1", null)), key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returns.length()").value(1))
                .andExpect(jsonPath("$.refundedTotal").value(10.0));
        mvc.perform(get("/api/products/" + product).session(tenant.admin())).andExpect(jsonPath("$.currentStock").value(8.0));
    }

    @Test
    void canceledSaleRejectsReturnAndReturnedSaleRejectsCancel() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Produto", "10");
        String canceled = read(sell(tenant.admin(), product, "1", null), "$.id");
        mvc.perform(post("/api/sales/" + canceled + "/cancel").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Teste"))))
                .andExpect(status().isOk());
        doReturn(tenant.admin(), canceled, List.of(item(1, "1", null)), null).andExpect(status().isUnprocessableEntity());

        String returned = read(sell(tenant.admin(), product, "2", null), "$.id");
        doReturn(tenant.admin(), returned, List.of(item(1, "1", null)), null).andExpect(status().isOk());
        mvc.perform(post("/api/sales/" + returned + "/cancel").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Teste"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void dashboardRevenueIsNetOfReturns() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Produto", "10");
        String saleId = read(sell(tenant.admin(), product, "4", null), "$.id");
        doReturn(tenant.admin(), saleId, List.of(item(1, "1", null)), null).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard").param("period", "TODAY").session(tenant.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.salesTotal").value(30.0))
                .andExpect(jsonPath("$.topProducts[0].quantity").value(3.0))
                .andExpect(jsonPath("$.topProducts[0].revenue").value(30.0));
    }

    @Test
    void returnRequiresPermissionAndIsTenantIsolated() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        TenantFixture other = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Produto", "10");
        String saleId = read(sell(tenant.admin(), product, "2", null), "$.id");

        MockHttpSession cashier = newEmployee(tenant.admin(), "CASHIER");
        doReturn(cashier, saleId, List.of(item(1, "1", null)), null).andExpect(status().isForbidden());
        doReturn(other.admin(), saleId, List.of(item(1, "1", null)), null).andExpect(status().isNotFound());

        mvc.perform(get("/api/sales/" + saleId).session(tenant.admin()))
                .andExpect(jsonPath("$.refundStatus").value("NONE"))
                .andExpect(jsonPath("$.refundedTotal").value(0.0));
        mvc.perform(get("/api/products/" + product).session(tenant.admin())).andExpect(jsonPath("$.currentStock").value(8.0));
    }

    private static Map<String, Object> item(int line, String quantity, Boolean restock) {
        return restock == null ? Map.of("lineNumber", line, "quantity", new BigDecimal(quantity))
                : Map.of("lineNumber", line, "quantity", new BigDecimal(quantity), "restock", restock);
    }

    private ResultActions doReturn(MockHttpSession session, String saleId, List<Map<String, Object>> items, String key)
            throws Exception {
        var request = post("/api/sales/" + saleId + "/returns").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", "Cliente devolveu", "refundMethod", "CASH", "items", items)));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request);
    }
}
