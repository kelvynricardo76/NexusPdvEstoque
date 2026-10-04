package com.nexus.pdv.user.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.auth.application.PasswordResetService;
import com.nexus.pdv.entitlement.EntitlementService;
import com.nexus.pdv.permission.application.PermissionGrantPolicy;
import com.nexus.pdv.permission.domain.EffectivePermissions;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.permission.domain.PermissionEffect;
import com.nexus.pdv.permission.domain.Role;
import com.nexus.pdv.permission.infrastructure.RoleRepository;
import com.nexus.pdv.plan.domain.LimitCode;
import com.nexus.pdv.shared.access.AccessContext;
import com.nexus.pdv.shared.access.AccessContextService;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.security.PasswordPolicy;
import com.nexus.pdv.shared.text.Texts;
import com.nexus.pdv.tenant.infrastructure.TenantRepository;
import com.nexus.pdv.user.api.UserDtos.CreateUserRequest;
import com.nexus.pdv.user.api.UserDtos.UpdateUserRequest;
import com.nexus.pdv.user.api.UserDtos.UserDetailResponse;
import com.nexus.pdv.user.api.UserDtos.UserResponse;
import com.nexus.pdv.user.domain.User;
import com.nexus.pdv.user.domain.UserStatus;
import com.nexus.pdv.user.infrastructure.UserRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão de funcionários do tenant (sempre limitada ao tenant do contexto autenticado). */
@Service
public class UserService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final EntitlementService entitlementService;
    private final AccessContextService accessContextService;
    private final GlobalEmailRegistry emailRegistry;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetService passwordResetService;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, TenantRepository tenantRepository,
            EntitlementService entitlementService, AccessContextService accessContextService,
            GlobalEmailRegistry emailRegistry, PasswordEncoder passwordEncoder,
            PasswordResetService passwordResetService, AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
        this.entitlementService = entitlementService;
        this.accessContextService = accessContextService;
        this.emailRegistry = emailRegistry;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetService = passwordResetService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> search(String q, UserStatus status, Pageable pageable) {
        return userRepository.search(Texts.searchTerm(q), status, pageable).map(UserService::toResponse);
    }

    @Transactional(readOnly = true)
    public UserDetailResponse detail(UUID id) {
        return toDetail(find(id), accessContextService.current());
    }

    @Transactional
    public UserDetailResponse create(CreateUserRequest request) {
        AccessContext context = accessContextService.current();
        String email = User.normalizeEmail(request.email());
        Role role = findRole(request.roleId());
        PermissionGrantPolicy.assertCanAssignRole(context, role);
        if (emailRegistry.isTaken(email)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Já existe um usuário com este e-mail.");
        }
        boolean hasPassword = request.password() != null && !request.password().isBlank();
        if (hasPassword) {
            PasswordPolicy.validate(request.password(), email);
        }
        assertUserLimit(context);

        String rawPassword = hasPassword ? request.password() : randomSecret();
        User user = userRepository.save(new User(role, Texts.clean(request.name()), email, Texts.clean(request.phone()),
                passwordEncoder.encode(rawPassword)));
        if (!hasPassword) {
            passwordResetService.issueToken(user);
        }
        auditService.record(AuditAction.CREATE, "User", user.getId(), Map.of("role", role.getName()));
        return toDetail(user, context);
    }

    @Transactional
    public UserDetailResponse update(UUID id, UpdateUserRequest request) {
        AccessContext context = accessContextService.current();
        User user = find(id);
        boolean roleChanged = !user.getRole().getId().equals(request.roleId());
        if (roleChanged) {
            PermissionGrantPolicy.assertCanManage(context, user);
            Role newRole = findRole(request.roleId());
            PermissionGrantPolicy.assertCanAssignRole(context, newRole);
            if (user.isTenantAdmin() && !newRole.isTenantAdmin()) {
                assertNotLastActiveAdmin(user);
            }
            String previous = user.getRole().getName();
            user.changeRole(newRole);
            auditService.record(AuditAction.ROLE_CHANGE, "User", user.getId(), Map.of("from", previous, "to", newRole.getName()));
        } else if (user.isTenantAdmin() && !context.tenantAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Somente administradores podem alterar outro administrador.");
        }
        user.updateProfile(Texts.clean(request.name()), Texts.clean(request.phone()));
        auditService.record(AuditAction.UPDATE, "User", user.getId(), Map.of());
        return toDetail(user, context);
    }

    @Transactional
    public UserDetailResponse changeStatus(UUID id, boolean active) {
        AccessContext context = accessContextService.current();
        User user = find(id);
        PermissionGrantPolicy.assertCanManage(context, user);
        if (active == user.isActive()) {
            return toDetail(user, context);
        }
        if (active) {
            assertUserLimit(context);
            user.changeStatus(UserStatus.ACTIVE);
            auditService.record(AuditAction.ENABLE, "User", user.getId());
        } else {
            if (user.isTenantAdmin()) {
                assertNotLastActiveAdmin(user);
            }
            user.changeStatus(UserStatus.INACTIVE);
            auditService.record(AuditAction.DISABLE, "User", user.getId());
        }
        return toDetail(user, context);
    }

    @Transactional
    public UserDetailResponse replaceOverrides(UUID id, Map<String, PermissionEffect> requested) {
        AccessContext context = accessContextService.current();
        User user = find(id);
        PermissionGrantPolicy.assertCanManage(context, user);
        if (user.isTenantAdmin()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "Administradores já possuem todas as permissões.");
        }
        Map<Permission, PermissionEffect> overrides = new EnumMap<>(Permission.class);
        requested.forEach((code, effect) -> overrides.put(parsePermission(code), effect));

        var previousAllows = user.getPermissionOverrides().entrySet().stream()
                .filter(entry -> entry.getValue() == PermissionEffect.ALLOW).map(Map.Entry::getKey).toList();
        var requestedAllows = overrides.entrySet().stream()
                .filter(entry -> entry.getValue() == PermissionEffect.ALLOW).map(Map.Entry::getKey).toList();
        PermissionGrantPolicy.assertCanGrant(context, requestedAllows, previousAllows);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("before", stringify(user.getPermissionOverrides()));
        meta.put("after", stringify(overrides));
        user.replaceOverrides(overrides);
        auditService.record(AuditAction.PERMISSION_CHANGE, "User", user.getId(), meta);
        return toDetail(user, context);
    }

    @Transactional
    public void requestPasswordReset(UUID id) {
        AccessContext context = accessContextService.current();
        User user = find(id);
        if (user.isTenantAdmin() && !context.tenantAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "Usuário inativo.");
        }
        passwordResetService.issueToken(user);
        auditService.record(AuditAction.PASSWORD_RESET_REQUEST, "User", user.getId());
    }

    /** Trava o tenant e verifica MAX_USERS (serializa criações/reativações concorrentes). */
    private void assertUserLimit(AccessContext context) {
        tenantRepository.lockById(context.tenantId());
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        entitlementService.assertWithinLimit(context.tenantId(), LimitCode.MAX_USERS, activeUsers, 1);
    }

    private void assertNotLastActiveAdmin(User user) {
        long activeAdmins = userRepository.countByRoleIdAndStatus(user.getRole().getId(), UserStatus.ACTIVE);
        if (user.isActive() && activeAdmins <= 1) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "A empresa precisa de pelo menos um administrador ativo.");
        }
    }

    private User find(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Role findRole(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Cargo inválido."));
    }

    private static Permission parsePermission(String code) {
        try {
            return Permission.valueOf(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Permissão desconhecida: " + code);
        }
    }

    private static Map<String, String> stringify(Map<Permission, PermissionEffect> overrides) {
        Map<String, String> result = new LinkedHashMap<>();
        overrides.forEach((permission, effect) -> result.put(permission.name(), effect.name()));
        return result;
    }

    private static String randomSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "9a";
    }

    static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getRole().getId(),
                user.getRole().getName(), user.getRole().getCode() == null ? null : user.getRole().getCode().name(),
                user.getStatus().name(), user.getLastLoginAt(), user.getCreatedAt());
    }

    private static UserDetailResponse toDetail(User user, AccessContext context) {
        Map<String, PermissionEffect> overrides = new LinkedHashMap<>();
        user.getPermissionOverrides().forEach((permission, effect) -> overrides.put(permission.name(), effect));
        var effective = EffectivePermissions.resolve(user.getRole(), user.getPermissionOverrides(),
                        context.entitlements().features()).stream()
                .sorted(Comparator.comparing(Enum::ordinal)).map(Enum::name).toList();
        return new UserDetailResponse(toResponse(user), overrides, effective);
    }
}
