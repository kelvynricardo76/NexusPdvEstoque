package com.nexus.pdv.importer.domain;

import com.nexus.pdv.shared.persistence.TenantVersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_jobs")
public class ImportJob extends TenantVersionedEntity {

    public enum Status { UPLOADED, VALIDATED, COMPLETED, FAILED }

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20, updatable = false)
    private ImportEntityType entityType;

    @Column(name = "file_name", nullable = false, length = 200, updatable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false, length = 10, updatable = false)
    private String fileType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "columns_json", nullable = false, length = 8000, updatable = false)
    private String columnsJson;

    @Column(name = "mapping_json", length = 8000)
    private String mappingJson;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "invalid_rows", nullable = false)
    private int invalidRows;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ImportJob() {
    }

    public ImportJob(ImportEntityType entityType, String fileName, String fileType, String columnsJson, int totalRows,
            UUID createdBy) {
        this.entityType = entityType;
        this.fileName = fileName;
        this.fileType = fileType;
        this.columnsJson = columnsJson;
        this.totalRows = totalRows;
        this.createdBy = createdBy;
        this.status = Status.UPLOADED;
    }

    public void markValidated(String mappingJson, int valid, int invalid) {
        this.mappingJson = mappingJson;
        this.validRows = valid;
        this.invalidRows = invalid;
        this.status = Status.VALIDATED;
    }

    public void markCompleted(int created, int updated, int skipped, Instant when) {
        this.createdCount = created;
        this.updatedCount = updated;
        this.skippedCount = skipped;
        this.completedAt = when;
        this.status = Status.COMPLETED;
    }

    public ImportEntityType getEntityType() {
        return entityType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public Status getStatus() {
        return status;
    }

    public String getColumnsJson() {
        return columnsJson;
    }

    public String getMappingJson() {
        return mappingJson;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getValidRows() {
        return validRows;
    }

    public int getInvalidRows() {
        return invalidRows;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
