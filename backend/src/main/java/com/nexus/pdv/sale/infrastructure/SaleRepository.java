package com.nexus.pdv.sale.infrastructure;

import com.nexus.pdv.sale.domain.Sale;
import com.nexus.pdv.sale.domain.SaleStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
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
public interface SaleRepository extends JpaRepository<Sale, UUID> {

    Optional<Sale> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> lockById(@Param("id") UUID id);

    Optional<Sale> findByNumber(long number);

    long countByCreatedAtGreaterThanEqual(Instant from);

    @Query(value = """
            select s from Sale s join fetch s.operator left join fetch s.customer
            where (:number is null or s.number = :number)
              and (:status is null or s.status = :status)
              and (:customerId is null or s.customer.id = :customerId)
              and (:operatorId is null or s.operator.id = :operatorId)
              and s.createdAt >= :from and s.createdAt < :to
            """,
            countQuery = """
            select count(s) from Sale s
            where (:number is null or s.number = :number)
              and (:status is null or s.status = :status)
              and (:customerId is null or s.customer.id = :customerId)
              and (:operatorId is null or s.operator.id = :operatorId)
              and s.createdAt >= :from and s.createdAt < :to
            """)
    Page<Sale> search(@Param("number") Long number, @Param("status") SaleStatus status,
            @Param("customerId") UUID customerId, @Param("operatorId") UUID operatorId, @Param("from") Instant from,
            @Param("to") Instant to, Pageable pageable);

    /*
     * Indicadores de faturamento são líquidos de devoluções: a venda devolvida continua COMPLETED
     * e o valor estornado (refundedTotal / returnedQuantity) é abatido na data da venda original.
     */

    /** (createdAt, total líquido) das vendas concluídas no período — agregação por dia feita no fuso do tenant. */
    @Query("""
            select s.createdAt, s.total - s.refundedTotal from Sale s
            where s.status = com.nexus.pdv.sale.domain.SaleStatus.COMPLETED
              and s.createdAt >= :from and s.createdAt < :to
            """)
    List<Object[]> completedTotals(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(s), coalesce(sum(s.total - s.refundedTotal), 0), coalesce(sum(s.discount), 0),
                   coalesce(sum(s.subtotal), 0)
            from Sale s
            where s.status = com.nexus.pdv.sale.domain.SaleStatus.COMPLETED
              and s.createdAt >= :from and s.createdAt < :to
            """)
    List<Object[]> completedSummary(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(s) from Sale s
            where s.status = com.nexus.pdv.sale.domain.SaleStatus.CANCELED
              and s.createdAt >= :from and s.createdAt < :to
            """)
    long countCanceled(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select p.method, count(distinct s.id), coalesce(sum(p.amount), 0) from Payment p join p.sale s
            where s.status = com.nexus.pdv.sale.domain.SaleStatus.COMPLETED
              and s.createdAt >= :from and s.createdAt < :to
            group by p.method
            """)
    List<Object[]> paymentBreakdown(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select i.product.id, i.descriptionSnapshot, sum(i.quantity - i.returnedQuantity),
                   sum(i.total * (i.quantity - i.returnedQuantity) / i.quantity),
                   sum(i.costPriceSnapshot * (i.quantity - i.returnedQuantity))
            from SaleItem i join i.sale s
            where s.status = com.nexus.pdv.sale.domain.SaleStatus.COMPLETED
              and s.createdAt >= :from and s.createdAt < :to
            group by i.product.id, i.descriptionSnapshot
            having sum(i.quantity - i.returnedQuantity) > 0
            order by sum(i.quantity - i.returnedQuantity) desc
            """)
    List<Object[]> topProducts(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("""
            select coalesce(sum(i.costPriceSnapshot * (i.quantity - i.returnedQuantity)), 0) from SaleItem i join i.sale s
            where s.status = com.nexus.pdv.sale.domain.SaleStatus.COMPLETED
              and s.createdAt >= :from and s.createdAt < :to
            """)
    BigDecimal costOfGoodsSold(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(s), coalesce(sum(s.total - s.refundedTotal), 0), max(s.createdAt) from Sale s
            where s.customer.id = :customerId and s.status = com.nexus.pdv.sale.domain.SaleStatus.COMPLETED
            """)
    List<Object[]> customerTotals(@Param("customerId") UUID customerId);

    @Query("""
            select s.customer.id, s.customer.name, count(s), sum(s.total - s.refundedTotal), max(s.createdAt) from Sale s
            where s.customer is not null and s.status = com.nexus.pdv.sale.domain.SaleStatus.COMPLETED
              and s.createdAt >= :from and s.createdAt < :to
            group by s.customer.id, s.customer.name
            order by sum(s.total - s.refundedTotal) desc
            """)
    List<Object[]> customerRanking(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("""
            select s from Sale s join fetch s.operator left join fetch s.customer
            where s.status = com.nexus.pdv.sale.domain.SaleStatus.COMPLETED
              and s.createdAt >= :from and s.createdAt < :to
            order by s.createdAt
            """)
    List<Sale> completedInPeriod(@Param("from") Instant from, @Param("to") Instant to);
}
