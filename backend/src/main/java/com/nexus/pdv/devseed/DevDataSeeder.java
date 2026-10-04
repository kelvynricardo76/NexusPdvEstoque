package com.nexus.pdv.devseed;

import com.nexus.pdv.category.application.CategoryService;
import com.nexus.pdv.customer.api.CustomerDtos.CustomerRequest;
import com.nexus.pdv.customer.application.CustomerService;
import com.nexus.pdv.financial.api.FinancialDtos.EntryRequest;
import com.nexus.pdv.financial.application.FinancialService;
import com.nexus.pdv.financial.domain.FinancialEntry;
import com.nexus.pdv.permission.domain.RoleCode;
import com.nexus.pdv.permission.infrastructure.RoleRepository;
import com.nexus.pdv.platform.domain.PlatformAdmin;
import com.nexus.pdv.platform.infrastructure.PlatformAdminRepository;
import com.nexus.pdv.product.api.CatalogDtos.CategoryRequest;
import com.nexus.pdv.product.api.CatalogDtos.ProductRequest;
import com.nexus.pdv.product.api.CatalogDtos.ProductResponse;
import com.nexus.pdv.product.api.CatalogDtos.StockAdjustRequest;
import com.nexus.pdv.product.api.CatalogDtos.StockEntryRequest;
import com.nexus.pdv.product.api.CatalogDtos.SupplierRequest;
import com.nexus.pdv.product.application.ProductService;
import com.nexus.pdv.product.domain.ProductUnit;
import com.nexus.pdv.sale.api.SaleDtos.FinalizeSaleRequest;
import com.nexus.pdv.sale.api.SaleDtos.ItemRequest;
import com.nexus.pdv.sale.api.SaleDtos.PaymentRequest;
import com.nexus.pdv.sale.api.SaleDtos.SaleResponse;
import com.nexus.pdv.sale.application.SaleService;
import com.nexus.pdv.sale.domain.PaymentMethod;
import com.nexus.pdv.shared.persistence.TenantContext;
import com.nexus.pdv.shared.security.AuthenticatedUser;
import com.nexus.pdv.shared.security.PrincipalType;
import com.nexus.pdv.stock.application.StockService;
import com.nexus.pdv.subscription.domain.BillingCycle;
import com.nexus.pdv.supplier.application.SupplierService;
import com.nexus.pdv.tenant.application.ProvisionTenantCommand;
import com.nexus.pdv.tenant.application.TenantProvisioningService;
import com.nexus.pdv.tenant.domain.Tenant;
import com.nexus.pdv.tenant.infrastructure.TenantRepository;
import com.nexus.pdv.user.domain.User;
import com.nexus.pdv.user.infrastructure.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dados de demonstração — SOMENTE no perfil {@code dev} (nunca em produção).
 * Usa os serviços reais (vendas, estoque, auditoria), garantindo dados consistentes.
 * Executa apenas se o banco ainda não possuir tenants.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "nexus.dev-seed", name = "enabled", havingValue = "true")
