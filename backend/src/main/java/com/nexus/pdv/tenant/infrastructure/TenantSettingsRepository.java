package com.nexus.pdv.tenant.infrastructure;

import com.nexus.pdv.tenant.domain.TenantSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, UUID> {
}
