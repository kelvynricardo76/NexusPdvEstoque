package com.nexus.pdv.importer.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.entitlement.EntitlementService;
import com.nexus.pdv.importer.api.ImportDtos.FieldInfo;
import com.nexus.pdv.importer.api.ImportDtos.ImportJobSummary;
import com.nexus.pdv.importer.api.ImportDtos.ImportReport;
import com.nexus.pdv.importer.api.ImportDtos.RowPreview;
import com.nexus.pdv.importer.api.ImportDtos.UploadResponse;
import com.nexus.pdv.importer.api.ImportDtos.ValidationResponse;
import com.nexus.pdv.importer.domain.ImportEntityType;
import com.nexus.pdv.importer.domain.ImportEntityType.ImportField;
import com.nexus.pdv.importer.domain.ImportJob;
import com.nexus.pdv.importer.domain.ImportJobRow;
import com.nexus.pdv.importer.infrastructure.ImportJobRepository;
import com.nexus.pdv.importer.infrastructure.ImportJobRowRepository;
import com.nexus.pdv.plan.domain.LimitCode;
import com.nexus.pdv.product.infrastructure.ProductRepository;
import com.nexus.pdv.shared.access.AccessContext;
import com.nexus.pdv.shared.access.AccessContextService;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.tenant.infrastructure.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Importação em etapas: UPLOAD → MAPEAMENTO → VALIDAÇÃO → PREVIEW → CONFIRMAÇÃO → RELATÓRIO.
 * Registros inválidos nunca são importados silenciosamente: a confirmação com linhas inválidas
 * exige {@code skipInvalid=true}, e as linhas ignoradas constam no relatório.
 * As linhas ficam persistidas, o que permite mover a execução para processamento assíncrono.
 */
@Service
public class ImportService {

    public static final int MAX_ROWS = 5000;
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int PREVIEW_ROWS = 50;
    private static final int MAX_REJECTED_IN_REPORT = 500;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Integer>> MAPPING = new TypeReference<>() {
    };

