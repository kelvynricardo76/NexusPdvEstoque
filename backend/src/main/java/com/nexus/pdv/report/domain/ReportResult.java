package com.nexus.pdv.report.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Resultado genérico de relatório: resumo, tabela e série opcional para gráfico.
 * O mesmo objeto alimenta a tela e as exportações (CSV/PDF).
 */
public record ReportResult(
        String type,
        String title,
        LocalDate from,
        LocalDate to,
        List<Metric> summary,
        List<Column> columns,
        List<Map<String, Object>> rows,
        List<ChartPoint> chart) {

    public enum ValueType { TEXT, MONEY, NUMBER, INTEGER, DATE, DATETIME, PERCENT }

    public record Column(String key, String label, ValueType type) {
    }

    public record Metric(String label, Object value, ValueType type) {
    }

    public record ChartPoint(String label, Object value) {
    }
}
