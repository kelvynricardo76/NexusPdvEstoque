package com.nexus.pdv.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.pdv.entitlement.EntitlementService;
import com.nexus.pdv.entitlement.Entitlements;
import com.nexus.pdv.permission.domain.EffectivePermissions;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.permission.domain.PermissionEffect;
import com.nexus.pdv.permission.domain.Role;
import com.nexus.pdv.permission.domain.RoleCode;
import com.nexus.pdv.plan.domain.FeatureCode;
import com.nexus.pdv.plan.domain.LimitCode;
import com.nexus.pdv.plan.domain.Plan;
import com.nexus.pdv.product.domain.StockSituation;
import com.nexus.pdv.report.application.CsvReportWriter;
import com.nexus.pdv.report.domain.ReportResult;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.security.NexusSecurityProperties;
import com.nexus.pdv.shared.security.LoginAttemptService;
import com.nexus.pdv.shared.security.PasswordPolicy;
import com.nexus.pdv.shared.text.Documents;
import com.nexus.pdv.shared.time.Period;
import com.nexus.pdv.subscription.domain.BillingCycle;
import com.nexus.pdv.subscription.domain.SubscriptionStatus;
import com.nexus.pdv.subscription.domain.TenantSubscription;
import com.nexus.pdv.tenant.domain.Tenant;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Testes unitários das regras centrais (sem Spring). */
class DomainRulesTest {

    // ---------- Permissões efetivas ----------

    @Test
    void denyOverrideAlwaysWinsAndFeaturesFilterPermissions() {
        Role cashier = Role.standard(UUID.randomUUID(), RoleCode.CASHIER);
        Set<FeatureCode> basic = EnumSet.of(FeatureCode.PDV, FeatureCode.SALES, FeatureCode.PRODUCTS, FeatureCode.CUSTOMERS);
        Set<Permission> effective = EffectivePermissions.resolve(cashier, Map.of(
                Permission.STOCK_READ, PermissionEffect.ALLOW,
                Permission.PRODUCT_READ, PermissionEffect.DENY,
                Permission.FINANCIAL_READ, PermissionEffect.ALLOW), basic);
        assertThat(effective).contains(Permission.PDV_ACCESS, Permission.SALE_CREATE)
                .doesNotContain(Permission.PRODUCT_READ)      // DENY vence o cargo
                .doesNotContain(Permission.STOCK_READ)        // ALLOW sem feature STOCK
                .doesNotContain(Permission.FINANCIAL_READ);   // ALLOW sem feature FINANCIAL
    }

    @Test
    void tenantAdminHasEverythingContractedAndIgnoresOverrides() {
        Role admin = Role.standard(UUID.randomUUID(), RoleCode.TENANT_ADMIN);
        Set<Permission> effective = EffectivePermissions.resolve(admin, Map.of(Permission.USER_READ, PermissionEffect.DENY),
                EnumSet.of(FeatureCode.PRODUCTS));
        assertThat(effective).contains(Permission.USER_READ, Permission.PRODUCT_READ, Permission.AUDIT_READ)
                .doesNotContain(Permission.PDV_ACCESS, Permission.FINANCIAL_READ);
    }

    // ---------- Entitlements ----------

    @Test
    void entitlementPrecedenceIsOverrideThenPlanThenDefault() throws Exception {
        Plan plan = new Plan("BASIC", "Básico", null, BigDecimal.TEN, BigDecimal.TEN, true, 1);
        plan.replaceFeatures(EnumSet.of(FeatureCode.PDV, FeatureCode.REPORTS));
        plan.replaceLimits(Map.of(LimitCode.MAX_USERS, 3L));
        Tenant tenant = new Tenant("Loja", null, null, null, null);
        tenant.setFeatureOverride(FeatureCode.ADVANCED_REPORTS, true);
        tenant.setFeatureOverride(FeatureCode.REPORTS, false);
        tenant.setLimitOverride(LimitCode.MAX_USERS, 5L);
        TenantSubscription subscription = new TenantSubscription(UUID.randomUUID(), plan, BillingCycle.MONTHLY,
                LocalDate.now(), null, "MANUAL");

        Entitlements result = EntitlementService.compute(tenant, subscription,
                List.of(limitDefinition(LimitCode.MAX_USERS, 1), limitDefinition(LimitCode.MAX_PRODUCTS, 100)));

        assertThat(result.hasFeature(FeatureCode.PDV)).isTrue();
        assertThat(result.hasFeature(FeatureCode.ADVANCED_REPORTS)).isTrue();
        assertThat(result.hasFeature(FeatureCode.REPORTS)).isFalse();
        assertThat(result.limit(LimitCode.MAX_USERS)).isEqualTo(5L);      // override
        assertThat(result.limit(LimitCode.MAX_PRODUCTS)).isEqualTo(100L); // padrão
        assertThat(result.allowsOneMore(LimitCode.MAX_USERS, 4)).isTrue();
        assertThat(result.allowsOneMore(LimitCode.MAX_USERS, 5)).isFalse();
    }

    @Test
    void unlimitedLimitAlwaysAllows() {
        Entitlements entitlements = new Entitlements("X", "X", Set.of(), Map.of(LimitCode.MAX_USERS, LimitCode.UNLIMITED));
        assertThat(entitlements.allowsOneMore(LimitCode.MAX_USERS, 1_000_000)).isTrue();
    }

