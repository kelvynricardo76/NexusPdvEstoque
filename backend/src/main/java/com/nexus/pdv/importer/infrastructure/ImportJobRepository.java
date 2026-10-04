package com.nexus.pdv.importer.infrastructure;

import com.nexus.pdv.importer.domain.ImportJob;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Filtrado automaticamente pelo tenant corrente. */
public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    Page<ImportJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
