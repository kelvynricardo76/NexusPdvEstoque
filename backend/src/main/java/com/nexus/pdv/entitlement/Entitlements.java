package com.nexus.pdv.entitlement;

import com.nexus.pdv.plan.domain.FeatureCode;
import com.nexus.pdv.plan.domain.LimitCode;
import java.util.Map;
import java.util.Set;

/**
 * Direitos efetivos de um tenant: resultado de plano + overrides + padrões.
 *
 * @param limits valores efetivos; {@link LimitCode#UNLIMITED} = ilimitado
 */
public record Entitlements(String planCode, String planName, Set<FeatureCode> features, Map<LimitCode, Long> limits) {

    public boolean hasFeature(FeatureCode feature) {
        return features.contains(feature);
    }

    public long limit(LimitCode code) {
        return limits.getOrDefault(code, 0L);
    }

    public boolean allowsOneMore(LimitCode code, long currentUsage) {
        long max = limit(code);
        return LimitCode.isUnlimited(max) || currentUsage < max;
    }
}
