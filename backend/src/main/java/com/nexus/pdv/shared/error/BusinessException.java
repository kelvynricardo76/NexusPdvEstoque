package com.nexus.pdv.shared.error;

/**
 * Exceção de regra de negócio mapeada para um {@link ErrorCode}. A mensagem é exibida ao
 * usuário final, portanto não deve conter dados sensíveis nem detalhes de implementação.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
