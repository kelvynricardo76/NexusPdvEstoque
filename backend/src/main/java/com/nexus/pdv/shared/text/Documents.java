package com.nexus.pdv.shared.text;

import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;

/** CPF/CNPJ: normalização (somente dígitos), validação de tamanho e mascaramento para exibição. */
public final class Documents {

    private Documents() {
    }

    public static String normalize(String document) {
        String digits = Texts.digits(document);
        if (digits == null) {
            return null;
        }
        if (digits.length() != 11 && digits.length() != 14) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Documento deve ser um CPF (11 dígitos) ou CNPJ (14 dígitos).");
        }
        return digits;
    }

    /** Exibe apenas os 4 últimos dígitos (minimização em listagens). */
    public static String mask(String document) {
        if (document == null || document.length() < 4) {
            return document;
        }
        return "•".repeat(document.length() - 4) + document.substring(document.length() - 4);
    }
}
