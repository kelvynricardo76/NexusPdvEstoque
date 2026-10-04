package com.nexus.pdv.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.pdv.support.IntegrationTest;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/** Relatórios, exportações (CSV/PDF), limite de histórico e importação (CSV/XLSX). */
class ReportAndImportTest extends IntegrationTest {

    @Test
    void salesReportAndDashboardReflectSales() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        UUID product = createProduct(tenant.admin(), "Relatório", "20");
        sell(tenant.admin(), product, "2", null);
        sell(tenant.admin(), product, "3", null);
        mvc.perform(get("/api/reports/SALES").param("period", "TODAY").session(tenant.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary[0].value").value(2))
                .andExpect(jsonPath("$.summary[1].value").value(50.0))
                .andExpect(jsonPath("$.rows.length()").value(2));
        mvc.perform(get("/api/dashboard").session(tenant.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.salesCount").value(2))
                .andExpect(jsonPath("$.kpis.salesTotal").value(50.0))
                .andExpect(jsonPath("$.last7Days.length()").value(7))
                .andExpect(jsonPath("$.topProducts[0].name").value("Relatório"))
                .andExpect(jsonPath("$.advanced.grossProfit").exists());
    }

    @Test
    void basicDashboardHasNoAdvancedBlock() throws Exception {
        TenantFixture tenant = newTenant("BASIC");
        mvc.perform(get("/api/dashboard").session(tenant.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advanced").doesNotExist());
    }

    @Test
    void exportsRespectFormatFeatures() throws Exception {
        TenantFixture plus = newTenant("PLUS");
        UUID product = createProduct(plus.admin(), "Exportável; com =fórmula", "5");
        sell(plus.admin(), product, "1", null);
        MvcResult csv = mvc.perform(get("/api/reports/STOCK/export").param("format", "CSV").session(plus.admin()))
                .andExpect(status().isOk()).andReturn();
        String content = csv.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv.getResponse().getContentType()).startsWith("text/csv");
        assertThat(content).contains("Produto;SKU;Categoria").contains("\"Exportável; com =fórmula\"");

        MvcResult pdf = mvc.perform(get("/api/reports/SALES/export").param("format", "PDF").param("period", "TODAY")
                        .session(plus.admin()))
                .andExpect(status().isOk()).andReturn();
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        TenantFixture basic = newTenant("BASIC");
        mvc.perform(get("/api/reports/STOCK/export").param("format", "CSV").session(basic.admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"));
    }

    @Test
    void reportHistoryIsLimitedByPlan() throws Exception {
        TenantFixture basic = newTenant("BASIC"); // REPORT_HISTORY_DAYS = 90
        mvc.perform(get("/api/reports/SALES").session(basic.admin())
                        .param("from", LocalDate.now().minusDays(200).toString())
                        .param("to", LocalDate.now().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
        mvc.perform(get("/api/reports/SALES").session(basic.admin()).param("period", "LAST_30_DAYS"))
                .andExpect(status().isOk());
    }

    @Test
    void csvImportFlowNeverImportsInvalidRowsSilently() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        String csv = "Nome;SKU;Categoria;Preço de venda;Estoque\n"
                + "Café 500g;CAF-1;Mercearia;18,90;10\n"
                + "Açúcar 1kg;ACU-1;Mercearia;5,49;20\n"
                + ";SEM-NOME;Mercearia;abc;1\n";
        MvcResult upload = mvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "produtos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .param("type", "PRODUCTS").session(tenant.admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.suggestedMapping.name").value(0))
                .andExpect(jsonPath("$.suggestedMapping.salePrice").value(3))
                .andExpect(jsonPath("$.suggestedMapping.initialStock").value(4))
                .andReturn();
        String jobId = read(upload, "$.jobId");

        mvc.perform(post("/api/imports/" + jobId + "/validate").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("mapping", Map.of("name", 0, "sku", 1, "category", 2, "salePrice", 3,
                                "initialStock", 4)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validRows").value(2))
                .andExpect(jsonPath("$.invalidRows").value(1))
                .andExpect(jsonPath("$.preview[2].errors.length()").value(2));

        mvc.perform(post("/api/imports/" + jobId + "/confirm").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"skipInvalid\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFIRMATION_REQUIRED"));

        mvc.perform(post("/api/imports/" + jobId + "/confirm").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"skipInvalid\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdCount").value(2))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.rejectedRows[0].rowNumber").value(4));

        mvc.perform(get("/api/products").param("q", "Café").session(tenant.admin()))
                .andExpect(jsonPath("$.content[0].currentStock").value(10.0))
                .andExpect(jsonPath("$.content[0].categoryName").value("Mercearia"));
        mvc.perform(get("/api/audit").param("action", "IMPORT").session(tenant.admin()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void xlsxImportOfCustomers() throws Exception {
        TenantFixture tenant = newTenant("PLUS");
        byte[] xlsx = minimalXlsx(new String[][] {{"Nome", "Telefone", "E-mail"},
                {"Cliente Planilha", "(11) 90000-0000", "cliente@planilha.com"}});
        MvcResult upload = mvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "clientes.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx))
                        .param("type", "CUSTOMERS").session(tenant.admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0]").value("Nome"))
                .andReturn();
        String jobId = read(upload, "$.jobId");
        mvc.perform(post("/api/imports/" + jobId + "/validate").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("mapping", Map.of("name", 0, "phone", 1, "email", 2)))))
                .andExpect(jsonPath("$.validRows").value(1));
        mvc.perform(post("/api/imports/" + jobId + "/confirm").session(tenant.admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"skipInvalid\":false}"))
                .andExpect(jsonPath("$.createdCount").value(1));
        mvc.perform(get("/api/customers").param("q", "Planilha").session(tenant.admin()))
                .andExpect(jsonPath("$.content[0].email").value("cliente@planilha.com"));
    }

    @Test
    void importRequiresDataImportFeature() throws Exception {
        TenantFixture basic = newTenant("BASIC");
        mvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "x.csv", "text/csv", "Nome\nA\n".getBytes(StandardCharsets.UTF_8)))
                        .param("type", "CATEGORIES").session(basic.admin()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"));
    }

    /** Monta um XLSX mínimo (inlineStr) para testar o parser sem dependências. */
    static byte[] minimalXlsx(String[][] rows) throws Exception {
        StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int r = 0; r < rows.length; r++) {
            sheet.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < rows[r].length; c++) {
                sheet.append("<c r=\"").append((char) ('A' + c)).append(r + 1).append("\" t=\"inlineStr\"><is><t>")
                        .append(rows[r][c]).append("</t></is></c>");
            }
            sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(sheet.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