    @Test
    void subscriptionOperationRules() {
        Plan plan = new Plan("P", "P", null, BigDecimal.TEN, new BigDecimal("120"), true, 1);
        LocalDate today = LocalDate.of(2026, 9, 28);
        TenantSubscription trial = new TenantSubscription(UUID.randomUUID(), plan, BillingCycle.MONTHLY, today,
                today.plusDays(3), "MANUAL");
        assertThat(trial.allowsOperation(today)).isTrue();
        assertThat(trial.allowsOperation(today.plusDays(4))).isFalse();
        assertThat(trial.monthlyRecurringRevenue()).isEqualByComparingTo("0");

        TenantSubscription annual = new TenantSubscription(UUID.randomUUID(), plan, BillingCycle.ANNUAL, today, null, "MANUAL");
        assertThat(annual.monthlyRecurringRevenue()).isEqualByComparingTo("10.00");
        annual.changeStatus(SubscriptionStatus.SUSPENDED);
        assertThat(annual.allowsOperation(today)).isFalse();
    }

    // ---------- Segurança ----------

    @Test
    void passwordPolicy() {
        PasswordPolicy.validate("Senha2026", "user@x.com");
        assertThatThrownBy(() -> PasswordPolicy.validate("curta1", null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("somenteletras", null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("12345678", null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void loginAttemptsLockByIpAsWell() {
        NexusSecurityProperties properties = new NexusSecurityProperties();
        properties.getLogin().setMaxAttemptsPerIp(3);
        properties.getLogin().setMaxAttemptsPerEmail(100);
        LoginAttemptService service = new LoginAttemptService(properties, Clock.fixed(Instant.now(), ZoneOffset.UTC));
        for (int i = 0; i < 3; i++) {
            service.recordFailure("user" + i + "@x.com", "10.0.0.1");
        }
        assertThatThrownBy(() -> service.assertNotBlocked("outro@x.com", "10.0.0.1")).isInstanceOf(BusinessException.class);
        service.assertNotBlocked("outro@x.com", "10.0.0.2");
    }

    @Test
    void csvCellsAreProtectedAgainstFormulaInjection() {
        ReportResult report = new ReportResult("T", "T", LocalDate.now(), LocalDate.now(), List.of(),
                List.of(new ReportResult.Column("name", "Nome", ReportResult.ValueType.TEXT),
                        new ReportResult.Column("total", "Total", ReportResult.ValueType.MONEY)),
                List.of(Map.of("name", "=HYPERLINK(\"http://x\")", "total", new BigDecimal("-10.50"))), List.of());
        String csv = new String(CsvReportWriter.write(report, ZoneId.of("UTC")), StandardCharsets.UTF_8);
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://x\"\")\"").contains("-10,50");
    }

    // ---------- Utilitários de domínio ----------

    @Test
    void stockSituation() {
        assertThat(StockSituation.of(BigDecimal.ZERO, BigDecimal.ONE)).isEqualTo(StockSituation.OUT_OF_STOCK);
        assertThat(StockSituation.of(new BigDecimal("2"), new BigDecimal("5"))).isEqualTo(StockSituation.LOW_STOCK);
        assertThat(StockSituation.of(new BigDecimal("6"), new BigDecimal("5"))).isEqualTo(StockSituation.NORMAL);
    }

    @Test
    void documentsAreNormalizedAndMasked() {
        assertThat(Documents.normalize("123.456.789-09")).isEqualTo("12345678909");
        assertThat(Documents.mask("12345678909")).endsWith("8909").doesNotContain("123456");
        assertThatThrownBy(() -> Documents.normalize("123")).isInstanceOf(BusinessException.class);
    }

    @Test
    void periodPresetsUseTenantTimezone() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-28T02:00:00Z"), ZoneOffset.UTC); // 27/09 23h em São Paulo
        ZoneId saoPaulo = ZoneId.of("America/Sao_Paulo");
        Period today = Period.resolve(Period.Preset.TODAY, null, null, saoPaulo, clock);
        assertThat(today.start()).isEqualTo(LocalDate.of(2026, 9, 27));
        Period week = Period.resolve(Period.Preset.LAST_7_DAYS, null, null, saoPaulo, clock);
        assertThat(week.days()).isEqualTo(7);
        assertThat(week.previous().end()).isEqualTo(week.start().minusDays(1));
    }

    private static com.nexus.pdv.plan.domain.LimitDefinition limitDefinition(LimitCode code, long defaultValue) {
        try {
            Constructor<com.nexus.pdv.plan.domain.LimitDefinition> constructor =
                    com.nexus.pdv.plan.domain.LimitDefinition.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            var definition = constructor.newInstance();
            var codeField = com.nexus.pdv.plan.domain.LimitDefinition.class.getDeclaredField("code");
            codeField.setAccessible(true);
            codeField.set(definition, code);
            Method update = com.nexus.pdv.plan.domain.LimitDefinition.class.getMethod("update", String.class, String.class, long.class);
            update.invoke(definition, code.name(), null, defaultValue);
            return definition;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
