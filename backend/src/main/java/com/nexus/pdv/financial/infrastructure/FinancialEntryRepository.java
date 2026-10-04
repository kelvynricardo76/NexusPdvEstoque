package com.nexus.pdv.financial.infrastructure;

import com.nexus.pdv.financial.domain.FinancialEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtrado automaticamente pelo tenant corrente. */
public interface FinancialEntryRepository extends JpaRepository<FinancialEntry, UUID> {

    /**
     * {@code displayStatus}: PENDING (a vencer), OVERDUE (vencida), PAID, CANCELED ou nulo (todas).
     */
    @Query("""
            select e from FinancialEntry e
            where (:type is null or e.type = :type)
              and (:q is null or lower(e.description) like lower(concat('%', :q, '%'))
                              or lower(e.category) like lower(concat('%', :q, '%')))
              and e.dueDate >= :from and e.dueDate <= :to
              and (:displayStatus is null
                   or (:displayStatus = 'PENDING' and e.status = com.nexus.pdv.financial.domain.FinancialEntry.Status.PENDING and e.dueDate >= :today)
                   or (:displayStatus = 'OVERDUE' and e.status = com.nexus.pdv.financial.domain.FinancialEntry.Status.PENDING and e.dueDate < :today)
                   or (:displayStatus = 'PAID' and e.status = com.nexus.pdv.financial.domain.FinancialEntry.Status.PAID)
                   or (:displayStatus = 'CANCELED' and e.status = com.nexus.pdv.financial.domain.FinancialEntry.Status.CANCELED))
            """)
    Page<FinancialEntry> search(@Param("type") FinancialEntry.Type type, @Param("q") String q,
            @Param("displayStatus") String displayStatus, @Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("today") LocalDate today, Pageable pageable);

    @Query("""
            select coalesce(sum(e.amount), 0) from FinancialEntry e
            where e.type = :type and e.status = com.nexus.pdv.financial.domain.FinancialEntry.Status.PAID
              and e.paymentDate >= :from and e.paymentDate <= :to
            """)
    BigDecimal sumPaid(@Param("type") FinancialEntry.Type type, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select coalesce(sum(e.amount), 0), count(e) from FinancialEntry e
            where e.type = :type and e.status = com.nexus.pdv.financial.domain.FinancialEntry.Status.PENDING
            """)
    List<Object[]> pendingTotals(@Param("type") FinancialEntry.Type type);

    @Query("""
            select coalesce(sum(e.amount), 0), count(e) from FinancialEntry e
            where e.type = :type and e.status = com.nexus.pdv.financial.domain.FinancialEntry.Status.PENDING
              and e.dueDate < :today
            """)
    List<Object[]> overdueTotals(@Param("type") FinancialEntry.Type type, @Param("today") LocalDate today);

    @Query("""
            select e.type, coalesce(e.category, 'Sem categoria'), sum(e.amount) from FinancialEntry e
            where e.status = com.nexus.pdv.financial.domain.FinancialEntry.Status.PAID
              and e.paymentDate >= :from and e.paymentDate <= :to
            group by e.type, coalesce(e.category, 'Sem categoria')
            order by sum(e.amount) desc
            """)
    List<Object[]> paidByCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
