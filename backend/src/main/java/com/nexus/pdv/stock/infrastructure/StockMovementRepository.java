package com.nexus.pdv.stock.infrastructure;

import com.nexus.pdv.stock.domain.StockMovement;
import com.nexus.pdv.stock.domain.StockMovementType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtrado automaticamente pelo tenant corrente. */
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    @Query(value = """
            select m from StockMovement m join fetch m.product left join fetch m.user
            where (:productId is null or m.product.id = :productId)
              and (:type is null or m.type = :type)
              and m.createdAt >= :from and m.createdAt < :to
            order by m.createdAt desc
            """,
            countQuery = """
            select count(m) from StockMovement m
            where (:productId is null or m.product.id = :productId)
              and (:type is null or m.type = :type)
              and m.createdAt >= :from and m.createdAt < :to
            """)
    Page<StockMovement> search(@Param("productId") UUID productId, @Param("type") StockMovementType type,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    List<StockMovement> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);

    long countByProductId(UUID productId);
}
