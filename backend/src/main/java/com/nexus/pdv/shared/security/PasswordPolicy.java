package com.nexus.pdv.shared.security;

import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;

/**
 * Política mínima de senha: 8 a 128 caracteres, com letras e números, diferente do e-mail.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void validate(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "A senha deve ter entre " + MIN_LENGTH + " e " + MAX_LENGTH + " caracteres.");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "A senha deve conter letras e números.");
        }
        if (email != null && password.equalsIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "A senha não pode ser igual ao e-mail.");
        }
    }
}
