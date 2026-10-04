package com.nexus.pdv.platform.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.platform.api.SuperAdminDtos.CreatePlatformAdminRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.PlatformAdminResponse;
import com.nexus.pdv.platform.domain.PlatformAdmin;
import com.nexus.pdv.platform.infrastructure.PlatformAdminRepository;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.security.CurrentUser;
import com.nexus.pdv.shared.security.PasswordPolicy;
import com.nexus.pdv.shared.text.Texts;
import com.nexus.pdv.user.domain.User;
import com.nexus.pdv.user.domain.UserStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administradores da Nexus Development (SUPER_ADMIN). */
@Service
public class PlatformAdminService {

    private final PlatformAdminRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public PlatformAdminService(PlatformAdminRepository repository, PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PlatformAdminResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(PlatformAdminService::toResponse).toList();
    }

    @Transactional
    public PlatformAdminResponse create(CreatePlatformAdminRequest request) {
        String email = User.normalizeEmail(request.email());
        if (repository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Já existe um administrador com este e-mail.");
        }
        PasswordPolicy.validate(request.password(), email);
        PlatformAdmin admin = repository.save(
                new PlatformAdmin(Texts.clean(request.name()), email, passwordEncoder.encode(request.password())));
        auditService.record(AuditAction.CREATE, "PlatformAdmin", admin.getId(), Map.of());
        return toResponse(admin);
    }

    @Transactional
    public PlatformAdminResponse changeStatus(UUID id, boolean active) {
        if (CurrentUser.require().id().equals(id) && !active) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "Você não pode desativar o próprio acesso.");
        }
        PlatformAdmin admin = repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        admin.changeStatus(active ? UserStatus.ACTIVE : UserStatus.INACTIVE);
        auditService.record(active ? AuditAction.ENABLE : AuditAction.DISABLE, "PlatformAdmin", id, Map.of());
        return toResponse(admin);
    }

    private static PlatformAdminResponse toResponse(PlatformAdmin admin) {
        return new PlatformAdminResponse(admin.getId(), admin.getName(), admin.getEmail(), admin.getStatus().name(),
                admin.getLastLoginAt(), admin.getCreatedAt());
    }
}
