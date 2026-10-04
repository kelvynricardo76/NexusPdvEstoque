package com.nexus.pdv.importer.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ImportDtos {

    private ImportDtos() {
    }

    public record FieldInfo(String key, String label, boolean required) {
    }

    /** Resultado do upload: colunas detectadas, campos de destino e mapeamento sugerido. */
    public record UploadResponse(UUID jobId, String entityType, String fileName, int totalRows, List<String> columns,
            List<FieldInfo> fields, Map<String, Integer> suggestedMapping, List<List<String>> sample) {
    }

    /** Mapeamento campo → índice da coluna no arquivo. */
    public record MappingRequest(@NotNull Map<String, Integer> mapping) {
    }

    public record RowPreview(int rowNumber, Map<String, String> values, List<String> errors, String status) {
    }

    public record ValidationResponse(UUID jobId, int totalRows, int validRows, int invalidRows,
            List<RowPreview> preview) {
    }

    /** {@code skipInvalid=true} confirma explicitamente que linhas inválidas serão ignoradas (e reportadas). */
    public record ConfirmRequest(boolean skipInvalid) {
    }

    public record ImportReport(UUID jobId, String entityType, String fileName, String status, int totalRows,
            int validRows, int invalidRows, int createdCount, int updatedCount, int skippedCount, Instant createdAt,
            Instant completedAt, List<RowPreview> rejectedRows) {
    }

    public record ImportJobSummary(UUID id, String entityType, String fileName, String status, int totalRows,
            int createdCount, int updatedCount, int skippedCount, Instant createdAt) {
    }
}
