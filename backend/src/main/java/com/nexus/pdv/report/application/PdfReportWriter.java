package com.nexus.pdv.report.application;

import com.nexus.pdv.report.domain.ReportResult;
import com.nexus.pdv.report.domain.ReportResult.Column;
import com.nexus.pdv.report.domain.ReportResult.Metric;
import com.nexus.pdv.report.domain.ReportResult.ValueType;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gerador mínimo de PDF (A4 paisagem, Helvetica/WinAnsi) para relatórios tabulares,
 * sem dependências externas. Suficiente para impressão e arquivamento de relatórios.
 */
public final class PdfReportWriter {

    private static final float WIDTH = 842f;
    private static final float HEIGHT = 595f;
    private static final float MARGIN = 36f;
    private static final float ROW_HEIGHT = 16f;
    private static final float FONT_SIZE = 8.5f;
    private static final Charset WIN_ANSI = Charset.forName("windows-1252");
    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private PdfReportWriter() {
    }

    public static byte[] write(ReportResult report, String companyName, ZoneId zone) {
        ReportFormatter formatter = new ReportFormatter(zone);
        List<String> pages = new ArrayList<>();
        float[] widths = columnWidths(report.columns(), report.rows(), formatter);

        int rowIndex = 0;
        int pageNumber = 1;
        do {
            StringBuilder content = new StringBuilder();
            float y = HEIGHT - MARGIN;
            // Cabeçalho
            text(content, "F2", 15, MARGIN, y - 12, report.title());
            text(content, "F1", 9, MARGIN, y - 28, (companyName == null ? "" : companyName + "  •  ")
                    + "Período: " + formatter.format(report.from(), ValueType.DATE) + " a "
                    + formatter.format(report.to(), ValueType.DATE));
            line(content, MARGIN, y - 36, WIDTH - MARGIN, y - 36, 0.64f, 0.9f, 0.21f);
            y -= 52;

            if (pageNumber == 1 && !report.summary().isEmpty()) {
                float x = MARGIN;
                for (Metric metric : report.summary()) {
                    text(content, "F1", 7.5f, x, y, metric.label().toUpperCase());
                    text(content, "F2", 11, x, y - 13, formatter.format(metric.value(), metric.type()));
                    x += 150;
                    if (x > WIDTH - MARGIN - 140) {
                        x = MARGIN;
                        y -= 32;
                    }
                }
                y -= 34;
            }

            // Cabeçalho da tabela
            rect(content, MARGIN, y - 5, WIDTH - 2 * MARGIN, ROW_HEIGHT, 0.93f);
            float x = MARGIN + 4;
            for (int c = 0; c < report.columns().size(); c++) {
                text(content, "F2", FONT_SIZE, x, y, fit(report.columns().get(c).label(), widths[c]));
                x += widths[c];
            }
            y -= ROW_HEIGHT;

            while (rowIndex < report.rows().size() && y > MARGIN + 24) {
                Map<String, Object> row = report.rows().get(rowIndex++);
                x = MARGIN + 4;
                for (int c = 0; c < report.columns().size(); c++) {
                    Column column = report.columns().get(c);
                    text(content, "F1", FONT_SIZE, x, y, fit(formatter.format(row.get(column.key()), column.type()), widths[c]));
                    x += widths[c];
                }
                line(content, MARGIN, y - 5, WIDTH - MARGIN, y - 5, 0.88f, 0.88f, 0.88f);
                y -= ROW_HEIGHT;
            }
            if (report.rows().isEmpty()) {
                text(content, "F1", 10, MARGIN + 4, y, "Nenhum registro no período.");
            }

            text(content, "F1", 7.5f, MARGIN, MARGIN - 12, "Nexus PDV & Estoque  •  Gerado em "
                    + GENERATED.format(Instant.now().atZone(zone)));
            text(content, "F1", 7.5f, WIDTH - MARGIN - 50, MARGIN - 12, "Página " + pageNumber);
            pages.add(content.toString());
            pageNumber++;
        } while (rowIndex < report.rows().size());

        return assemble(pages);
    }

