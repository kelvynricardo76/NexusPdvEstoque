package com.nexus.pdv.permission.infrastructure;

import com.nexus.pdv.permission.domain.Role;
import com.nexus.pdv.permission.domain.RoleCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas filtradas automaticamente pelo tenant corrente ({@code @TenantId}). */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(RoleCode code);

    List<Role> findAllByOrderBySystemRoleDescNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
