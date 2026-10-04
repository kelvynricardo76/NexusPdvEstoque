package com.nexus.pdv.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.nexus.pdv.platform.domain.PlatformAdmin;
import com.nexus.pdv.platform.infrastructure.PlatformAdminRepository;
import com.nexus.pdv.shared.persistence.TenantContext;
import com.nexus.pdv.subscription.domain.BillingCycle;
import com.nexus.pdv.tenant.application.ProvisionTenantCommand;
import com.nexus.pdv.tenant.application.TenantProvisioningService;
import com.nexus.pdv.tenant.domain.Tenant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base dos testes de integração: sobe a aplicação completa (H2 em memória) e oferece helpers
 * para criar tenants, autenticar via API real (sessão + CSRF) e ler respostas JSON.
 * Todas as subclasses compartilham o mesmo contexto Spring (mesma configuração).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestBeans.class)
public abstract class IntegrationTest {

    public static final String PASSWORD = "Senha@1234";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected TenantProvisioningService provisioningService;

    @Autowired
    protected PlatformAdminRepository platformAdminRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected TestBeans.RecordingPasswordResetNotifier resetNotifier;

    /** Tenant criado para o teste, com seu administrador. */
    public record TenantFixture(UUID tenantId, String adminEmail, MockHttpSession admin) {
    }

    protected static String unique(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected TenantFixture newTenant(String planCode) throws Exception {
        String email = unique("admin") + "@teste.com";
        Tenant tenant = TenantContext.callAsSystem(() -> provisioningService.provision(new ProvisionTenantCommand(
                "Empresa " + unique("t"), null, null, null, null, planCode, BillingCycle.MONTHLY, 0, "Admin Teste",
                email, null, PASSWORD)));
        return new TenantFixture(tenant.getId(), email, login(email, PASSWORD));
    }

    protected MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected MockHttpSession superAdmin() throws Exception {
        String email = unique("root") + "@nexus.dev";
        platformAdminRepository.save(new PlatformAdmin("Root", email, passwordEncoder.encode(PASSWORD)));
        MvcResult result = mvc.perform(post("/api/super-admin/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** Cria funcionário via API com o cargo padrão informado e devolve a sessão dele. */
    protected MockHttpSession newEmployee(MockHttpSession admin, String roleCode) throws Exception {
        return login(createEmployee(admin, roleCode).email(), PASSWORD);
    }

    public record Employee(UUID id, String email) {
    }

    protected Employee createEmployee(MockHttpSession admin, String roleCode) throws Exception {
        String roleId = roleId(admin, roleCode);
        String email = unique(roleCode.toLowerCase()) + "@teste.com";
        MvcResult result = mvc.perform(post("/api/users").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Funcionário " + roleCode, "email", email, "roleId", roleId,
                                "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        return new Employee(UUID.fromString(read(result, "$.user.id")), email);
    }

    protected String roleId(MockHttpSession admin, String roleCode) throws Exception {
        MvcResult roles = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/roles").session(admin))
                .andExpect(status().isOk()).andReturn();
        List<String> ids = JsonPath.read(roles.getResponse().getContentAsString(), "$[?(@.code == '" + roleCode + "')].id");
        return ids.get(0);
    }

    protected UUID createProduct(MockHttpSession session, String name, String stock) throws Exception {
        MvcResult result = mvc.perform(post("/api/products").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "salePrice", new BigDecimal("10.00"), "unit", "UN",
                                "initialStock", new BigDecimal(stock), "minimumStock", BigDecimal.ONE))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result, "$.id"));
    }

    protected MvcResult sell(MockHttpSession session, UUID productId, String quantity, String idempotencyKey)
            throws Exception {
        BigDecimal total = new BigDecimal("10.00").multiply(new BigDecimal(quantity));
        MockHttpServletRequestBuilder request = post("/api/sales").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "items", List.of(Map.of("productId", productId, "quantity", new BigDecimal(quantity))),
                        "payments", List.of(Map.of("method", "PIX", "amount", total)))));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request).andReturn();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
