package com.nexus.pdv.platform.infrastructure;

import com.nexus.pdv.platform.domain.PlatformAdmin;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, UUID> {

    Optional<PlatformAdmin> findByEmail(String email);

    boolean existsByEmail(String email);

    List<PlatformAdmin> findAllByOrderByNameAsc();
}
