package com.nexus.pdv.product.infrastructure;

import com.nexus.pdv.product.domain.Product;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtrado automaticamente pelo tenant corrente. */
public interface ProductRepository extends JpaRepository<Product, UUID> {

    /**
     * Trava produtos para alteração de estoque. Sempre em ordem de id para evitar deadlocks
     * entre transações concorrentes que travam conjuntos sobrepostos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> lockAllById(@Param("ids") Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> lockById(@Param("id") UUID id);

    Optional<Product> findFirstByBarcodeAndActiveTrue(String barcode);

    Optional<Product> findFirstBySkuIgnoreCaseAndActiveTrue(String sku);

    Optional<Product> findFirstBySkuIgnoreCase(String sku);

    Optional<Product> findFirstByBarcode(String barcode);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, UUID id);

    boolean existsByBarcode(String barcode);

    boolean existsByBarcodeAndIdNot(String barcode, UUID id);

    long countByCategoryId(UUID categoryId);

    String SEARCH_FILTER = """
            where (:q is null or lower(p.name) like lower(concat('%', :q, '%'))
                              or lower(p.sku) like lower(concat('%', :q, '%'))
                              or p.barcode like concat('%', :q, '%'))
              and (:categoryId is null or c.id = :categoryId)
              and (:active is null or p.active = :active)
              and (:situation is null
                   or (:situation = 'OUT_OF_STOCK' and p.currentStock <= 0)
                   or (:situation = 'LOW_STOCK' and p.currentStock > 0 and p.currentStock <= p.minimumStock)
                   or (:situation = 'NORMAL' and p.currentStock > 0 and p.currentStock > p.minimumStock))
            """;

    @Query(value = "select p from Product p left join fetch p.category c " + SEARCH_FILTER,
            countQuery = "select count(p) from Product p left join p.category c " + SEARCH_FILTER)
    Page<Product> search(@Param("q") String q, @Param("categoryId") UUID categoryId, @Param("active") Boolean active,
            @Param("situation") String situation, Pageable pageable);

    @Query("select count(p) from Product p where p.active = true and p.currentStock <= 0")
    long countOutOfStock();

    @Query("select count(p) from Product p where p.active = true and p.currentStock > 0 and p.currentStock <= p.minimumStock")
    long countLowStock();

    @Query("select count(p) from Product p where p.active = true and p.currentStock > 0")
    long countInStock();

    @Query("""
            select p from Product p
            where p.active = true and p.currentStock <= p.minimumStock
            order by p.currentStock asc, p.name asc
            """)
    List<Product> findAttentionNeeded(Pageable pageable);

    @Query("select coalesce(sum(p.currentStock * p.costPrice), 0) from Product p where p.active = true and p.currentStock > 0")
    BigDecimal stockValueAtCost();

    @Query("select coalesce(sum(p.currentStock * p.salePrice), 0) from Product p where p.active = true and p.currentStock > 0")
    BigDecimal stockValueAtSalePrice();
}