    private final ImportJobRepository jobRepository;
    private final ImportJobRowRepository rowRepository;
    private final ImportRowProcessor processor;
    private final AccessContextService accessContextService;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final EntitlementService entitlementService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ImportService(ImportJobRepository jobRepository, ImportJobRowRepository rowRepository,
            ImportRowProcessor processor, AccessContextService accessContextService, ProductRepository productRepository,
            TenantRepository tenantRepository, EntitlementService entitlementService, AuditService auditService,
            ObjectMapper objectMapper, Clock clock) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.processor = processor;
        this.accessContextService = accessContextService;
        this.productRepository = productRepository;
        this.tenantRepository = tenantRepository;
        this.entitlementService = entitlementService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public UploadResponse upload(MultipartFile file, ImportEntityType type) {
        AccessContext context = authorize(type);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Envie um arquivo CSV ou XLSX.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "O arquivo deve ter no máximo 5 MB.");
        }
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Não foi possível ler o arquivo.");
        }
        TabularFile table = switch (extension) {
            case "csv", "txt" -> CsvParser.parse(bytes, MAX_ROWS);
            case "xlsx" -> XlsxParser.parse(bytes, MAX_ROWS);
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Formato não suportado. Use CSV ou XLSX.");
        };
        if (table.headers().isEmpty() || table.rows().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "O arquivo precisa de cabeçalho e ao menos uma linha.");
        }

        ImportJob job = jobRepository.save(new ImportJob(type, fileName, extension.equals("xlsx") ? "XLSX" : "CSV",
                toJson(table.headers()), table.rows().size(), context.userId()));
        List<ImportJobRow> rows = new ArrayList<>();
        for (int i = 0; i < table.rows().size(); i++) {
            rows.add(new ImportJobRow(job.getId(), i + 2, toJson(table.rows().get(i))));
        }
        rowRepository.saveAll(rows);

        return new UploadResponse(job.getId(), type.name(), fileName, table.rows().size(), table.headers(),
                fields(type), suggestMapping(type, table.headers()),
                table.rows().stream().limit(5).toList());
    }

    @Transactional
    public ValidationResponse validate(UUID jobId, Map<String, Integer> mapping) {
        ImportJob job = find(jobId);
        AccessContext context = authorize(job.getEntityType());
        if (job.getStatus() == ImportJob.Status.COMPLETED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "Esta importação já foi concluída.");
        }
        List<String> headers = fromJson(job.getColumnsJson(), STRING_LIST);
        for (ImportField field : job.getEntityType().fields()) {
            Integer column = mapping.get(field.key());
            if (field.required() && column == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mapeie o campo obrigatório \"" + field.label() + "\".");
            }
            if (column != null && (column < 0 || column >= headers.size())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Coluna inválida para \"" + field.label() + "\".");
            }
        }

        Set<String> seen = new HashSet<>();
        int valid = 0;
        int invalid = 0;
        List<RowPreview> preview = new ArrayList<>();
        for (ImportJobRow row : rowRepository.findByJobIdOrderByRowNumberAsc(jobId)) {
            Map<String, String> values = mapRow(job.getEntityType(), mapping, fromJson(row.getDataJson(), STRING_LIST));
            List<String> errors = processor.validate(job.getEntityType(), values, context, seen);
            row.markValidation(errors.isEmpty() ? null : toJson(errors));
            if (errors.isEmpty()) {
                valid++;
            } else {
                invalid++;
            }
            if (preview.size() < PREVIEW_ROWS) {
                preview.add(new RowPreview(row.getRowNumber(), values, errors, row.getStatus().name()));
            }
        }
        job.markValidated(toJson(mapping), valid, invalid);
        return new ValidationResponse(jobId, job.getTotalRows(), valid, invalid, preview);
    }

    @Transactional
    public ImportReport confirm(UUID jobId, boolean skipInvalid) {
        ImportJob job = find(jobId);
        AccessContext context = authorize(job.getEntityType());
        if (job.getStatus() != ImportJob.Status.VALIDATED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE,
                    job.getStatus() == ImportJob.Status.COMPLETED ? "Esta importação já foi concluída."
                            : "Valide o arquivo antes de confirmar.");
        }
        if (job.getInvalidRows() > 0 && !skipInvalid) {
            throw new BusinessException(ErrorCode.CONFIRMATION_REQUIRED,
                    job.getInvalidRows() + " linha(s) inválida(s) não serão importadas. Confirme para continuar.");
        }
        Map<String, Integer> mapping = fromJson(job.getMappingJson(), MAPPING);
        List<ImportJobRow> rows = rowRepository.findByJobIdOrderByRowNumberAsc(jobId);

        if (job.getEntityType() == ImportEntityType.PRODUCTS) {
            long newProducts = rows.stream()
                    .filter(row -> row.getStatus() == ImportJobRow.Status.VALID)
                    .filter(row -> processor.createsNewProduct(mapRow(job.getEntityType(), mapping,
                            fromJson(row.getDataJson(), STRING_LIST))))
                    .count();
            tenantRepository.lockById(context.tenantId());
            entitlementService.assertWithinLimit(context.tenantId(), LimitCode.MAX_PRODUCTS, productRepository.count(),
                    newProducts);
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (ImportJobRow row : rows) {
            if (row.getStatus() != ImportJobRow.Status.VALID) {
                row.markSkipped();
                skipped++;
                continue;
            }
            Map<String, String> values = mapRow(job.getEntityType(), mapping, fromJson(row.getDataJson(), STRING_LIST));
            if (processor.apply(job.getEntityType(), values, context) == ImportRowProcessor.Outcome.CREATED) {
                created++;
            } else {
                updated++;
            }
            row.markImported();
        }
        job.markCompleted(created, updated, skipped, clock.instant());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("entity", job.getEntityType().name());
        meta.put("file", job.getFileName());
        meta.put("created", created);
        meta.put("updated", updated);
        meta.put("skipped", skipped);
        auditService.record(AuditAction.IMPORT, "ImportJob", job.getId(), meta);
        return report(job);
    }

    @Transactional(readOnly = true)
    public ImportReport get(UUID jobId) {
        return report(find(jobId));
    }

    @Transactional(readOnly = true)
    public Page<ImportJobSummary> list(Pageable pageable) {
        return jobRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(job -> new ImportJobSummary(job.getId(), job.getEntityType().name(), job.getFileName(),
                        job.getStatus().name(), job.getTotalRows(), job.getCreatedCount(), job.getUpdatedCount(),
                        job.getSkippedCount(), job.getCreatedAt()));
    }

    /** Modelo CSV com os cabeçalhos esperados para o tipo informado. */
    public byte[] template(ImportEntityType type) {
        String header = String.join(";", type.fields().stream().map(ImportField::label).toList());
        return ("﻿" + header + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    public List<FieldInfo> fields(ImportEntityType type) {
        return type.fields().stream().map(field -> new FieldInfo(field.key(), field.label(), field.required())).toList();
    }

    private ImportReport report(ImportJob job) {
        Map<String, Integer> mapping = job.getMappingJson() == null ? Map.of() : fromJson(job.getMappingJson(), MAPPING);
        List<RowPreview> rejected = new ArrayList<>();
        var statuses = List.of(ImportJobRow.Status.INVALID, ImportJobRow.Status.SKIPPED);
        for (ImportJobRow.Status status : statuses) {
            for (ImportJobRow row : rowRepository.findByJobIdAndStatusOrderByRowNumberAsc(job.getId(), status,
                    PageRequest.of(0, MAX_REJECTED_IN_REPORT))) {
                if (row.getErrorsJson() == null) {
                    continue;
                }
                rejected.add(new RowPreview(row.getRowNumber(),
                        mapRow(job.getEntityType(), mapping, fromJson(row.getDataJson(), STRING_LIST)),
                        fromJson(row.getErrorsJson(), STRING_LIST), row.getStatus().name()));
            }
        }
        return new ImportReport(job.getId(), job.getEntityType().name(), job.getFileName(), job.getStatus().name(),
                job.getTotalRows(), job.getValidRows(), job.getInvalidRows(), job.getCreatedCount(),
                job.getUpdatedCount(), job.getSkippedCount(), job.getCreatedAt(), job.getCompletedAt(), rejected);
    }

    private AccessContext authorize(ImportEntityType type) {
        AccessContext context = accessContextService.current();
        context.require(type.requiredPermission());
        return context;
    }

    private static Map<String, String> mapRow(ImportEntityType type, Map<String, Integer> mapping, List<String> values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (ImportField field : type.fields()) {
            Integer column = mapping.get(field.key());
            row.put(field.key(), column != null && column < values.size() ? values.get(column) : null);
        }
        return row;
    }

    static Map<String, Integer> suggestMapping(ImportEntityType type, List<String> headers) {
        Map<String, Integer> suggestion = new LinkedHashMap<>();
        for (ImportField field : type.fields()) {
            for (int i = 0; i < headers.size(); i++) {
                String header = normalize(headers.get(i));
                boolean matches = header.equals(normalize(field.key())) || header.equals(normalize(field.label()));
                for (String alias : field.aliases()) {
                    matches |= header.equals(normalize(alias));
                }
                if (matches && !suggestion.containsValue(i)) {
                    suggestion.put(field.key(), i);
                    break;
                }
            }
        }
        return suggestion;
    }

    private static String normalize(String value) {
        String noAccents = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String sanitizeFileName(String name) {
        String base = name == null ? "arquivo" : name.replaceAll("[\\\\/]", "_");
        base = base.replaceAll("[^\\p{L}\\p{N}._ -]", "");
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    private ImportJob find(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Importação não encontrada."));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
