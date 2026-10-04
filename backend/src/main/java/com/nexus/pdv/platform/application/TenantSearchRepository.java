package com.nexus.pdv.platform.application;

import com.nexus.pdv.subscription.domain.SubscriptionStatus;
import com.nexus.pdv.tenant.domain.EffectiveTenantStatus;
import com.nexus.pdv.tenant.domain.Tenant;
import com.nexus.pdv.tenant.domain.TenantStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Busca de tenants para o Super Admin, filtrando pela situação efetiva
 * (combinação do status administrativo com o status da assinatura).
 */
@Repository
public class TenantSearchRepository {

    private final EntityManager entityManager;

    public TenantSearchRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Page<Tenant> search(String q, EffectiveTenantStatus status, String planCode, Pageable pageable) {
        StringBuilder where = new StringBuilder(" from Tenant t, TenantSubscription s where s.tenantId = t.id");
        List<Object[]> params = new ArrayList<>();
        if (q != null) {
            where.append(" and (lower(t.name) like :q or lower(t.tradeName) like :q or t.document like :q)");
            params.add(new Object[] {"q", "%" + q.toLowerCase() + "%"});
        }
        if (planCode != null) {
            where.append(" and s.plan.code = :planCode");
            params.add(new Object[] {"planCode", planCode});
        }
        if (status != null) {
            switch (status) {
                case TRIAL, ACTIVE, PAST_DUE -> {
                    where.append(" and t.status = :active and s.status = :subStatus");
                    params.add(new Object[] {"active", TenantStatus.ACTIVE});
                    params.add(new Object[] {"subStatus", SubscriptionStatus.valueOf(status.name())});
                }
                case SUSPENDED, CANCELED -> {
                    where.append(" and (t.status = :tenantStatus or (t.status = :active and s.status = :subStatus))");
                    params.add(new Object[] {"tenantStatus", TenantStatus.valueOf(status.name())});
                    params.add(new Object[] {"active", TenantStatus.ACTIVE});
                    params.add(new Object[] {"subStatus", SubscriptionStatus.valueOf(status.name())});
                }
            }
        }
        TypedQuery<Tenant> query = entityManager.createQuery("select t" + where + " order by t.createdAt desc", Tenant.class);
        TypedQuery<Long> count = entityManager.createQuery("select count(t)" + where, Long.class);
        for (Object[] param : params) {
            query.setParameter((String) param[0], param[1]);
            count.setParameter((String) param[0], param[1]);
        }
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        return new PageImpl<>(query.getResultList(), pageable, count.getSingleResult());
    }
}
