package com.nexus.pdv.importer.domain;

import com.nexus.pdv.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "import_job_rows")
public class ImportJobRow extends TenantOwnedEntity {

    public enum Status { PENDING, VALID, INVALID, IMPORTED, SKIPPED }

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "row_number", nullable = false, updatable = false)
    private int rowNumber;

    @Column(name = "data_json", nullable = false, length = 20000, updatable = false)
    private String dataJson;

    @Column(name = "errors_json", length = 4000)
    private String errorsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    protected ImportJobRow() {
    }

    public ImportJobRow(UUID jobId, int rowNumber, String dataJson) {
        this.jobId = jobId;
        this.rowNumber = rowNumber;
        this.dataJson = dataJson;
        this.status = Status.PENDING;
    }

    public void markValidation(String errorsJson) {
        this.errorsJson = errorsJson;
        this.status = errorsJson == null ? Status.VALID : Status.INVALID;
    }

    public void markImported() {
        this.status = Status.IMPORTED;
    }

    public void markSkipped() {
        this.status = Status.SKIPPED;
    }

    public UUID getJobId() {
        return jobId;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getDataJson() {
        return dataJson;
    }

    public String getErrorsJson() {
        return errorsJson;
    }

    public Status getStatus() {
        return status;
    }
}
