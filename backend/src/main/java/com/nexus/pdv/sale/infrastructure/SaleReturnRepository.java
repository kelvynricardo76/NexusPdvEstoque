package com.nexus.pdv.sale.infrastructure;

import com.nexus.pdv.sale.domain.SaleReturn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtrado automaticamente pelo tenant corrente. */
public interface SaleReturnRepository extends JpaRepository<SaleReturn, UUID> {

    Optional<SaleReturn> findByIdempotencyKey(String idempotencyKey);

    @Query("select r from SaleReturn r join fetch r.user where r.sale.id = :saleId order by r.createdAt")
    List<SaleReturn> findBySale(@Param("saleId") UUID saleId);
}
