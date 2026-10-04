package com.nexus.pdv.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * IP e endpoint da requisição atual. O IP vem de {@code getRemoteAddr()}; atrás de proxy reverso,
 * configurar {@code server.forward-headers-strategy=native} para que ele reflita o cliente real
 * (nunca confiar em X-Forwarded-For diretamente).
 */
public record ClientRequestInfo(String ip, String endpoint) {

    private static final ClientRequestInfo NONE = new ClientRequestInfo(null, null);

    public static ClientRequestInfo current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return from(servlet.getRequest());
        }
        return NONE;
    }

    public static ClientRequestInfo from(HttpServletRequest request) {
        String endpoint = request.getMethod() + " " + request.getRequestURI();
        if (endpoint.length() > 200) {
            endpoint = endpoint.substring(0, 200);
        }
        return new ClientRequestInfo(request.getRemoteAddr(), endpoint);
    }
}
