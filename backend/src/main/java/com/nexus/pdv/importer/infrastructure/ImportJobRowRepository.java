package com.nexus.pdv.importer.infrastructure;

import com.nexus.pdv.importer.domain.ImportJobRow;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Filtrado automaticamente pelo tenant corrente. */
public interface ImportJobRowRepository extends JpaRepository<ImportJobRow, UUID> {

    List<ImportJobRow> findByJobIdOrderByRowNumberAsc(UUID jobId);

    List<ImportJobRow> findByJobIdOrderByRowNumberAsc(UUID jobId, Pageable pageable);

    List<ImportJobRow> findByJobIdAndStatusOrderByRowNumberAsc(UUID jobId, ImportJobRow.Status status, Pageable pageable);
}
