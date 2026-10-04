package com.nexus.pdv.shared.web;

import org.slf4j.MDC;

/**
 * Acesso ao identificador de correlação da requisição atual.
 */
public final class TraceContext {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Request-Id";

    private TraceContext() {
    }

    public static String currentTraceId() {
        return MDC.get(MDC_KEY);
    }
}