    private static float[] columnWidths(List<Column> columns, List<Map<String, Object>> rows, ReportFormatter formatter) {
        float available = WIDTH - 2 * MARGIN - 8;
        float[] weights = new float[columns.size()];
        float total = 0;
        for (int c = 0; c < columns.size(); c++) {
            Column column = columns.get(c);
            int max = column.label().length();
            for (int r = 0; r < Math.min(rows.size(), 200); r++) {
                max = Math.max(max, formatter.format(rows.get(r).get(column.key()), column.type()).length());
            }
            weights[c] = Math.min(Math.max(max, 4), 40);
            total += weights[c];
        }
        for (int c = 0; c < weights.length; c++) {
            weights[c] = weights[c] / total * available;
        }
        return weights;
    }

    private static String fit(String value, float width) {
        int maxChars = Math.max(3, (int) (width / (FONT_SIZE * 0.52f)) - 1);
        return value.length() <= maxChars ? value : value.substring(0, maxChars - 1) + "…";
    }

    private static void text(StringBuilder out, String font, float size, float x, float y, String value) {
        out.append("BT /").append(font).append(' ').append(fmt(size)).append(" Tf ")
                .append(fmt(x)).append(' ').append(fmt(y)).append(" Td (").append(escape(value)).append(") Tj ET\n");
    }

    private static void line(StringBuilder out, float x1, float y1, float x2, float y2, float r, float g, float b) {
        out.append(fmt(r)).append(' ').append(fmt(g)).append(' ').append(fmt(b)).append(" RG 0.6 w ")
                .append(fmt(x1)).append(' ').append(fmt(y1)).append(" m ").append(fmt(x2)).append(' ').append(fmt(y2))
                .append(" l S 0 0 0 RG\n");
    }

    private static void rect(StringBuilder out, float x, float y, float w, float h, float gray) {
        out.append(fmt(gray)).append(" g ").append(fmt(x)).append(' ').append(fmt(y)).append(' ').append(fmt(w))
                .append(' ').append(fmt(h)).append(" re f 0 g\n");
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if (ch == '(' || ch == ')' || ch == '\\') {
                escaped.append('\\');
            }
            escaped.append(ch == '\n' || ch == '\r' ? ' ' : ch);
        }
        return escaped.toString();
    }

    private static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static byte[] assemble(List<String> pageContents) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n%âãÏÓ\n");

        int pageCount = pageContents.size();
        int firstPageObj = 5;
        // 1 catálogo, 2 páginas, 3 fonte normal, 4 fonte negrito, depois (página, conteúdo) por página
        offsets.add(out.size());
        write(out, "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n");
        offsets.add(out.size());
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            kids.append(firstPageObj + i * 2).append(" 0 R ");
        }
        write(out, "2 0 obj << /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >> endobj\n");
        offsets.add(out.size());
        write(out, "3 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >> endobj\n");
        offsets.add(out.size());
        write(out, "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >> endobj\n");

        for (int i = 0; i < pageCount; i++) {
            int pageObj = firstPageObj + i * 2;
            int contentObj = pageObj + 1;
            byte[] stream = pageContents.get(i).getBytes(WIN_ANSI);
            offsets.add(out.size());
            write(out, pageObj + " 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 " + (int) WIDTH + " " + (int) HEIGHT
                    + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + contentObj + " 0 R >> endobj\n");
            offsets.add(out.size());
            write(out, contentObj + " 0 obj << /Length " + stream.length + " >> stream\n");
            out.writeBytes(stream);
            write(out, "\nendstream endobj\n");
        }

        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            table.append(String.format("%010d 00000 n \n", offset));
        }
        table.append("trailer << /Size ").append(offsets.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        write(out, table.toString());
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1));
    }
}
