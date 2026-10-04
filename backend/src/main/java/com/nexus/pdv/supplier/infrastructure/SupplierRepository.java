package com.nexus.pdv.supplier.infrastructure;

import com.nexus.pdv.supplier.domain.Supplier;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtrado automaticamente pelo tenant corrente. */
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findFirstByDocument(String document);

    @Query("""
            select s from Supplier s
            where (:q is null or lower(s.legalName) like lower(concat('%', :q, '%'))
                              or lower(s.tradeName) like lower(concat('%', :q, '%'))
                              or s.document like concat('%', :q, '%'))
              and (:active is null or s.active = :active)
            """)
    Page<Supplier> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);
}
