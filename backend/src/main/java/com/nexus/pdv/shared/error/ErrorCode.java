package com.nexus.pdv.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Catálogo de códigos de erro expostos pela API. O {@code code} é contrato público:
 * o frontend decide comportamento por ele, nunca pela mensagem.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dados inválidos."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Autenticação necessária."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Você não tem permissão para realizar esta operação."),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "Sessão expirada ou requisição inválida. Recarregue a página."),
    TENANT_SUSPENDED(HttpStatus.FORBIDDEN, "A empresa está suspensa."),
    SUBSCRIPTION_INACTIVE(HttpStatus.FORBIDDEN, "A assinatura da empresa não está ativa."),
    FEATURE_NOT_AVAILABLE(HttpStatus.FORBIDDEN, "Este recurso não está disponível no seu plano."),
    PLAN_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY, "O limite do seu plano foi atingido."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso não encontrado."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Método não suportado para este recurso."),
    CONFLICT(HttpStatus.CONFLICT, "O recurso foi alterado ou já existe."),
    CONFIRMATION_REQUIRED(HttpStatus.CONFLICT, "Esta alteração exige confirmação."),
    BUSINESS_RULE(HttpStatus.UNPROCESSABLE_ENTITY, "Operação não permitida."),
    STOCK_INSUFFICIENT(HttpStatus.UNPROCESSABLE_ENTITY, "Estoque insuficiente."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Tente novamente mais tarde."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro inesperado.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
