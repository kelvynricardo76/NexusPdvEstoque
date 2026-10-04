package com.nexus.pdv.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.pdv.shared.error.ApiError;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.web.TraceContext;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/** Respostas 401/403 do Spring Security no formato padrão {@link ApiError}. */
@Component
public class JsonSecurityErrorHandlers {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> write(response, ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.defaultMessage());
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            if (ex instanceof CsrfException) {
                write(response, ErrorCode.CSRF_INVALID, ErrorCode.CSRF_INVALID.defaultMessage());
            } else {
                write(response, ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.defaultMessage());
            }
        };
    }

    public void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError body = new ApiError(code.name(), message, Instant.now(), TraceContext.currentTraceId(), null);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