@Order(10)
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final TenantRepository tenantRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final TenantProvisioningService provisioningService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CategoryService categoryService;
    private final SupplierService supplierService;
    private final ProductService productService;
    private final CustomerService customerService;
    private final StockService stockService;
    private final SaleService saleService;
    private final FinancialService financialService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String password;
    private final String devLoginEmail;
    private final String devLoginPassword;

    public DevDataSeeder(TenantRepository tenantRepository, PlatformAdminRepository platformAdminRepository,
            TenantProvisioningService provisioningService, UserRepository userRepository, RoleRepository roleRepository,
            CategoryService categoryService, SupplierService supplierService, ProductService productService,
            CustomerService customerService, StockService stockService, SaleService saleService,
            FinancialService financialService, PasswordEncoder passwordEncoder, JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager, Clock clock,
            @Value("${nexus.dev-seed.password}") String password,
            @Value("${nexus.dev-login.email:}") String devLoginEmail,
            @Value("${nexus.dev-login.password:}") String devLoginPassword) {
        this.devLoginEmail = devLoginEmail;
        this.devLoginPassword = devLoginPassword;
        this.tenantRepository = tenantRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.provisioningService = provisioningService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.categoryService = categoryService;
        this.supplierService = supplierService;
        this.productService = productService;
        this.customerService = customerService;
        this.stockService = stockService;
        this.saleService = saleService;
        this.financialService = financialService;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tenantRepository.count() == 0) {
            seedAll();
        }
        ensureDevLoginAccount();
    }

    /**
     * Conta de atalho "admin"/"admin" (administrador da Loja Exemplo). Criada também em bancos já
     * populados. A senha curta ignora a política de senha de propósito — existe só no perfil dev.
     */
    private void ensureDevLoginAccount() {
        if (devLoginEmail.isBlank() || devLoginPassword.isBlank()) {
            return;
        }
        TenantContext.runAsSystem(() -> tx.executeWithoutResult(status -> {
            if (userRepository.findByEmail(devLoginEmail).isPresent()) {
                return;
            }
            User erick = userRepository.findByEmail("erick@lojaexemplo.com.br").orElse(null);
            if (erick == null) {
                return;
            }
            var role = roleRepository.findAll().stream()
                    .filter(r -> r.getTenantId().equals(erick.getTenantId()) && r.getCode() == RoleCode.TENANT_ADMIN)
                    .findFirst().orElseThrow();
            userRepository.save(User.provisioned(erick.getTenantId(), role, "Administrador", devLoginEmail, null,
                    passwordEncoder.encode(devLoginPassword)));
            log.info("[DEV] Login de atalho criado: usuário \"admin\" (administrador da Loja Exemplo).");
        }));
    }

    private void seedAll() {
        log.info("[DEV] Criando dados de demonstração...");
        tx.executeWithoutResult(status -> {
            if (!platformAdminRepository.existsByEmail("admin@nexus.dev")) {
                platformAdminRepository.save(new PlatformAdmin("Administrador Nexus", "admin@nexus.dev",
                        passwordEncoder.encode(password)));
            }
        });

        Tenant loja = TenantContext.callAsSystem(() -> provisioningService.provision(new ProvisionTenantCommand(
                "Loja Exemplo Comércio de Alimentos LTDA", "Loja Exemplo", "12345678000190", "contato@lojaexemplo.com.br",
                "(11) 4002-8922", "PLUS", BillingCycle.MONTHLY, 0, "Erick Ricardo", "erick@lojaexemplo.com.br",
                "(11) 98888-0000", password)));
        TenantContext.callAsSystem(() -> provisioningService.provision(new ProvisionTenantCommand(
                "Mercado Central LTDA", "Mercado Central", "98765432000110", "contato@mercadocentral.com.br",
                "(21) 3333-4444", "BASIC", BillingCycle.MONTHLY, 14, "Ana Souza", "ana@mercadocentral.com.br", null,
                password)));

        TenantContext.runAsSystem(() -> tx.executeWithoutResult(status -> {
            createEmployee(loja.getId(), RoleCode.MANAGER, "Maria Oliveira", "maria@lojaexemplo.com.br");
            createEmployee(loja.getId(), RoleCode.CASHIER, "João Santos", "joao@lojaexemplo.com.br");
            createEmployee(loja.getId(), RoleCode.STOCK_OPERATOR, "Pedro Lima", "pedro@lojaexemplo.com.br");
        }));

        User erick = TenantContext.callAsSystem(() -> tx.execute(s -> userRepository.findByEmail("erick@lojaexemplo.com.br").orElseThrow()));
        User ana = TenantContext.callAsSystem(() -> tx.execute(s -> userRepository.findByEmail("ana@mercadocentral.com.br").orElseThrow()));

        runAs(erick, this::seedLojaExemplo);
        runAs(ana, this::seedMercadoCentral);
        log.info("[DEV] Dados criados. Logins (senha definida em nexus.dev-seed.password): "
                + "erick@lojaexemplo.com.br (admin), maria@, joao@, pedro@lojaexemplo.com.br, "
                + "ana@mercadocentral.com.br e Super Admin admin@nexus.dev");
    }

    private void createEmployee(UUID tenantId, RoleCode code, String name, String email) {
        var role = roleRepository.findAll().stream()
                .filter(r -> r.getTenantId().equals(tenantId) && r.getCode() == code)
                .findFirst().orElseThrow();
        userRepository.save(User.provisioned(tenantId, role, name, email, null, passwordEncoder.encode(password)));
    }

    private void runAs(User user, Runnable action) {
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getTenantId(), user.getEmail(),
                user.getName(), PrincipalType.TENANT_USER);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities()));
        SecurityContextHolder.setContext(context);
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void seedLojaExemplo() {
        var bebidas = categoryService.create(new CategoryRequest("Bebidas", "Refrigerantes, sucos e águas")).id();
        var alimentos = categoryService.create(new CategoryRequest("Alimentos", "Mercearia e congelados")).id();
        var laticinios = categoryService.create(new CategoryRequest("Laticínios", "Queijos, leites e derivados")).id();
        var padaria = categoryService.create(new CategoryRequest("Padaria", "Pães e confeitaria")).id();
        var limpeza = categoryService.create(new CategoryRequest("Limpeza", "Produtos de limpeza")).id();
        var hortifruti = categoryService.create(new CategoryRequest("Hortifrúti", "Frutas, verduras e legumes")).id();

        var distribuidora = supplierService.create(new SupplierRequest("Distribuidora Paulista de Bebidas LTDA",
                "DP Bebidas", "11222333000144", "(11) 3000-1000", "vendas@dpbebidas.com.br", "Av. Industrial, 500 - SP", null)).id();
        var frigorifico = supplierService.create(new SupplierRequest("Frigorífico Boa Carne S.A.", "Boa Carne",
                "22333444000155", "(11) 3000-2000", "comercial@boacarne.com.br", null, null)).id();
        var laticinioSupplier = supplierService.create(new SupplierRequest("Laticínios Serra Verde LTDA", "Serra Verde",
                "33444555000166", "(35) 3222-3000", "pedidos@serraverde.com.br", null, null)).id();
        supplierService.create(new SupplierRequest("Limpa Tudo Distribuidora ME", "Limpa Tudo", "44555666000177",
                "(11) 3000-4000", null, null, null));

        List<ProductResponse> products = new ArrayList<>();
        products.add(product("Coca-Cola 2L", "BEB-001", "7894900011517", bebidas, distribuidora, "12.00", "7.20", "10", "3"));
        products.add(product("Guaraná Antarctica 2L", "BEB-002", "7891991000833", bebidas, distribuidora, "9.90", "5.60", "10", "40"));
        products.add(product("Água Mineral 500ml", "BEB-003", "7896064200018", bebidas, distribuidora, "3.00", "1.10", "24", "120"));
        products.add(product("Suco de Laranja 1L", "BEB-004", "7896045505101", bebidas, distribuidora, "8.50", "4.90", "8", "30"));
        products.add(product("Cerveja Lata 350ml", "BEB-005", "7891149103102", bebidas, distribuidora, "4.50", "2.60", "48", "180"));
        products.add(product("Hambúrguer 120g", "ALI-001", "7893000394117", alimentos, frigorifico, "19.90", "11.00", "20", "5"));
        products.add(product("Batata Frita Congelada 1kg", "ALI-002", "7891000100103", alimentos, null, "14.00", "8.30", "10", "42"));
        products.add(product("Arroz Branco 5kg", "ALI-003", "7896006711015", alimentos, null, "27.90", "19.50", "10", "35"));
        products.add(product("Feijão Carioca 1kg", "ALI-004", "7896006740015", alimentos, null, "8.90", "5.80", "15", "50"));
        products.add(product("Macarrão Espaguete 500g", "ALI-005", "7896005800017", alimentos, null, "4.99", "2.70", "20", "60"));
        products.add(product("Óleo de Soja 900ml", "ALI-006", "7891107101621", alimentos, null, "7.49", "5.10", "12", "45"));
        products.add(product("Queijo Mussarela 500g", "LAT-001", "7896051111016", laticinios, laticinioSupplier, "32.50", "21.00", "15", "4"));
        products.add(product("Leite Integral 1L", "LAT-002", "7896051130017", laticinios, laticinioSupplier, "5.49", "3.80", "24", "90"));
        products.add(product("Iogurte Natural 170g", "LAT-003", "7891025101017", laticinios, laticinioSupplier, "3.99", "2.10", "20", "36"));
        products.add(product("Manteiga 200g", "LAT-004", "7896051150015", laticinios, laticinioSupplier, "12.90", "8.40", "10", "22"));
        products.add(product("Pão de Hambúrguer (6un)", "PAD-001", "7896002300015", padaria, null, "12.00", "6.50", "20", "0"));
        products.add(product("Pão Francês (kg)", "PAD-002", null, padaria, null, "16.90", "7.90", "5", "25", ProductUnit.KG));
        products.add(product("Bolo de Chocolate", "PAD-003", null, padaria, null, "24.90", "12.00", "3", "8"));
        products.add(product("Detergente 500ml", "LIM-001", "7891022100013", limpeza, null, "2.79", "1.40", "24", "70"));
        products.add(product("Sabão em Pó 1kg", "LIM-002", "7891150020016", limpeza, null, "15.90", "10.20", "10", "28"));
        products.add(product("Água Sanitária 2L", "LIM-003", "7896094900011", limpeza, null, "6.49", "3.30", "12", "40"));
        products.add(product("Banana Prata (kg)", "HOR-001", null, hortifruti, null, "6.99", "3.50", "10", "30", ProductUnit.KG));
        products.add(product("Tomate (kg)", "HOR-002", null, hortifruti, null, "8.49", "4.60", "8", "18", ProductUnit.KG));
        products.add(product("Batata 1kg", "HOR-003", null, hortifruti, null, "14.00", "6.80", "10", "42"));

        List<UUID> customers = new ArrayList<>();
        String[][] people = {
                {"Carlos Almeida", "12345678909", "(11) 99111-2222", "carlos.almeida@email.com"},
                {"Fernanda Costa", null, "(11) 99222-3333", "fernanda.costa@email.com"},
                {"Rafael Martins", "98765432100", "(11) 99333-4444", null},
                {"Juliana Rocha", null, "(11) 99444-5555", "ju.rocha@email.com"},
                {"Lucas Ferreira", null, "(11) 99555-6666", null},
                {"Patrícia Gomes", "11144477735", "(11) 99666-7777", "patricia.gomes@email.com"},
                {"Bruno Carvalho", null, "(11) 99777-8888", null},
                {"Amanda Ribeiro", null, "(11) 99888-9999", "amanda.r@email.com"},
                {"Restaurante Sabor & Cia", "55666777000188", "(11) 3555-6666", "compras@saborecia.com.br"},
                {"Mariana Teixeira", null, null, null}};
        for (String[] person : people) {
            customers.add(customerService.create(new CustomerRequest(person[0], person[1], person[2], person[3], null, null)).id());
        }

        // Vendas dos últimos 45 dias com datas retroativas (estoque reposto para não zerar).
        Random random = new Random(2026);
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        List<ProductResponse> sellable = products.stream().filter(p -> !p.sku().equals("PAD-001")).toList();
        List<UUID> saleIds = new ArrayList<>();
        for (int daysAgo = 44; daysAgo >= 0; daysAgo--) {
            LocalDate day = today.minusDays(daysAgo);
            int salesToday = daysAgo == 0 ? 6 : 4 + random.nextInt(9);
            if (daysAgo % 7 == 0) {
                for (ProductResponse product : sellable) {
                    if (!product.sku().equals("BEB-001") && !product.sku().equals("ALI-001") && !product.sku().equals("LAT-001")) {
                        stockService.registerEntry(new StockEntryRequest(product.id(), BigDecimal.valueOf(20), "Reposição semanal"));
                    }
                }
            }
            for (int s = 0; s < salesToday; s++) {
                SaleResponse sale = randomSale(random, sellable, customers);
                if (sale == null) {
                    continue;
                }
                LocalTime time = LocalTime.of(8 + random.nextInt(12), random.nextInt(60));
                backdate(sale.id(), ZonedDateTime.of(day, time, ZONE));
                saleIds.add(sale.id());
            }
        }
        if (saleIds.size() > 10) {
            saleService.cancel(saleIds.get(saleIds.size() - 3), "Cliente desistiu da compra");
            saleService.cancel(saleIds.get(saleIds.size() / 2), "Erro de digitação no PDV");
        }
        // Contagem de inventário: deixa alguns itens abaixo do mínimo para os alertas de estoque.
        for (ProductResponse product : products) {
            BigDecimal target = switch (product.sku()) {
                case "BEB-001" -> new BigDecimal("3");
                case "ALI-001" -> new BigDecimal("5");
                case "LAT-001" -> new BigDecimal("4");
                default -> null;
            };
            if (target != null) {
                stockService.adjust(new StockAdjustRequest(product.id(), target, "Contagem de inventário"));
            }
        }

        // Financeiro operacional.
        financial(FinancialEntry.Type.PAYABLE, "Aluguel da loja", "Aluguel", "3500.00", today.withDayOfMonth(5), true);
        financial(FinancialEntry.Type.PAYABLE, "Energia elétrica", "Utilidades", "780.40", today.minusDays(3), false);
        financial(FinancialEntry.Type.PAYABLE, "Pedido DP Bebidas", "Fornecedores", "2450.00", today.plusDays(7), false);
        financial(FinancialEntry.Type.PAYABLE, "Internet e telefone", "Utilidades", "199.90", today.plusDays(12), false);
        financial(FinancialEntry.Type.PAYABLE, "Salários", "Pessoal", "8900.00", today.minusDays(20), true);
        financial(FinancialEntry.Type.RECEIVABLE, "Venda a prazo — Restaurante Sabor & Cia", "Vendas a prazo", "1890.00", today.plusDays(10), false);
        financial(FinancialEntry.Type.RECEIVABLE, "Venda a prazo — Carlos Almeida", "Vendas a prazo", "320.00", today.minusDays(6), false);
        financial(FinancialEntry.Type.RECEIVABLE, "Repasse de cartões", "Cartões", "5400.00", today.minusDays(2), true);
    }

    private void seedMercadoCentral() {
        var mercearia = categoryService.create(new CategoryRequest("Mercearia", null)).id();
        product("Café Torrado 500g", "MC-001", "7896089012019", mercearia, null, "18.90", "12.00", "10", "25");
        product("Açúcar Refinado 1kg", "MC-002", "7896089023015", mercearia, null, "5.49", "3.60", "20", "60");
        customerService.create(new CustomerRequest("Cliente do Mercado Central", null, null, null, null, null));
    }

    private ProductResponse product(String name, String sku, String barcode, UUID category, UUID supplier, String price,
            String cost, String minimum, String initial) {
        return product(name, sku, barcode, category, supplier, price, cost, minimum, initial, ProductUnit.UN);
    }

    private ProductResponse product(String name, String sku, String barcode, UUID category, UUID supplier, String price,
            String cost, String minimum, String initial, ProductUnit unit) {
        return productService.create(new ProductRequest(name, null, sku, barcode, category, supplier,
                new BigDecimal(price), new BigDecimal(cost), new BigDecimal(minimum), new BigDecimal(initial), unit));
    }

    private SaleResponse randomSale(Random random, List<ProductResponse> products, List<UUID> customers) {
        int itemCount = 1 + random.nextInt(4);
        List<ItemRequest> items = new ArrayList<>();
        List<UUID> used = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            ProductResponse product = products.get(random.nextInt(products.size()));
            if (used.contains(product.id())) {
                continue;
            }
            used.add(product.id());
            BigDecimal quantity = product.unit().isDiscrete()
                    ? BigDecimal.valueOf(1 + random.nextInt(3))
                    : BigDecimal.valueOf(0.3 + random.nextDouble() * 1.5).setScale(3, RoundingMode.HALF_UP);
            items.add(new ItemRequest(product.id(), quantity, null));
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ItemRequest item : items) {
            ProductResponse product = products.stream().filter(p -> p.id().equals(item.productId())).findFirst().orElseThrow();
            total = total.add(product.salePrice().multiply(item.quantity()).setScale(2, RoundingMode.HALF_UP));
        }
        PaymentMethod[] methods = {PaymentMethod.PIX, PaymentMethod.PIX, PaymentMethod.CREDIT_CARD, PaymentMethod.CREDIT_CARD,
                PaymentMethod.DEBIT_CARD, PaymentMethod.CASH, PaymentMethod.OTHER};
        PaymentMethod method = methods[random.nextInt(methods.length)];
        BigDecimal paid = method == PaymentMethod.CASH ? total.setScale(0, RoundingMode.UP).add(BigDecimal.valueOf(random.nextInt(3) * 5L)) : total;
        UUID customer = random.nextInt(3) == 0 ? customers.get(random.nextInt(customers.size())) : null;
        try {
            return saleService.finalizeSale(new FinalizeSaleRequest(items, customer, null,
                    List.of(new PaymentRequest(method, paid))), null);
        } catch (RuntimeException ex) {
            return null; // estoque insuficiente para a combinação sorteada
        }
    }

    private void backdate(UUID saleId, ZonedDateTime when) {
        Timestamp timestamp = Timestamp.from(when.toInstant());
        jdbc.update("UPDATE sales SET created_at = ?, updated_at = ? WHERE id = ?", timestamp, timestamp, saleId);
        jdbc.update("UPDATE sale_items SET created_at = ? WHERE sale_id = ?", timestamp, saleId);
        jdbc.update("UPDATE payments SET created_at = ? WHERE sale_id = ?", timestamp, saleId);
        jdbc.update("UPDATE stock_movements SET created_at = ? WHERE reference_id = ?", timestamp, saleId);
    }

    private void financial(FinancialEntry.Type type, String description, String category, String amount,
            LocalDate dueDate, boolean paid) {
        var entry = financialService.create(new EntryRequest(type, description, category, new BigDecimal(amount), dueDate,
                null, null, null));
        if (paid) {
            LocalDate today = LocalDate.now(clock.withZone(ZONE));
            financialService.pay(entry.id(), dueDate.isAfter(today) ? today : dueDate);
        }
    }
}
