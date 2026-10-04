package com.nexus.pdv;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.plan.domain.FeatureCode;
import com.nexus.pdv.plan.domain.LimitCode;
import com.nexus.pdv.support.IntegrationTest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Migrations e sincronismo entre catálogos do banco e contratos do código. */
class NexusPdvApplicationTests extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAppliesAllMigrationsSuccessfully() {
        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        assertThat(history).hasSizeGreaterThanOrEqualTo(9);
        assertThat(history).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
    }

    @Test
    void permissionCatalogMatchesEnum() {
        List<String> inDatabase = jdbc.queryForList("SELECT code FROM permissions", String.class);
        assertThat(inDatabase).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(Permission.values()).map(Enum::name).toList());
        for (Permission permission : Permission.values()) {
            String feature = jdbc.queryForObject("SELECT feature_code FROM permissions WHERE code = ?", String.class,
                    permission.name());
            assertThat(feature).as(permission.name())
                    .isEqualTo(permission.feature() == null ? null : permission.feature().name());
        }
    }

    @Test
    void featureAndLimitCatalogsMatchEnums() {
        assertThat(jdbc.queryForList("SELECT code FROM features", String.class))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(FeatureCode.values()).map(Enum::name).toList());
        assertThat(jdbc.queryForList("SELECT code FROM limit_definitions", String.class))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(LimitCode.values()).map(Enum::name).toList());
    }

    @Test
    void initialPlansAreSeededAsData() {
        assertThat(jdbc.queryForList("SELECT code FROM plans", String.class)).contains("BASIC", "PLUS");
        Long basicUsers = jdbc.queryForObject("""
                SELECT l.limit_value FROM plan_limits l JOIN plans p ON p.id = l.plan_id
                WHERE p.code = 'BASIC' AND l.limit_code = 'MAX_USERS'""", Long.class);
        assertThat(basicUsers).isEqualTo(3L);
    }
}
