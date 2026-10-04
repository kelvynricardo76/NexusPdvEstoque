package com.nexus.pdv.customer.infrastructure;

import com.nexus.pdv.customer.domain.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtrado automaticamente pelo tenant corrente. */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findFirstByDocument(String document);

    long countByActiveTrue();

    @Query("""
            select c from Customer c
            where (:q is null or lower(c.name) like lower(concat('%', :q, '%'))
                              or c.document like concat('%', :q, '%')
                              or c.phone like concat('%', :q, '%')
                              or lower(c.email) like lower(concat('%', :q, '%')))
              and (:active is null or c.active = :active)
            """)
    Page<Customer> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);
}
