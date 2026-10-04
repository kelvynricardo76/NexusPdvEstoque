package com.nexus.pdv.tenant.application;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sequências numéricas por tenant (ex.: número da venda). A linha do contador é travada com
 * {@code SELECT ... FOR UPDATE} dentro da transação chamadora, garantindo números únicos e
 * sequenciais mesmo sob concorrência.
 */
@Service
public class TenantCounterService {

    public static final String SALE_NUMBER = "SALE";

    private final JdbcTemplate jdbc;

    public TenantCounterService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long next(UUID tenantId, String counterName) {
        Long current = jdbc.query(
                "SELECT current_value FROM tenant_counters WHERE tenant_id = ? AND counter_name = ? FOR UPDATE",
                rs -> rs.next() ? rs.getLong(1) : null,
                tenantId, counterName);
        if (current == null) {
            jdbc.update("INSERT INTO tenant_counters (tenant_id, counter_name, current_value) VALUES (?, ?, 1)",
                    tenantId, counterName);
            return 1L;
        }
        long next = current + 1;
        jdbc.update("UPDATE tenant_counters SET current_value = ? WHERE tenant_id = ? AND counter_name = ?",
                next, tenantId, counterName);
        return next;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(UUID tenantId, String counterName) {
        jdbc.update("INSERT INTO tenant_counters (tenant_id, counter_name, current_value) VALUES (?, ?, 0)",
                tenantId, counterName);
    }
}
