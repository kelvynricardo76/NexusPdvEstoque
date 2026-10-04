package com.nexus.pdv.report.application;

import com.nexus.pdv.report.domain.ReportResult;
import com.nexus.pdv.report.domain.ReportResult.Column;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Map;

/**
 * CSV compatível com Excel pt-BR: UTF-8 com BOM, separador ";" e vírgula decimal.
 * Protege contra injeção de fórmulas (células iniciadas por = + - @ recebem apóstrofo).
 */
public final class CsvReportWriter {

    private CsvReportWriter() {
    }

    public static byte[] write(ReportResult report, ZoneId zone) {
        ReportFormatter formatter = new ReportFormatter(zone);
        StringBuilder csv = new StringBuilder("﻿");
        csv.append(String.join(";", report.columns().stream().map(column -> escape(column.label())).toList())).append("\r\n");
        for (Map<String, Object> row : report.rows()) {
            boolean first = true;
            for (Column column : report.columns()) {
                if (!first) {
                    csv.append(';');
                }
                first = false;
                csv.append(escape(formatter.formatPlain(row.get(column.key()), column.type())));
            }
            csv.append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String escape(String value) {
        String safe = value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0 && !isNumeric(safe)) {
            safe = "'" + safe;
        }
        if (safe.contains(";") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static boolean isNumeric(String value) {
        return value.matches("^-?[0-9.,]+$");
    }
}
