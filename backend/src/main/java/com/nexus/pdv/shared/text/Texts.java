package com.nexus.pdv.shared.text;

/** Utilitários de normalização de entrada. */
public final class Texts {

    private Texts() {
    }

    /** Remove espaços nas extremidades; string vazia vira {@code null}. */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Mantém apenas dígitos (documentos, telefones); vazio vira {@code null}. */
    public static String digits(String value) {
        if (value == null) {
            return null;
        }
        String onlyDigits = value.replaceAll("\\D", "");
        return onlyDigits.isEmpty() ? null : onlyDigits;
    }

    /** Termo de busca normalizado; vazio vira {@code null}. */
    public static String searchTerm(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }
}
