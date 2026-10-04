package com.nexus.pdv.category.infrastructure;

import com.nexus.pdv.category.domain.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtrado automaticamente pelo tenant corrente. */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    @Query("""
            select c from Category c
            where (:q is null or lower(c.name) like lower(concat('%', :q, '%')))
              and (:active is null or c.active = :active)
            order by c.name
            """)
    List<Category> search(@Param("q") String q, @Param("active") Boolean active);

    @Query("select c.id, count(p) from Product p join p.category c group by c.id")
    List<Object[]> countProductsByCategory();
}
