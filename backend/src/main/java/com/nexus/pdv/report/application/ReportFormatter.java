package com.nexus.pdv.report.application;

import com.nexus.pdv.report.domain.ReportResult.ValueType;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatação pt-BR de valores de relatório para exportação. */
final class ReportFormatter {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ZoneId zone;

    ReportFormatter(ZoneId zone) {
        this.zone = zone;
    }

    String format(Object value, ValueType type) {
        if (value == null) {
            return "";
        }
        return switch (type) {
            case MONEY -> "R$ " + decimal(value, "#,##0.00");
            case NUMBER -> decimal(value, "#,##0.###");
            case INTEGER -> decimal(value, "#,##0");
            case PERCENT -> decimal(value, "#,##0.0") + "%";
            case DATE -> value instanceof LocalDate date ? date.format(DATE) : value.toString();
            case DATETIME -> value instanceof Instant instant ? DATE_TIME.format(instant.atZone(zone)) : value.toString();
            case TEXT -> value.toString();
        };
    }

    /** Número sem símbolo de moeda (CSV, para permitir cálculo em planilhas). */
    String formatPlain(Object value, ValueType type) {
        if (value == null) {
            return "";
        }
        return switch (type) {
            case MONEY -> decimal(value, "0.00");
            case NUMBER -> decimal(value, "0.###");
            case INTEGER -> decimal(value, "0");
            case PERCENT -> decimal(value, "0.0");
            default -> format(value, type);
        };
    }

    private static String decimal(Object value, String pattern) {
        BigDecimal number = value instanceof BigDecimal big ? big : new BigDecimal(value.toString());
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(PT_BR)).format(number);
    }
}
