package com.nexus.pdv.entitlement;

import com.nexus.pdv.plan.domain.FeatureCode;
import com.nexus.pdv.plan.domain.LimitCode;
import java.util.List;

/** Direitos do tenant com a origem de cada valor (plano, override, padrão). */
public record EntitlementBreakdown(List<FeatureEntry> features, List<LimitEntry> limits) {

    /** @param override {@code null} = sem exceção; true = concedida; false = revogada */
    public record FeatureEntry(FeatureCode code, boolean inPlan, Boolean override, boolean effective) {
    }

    /** Valores -1 = ilimitado; {@code null} = não definido naquela camada. */
    public record LimitEntry(LimitCode code, String name, long defaultValue, Long planValue, Long override,
            long effective) {
    }
}
