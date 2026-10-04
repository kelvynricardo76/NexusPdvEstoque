package com.nexus.pdv.shared.error;

import java.time.Instant;
import java.util.List;

/**
 * Formato único de erro da API. Nunca contém stack trace ou detalhes internos.
 */
public record ApiError(
        String code,
        String message,
        Instant timestamp,
        String traceId,
        List<FieldViolation> fields) {

    public record FieldViolation(String field, String message) {
    }
}
