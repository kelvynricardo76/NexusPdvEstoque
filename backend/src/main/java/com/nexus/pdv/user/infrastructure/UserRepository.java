package com.nexus.pdv.user.infrastructure;

import com.nexus.pdv.user.domain.User;
import com.nexus.pdv.user.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consultas filtradas automaticamente pelo tenant corrente ({@code @TenantId}).
 * Buscas globais por e-mail (login, unicidade) devem rodar em {@code TenantContext.callAsSystem}.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByStatus(UserStatus status);

    long countByRoleId(UUID roleId);

    long countByRoleIdAndStatus(UUID roleId, UserStatus status);

    List<User> findByRoleId(UUID roleId);

    @Query("""
            select u from User u
            where (:q is null or lower(u.name) like lower(concat('%', :q, '%'))
                              or lower(u.email) like lower(concat('%', :q, '%')))
              and (:status is null or u.status = :status)
            """)
    Page<User> search(@Param("q") String q, @Param("status") UserStatus status, Pageable pageable);

    // Consultas do Super Admin (contexto root, sem filtro de tenant).

    @Query("select count(u) from User u where u.tenantId = :tenantId and u.status = :status")
    long countByTenantAndStatus(@Param("tenantId") UUID tenantId, @Param("status") UserStatus status);

    @Query("select u from User u where u.tenantId = :tenantId order by u.name")
    List<User> findAllOfTenant(@Param("tenantId") UUID tenantId);

    @Query("select u.tenantId, count(u) from User u where u.status = :status group by u.tenantId")
    List<Object[]> countByTenantGrouped(@Param("status") UserStatus status);
}
