package com.nexus.pdv.permission.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.permission.domain.Role;
import com.nexus.pdv.permission.infrastructure.RoleRepository;
import com.nexus.pdv.shared.access.AccessContext;
import com.nexus.pdv.shared.access.AccessContextService;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.text.Texts;
import com.nexus.pdv.user.api.UserDtos.PermissionResponse;
import com.nexus.pdv.user.api.UserDtos.RoleRequest;
import com.nexus.pdv.user.api.UserDtos.RoleResponse;
import com.nexus.pdv.user.infrastructure.UserRepository;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cargos do tenant (padrão + personalizados) e catálogo de permissões. */
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AccessContextService accessContextService;
    private final AuditService auditService;

    public RoleService(RoleRepository roleRepository, UserRepository userRepository,
            AccessContextService accessContextService, AuditService auditService) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.accessContextService = accessContextService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepository.findAllByOrderBySystemRoleDescNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        AccessContext context = accessContextService.current();
        String name = Texts.clean(request.name());
        if (roleRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Já existe um cargo com este nome.");
        }
        Set<Permission> permissions = parse(request.permissions());
        PermissionGrantPolicy.assertCanGrant(context, permissions, Set.of());
        Role role = roleRepository.save(Role.custom(name, Texts.clean(request.description()), permissions));
        auditService.record(AuditAction.CREATE, "Role", role.getId(), Map.of("name", name, "permissions", names(permissions)));
        return toResponse(role);
    }

    @Transactional
    public RoleResponse update(UUID id, RoleRequest request) {
        AccessContext context = accessContextService.current();
        Role role = find(id);
        if (role.isSystemRole()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "O cargo Administrador não pode ser alterado.");
        }
        if (role.getId().equals(context.roleId()) && !context.tenantAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Você não pode alterar o próprio cargo.");
        }
        String name = Texts.clean(request.name());
        if (roleRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Já existe um cargo com este nome.");
        }
        Set<Permission> permissions = parse(request.permissions());
        Set<Permission> previous = role.getPermissions();
        PermissionGrantPolicy.assertCanGrant(context, permissions, previous);
        role.update(name, Texts.clean(request.description()), permissions);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("before", names(previous));
        meta.put("after", names(permissions));
        auditService.record(AuditAction.PERMISSION_CHANGE, "Role", role.getId(), meta);
        return toResponse(role);
    }

    @Transactional
    public void delete(UUID id) {
        Role role = find(id);
        if (role.getCode() != null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "Cargos padrão não podem ser excluídos.");
        }
        if (userRepository.countByRoleId(id) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "Existem usuários com este cargo. Altere-os antes de excluir.");
        }
        roleRepository.delete(role);
        auditService.record(AuditAction.DELETE, "Role", id, Map.of("name", role.getName()));
    }

    /** Catálogo completo, indicando quais permissões o plano do tenant torna utilizáveis. */
    @Transactional(readOnly = true)
    public List<PermissionResponse> catalog() {
        AccessContext context = accessContextService.current();
        return Arrays.stream(Permission.values())
                .map(permission -> new PermissionResponse(
                        permission.name(),
                        permission.module().name(),
                        permission.module().label(),
                        permission.action().name(),
                        permission.label(),
                        permission.feature() == null ? null : permission.feature().name(),
                        permission.feature() == null || context.hasFeature(permission.feature())))
                .toList();
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getCode() == null ? null : role.getCode().name(), role.getName(),
                role.getDescription(), role.isSystemRole(), names(role.getPermissions()),
                userRepository.countByRoleId(role.getId()));
    }

    private Role find(UUID id) {
        return roleRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static Set<Permission> parse(Set<String> codes) {
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        for (String code : codes) {
            try {
                result.add(Permission.valueOf(code));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Permissão desconhecida: " + code);
            }
        }
        return result;
    }

    private static List<String> names(Set<Permission> permissions) {
        return permissions.stream().sorted(Comparator.comparing(Enum::ordinal)).map(Enum::name).toList();
    }
}
