package com.nexus.pdv.platform.api;

import com.nexus.pdv.audit.api.AuditEntryResponse;
import com.nexus.pdv.audit.application.AuditQueryService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.platform.api.SuperAdminDtos.ChangePlanRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.CreateTenantRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.FeatureOverrideRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.LimitOverrideRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.TenantDetail;
import com.nexus.pdv.platform.api.SuperAdminDtos.TenantStatusRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.TenantSummary;
import com.nexus.pdv.platform.api.SuperAdminDtos.TenantUserView;
import com.nexus.pdv.platform.api.SuperAdminDtos.UpdateSubscriptionRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.UpdateTenantRequest;
import com.nexus.pdv.platform.application.PlatformSettingsService;
import com.nexus.pdv.platform.application.TenantAdministrationService;
import com.nexus.pdv.shared.web.PageResponse;
import com.nexus.pdv.tenant.application.ProvisionTenantCommand;
import com.nexus.pdv.tenant.application.TenantProvisioningService;
import com.nexus.pdv.tenant.domain.EffectiveTenantStatus;
import com.nexus.pdv.tenant.domain.Tenant;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Super Admin — Empresas")
@RestController
@RequestMapping("/api/super-admin/tenants")
public class SuperAdminTenantController {

    private final TenantAdministrationService administrationService;
    private final TenantProvisioningService provisioningService;
    private final PlatformSettingsService platformSettings;
    private final AuditQueryService auditQueryService;

    public SuperAdminTenantController(TenantAdministrationService administrationService,
            TenantProvisioningService provisioningService, PlatformSettingsService platformSettings,
            AuditQueryService auditQueryService) {
        this.administrationService = administrationService;
        this.provisioningService = provisioningService;
        this.platformSettings = platformSettings;
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public PageResponse<TenantSummary> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) EffectiveTenantStatus status,
            @RequestParam(required = false) String planCode,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(administrationService.search(q, status, planCode, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantDetail create(@Valid @RequestBody CreateTenantRequest request) {
        int trialDays = request.trialDays() != null ? request.trialDays() : platformSettings.defaultTrialDays();
        Tenant tenant = provisioningService.provision(new ProvisionTenantCommand(
                request.name(), request.tradeName(), request.document(), request.email(), request.phone(),
                request.planCode(), request.billingCycle(), trialDays, request.admin().name(), request.admin().email(),
                request.admin().phone(), request.admin().password()));
        return administrationService.detail(tenant.getId());
    }

    @GetMapping("/{id}")
    public TenantDetail detail(@PathVariable UUID id) {
        return administrationService.detail(id);
    }

    @PutMapping("/{id}")
    public TenantDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateTenantRequest request) {
        administrationService.update(id, request);
        return administrationService.detail(id);
    }

    @PostMapping("/{id}/status")
    public TenantDetail changeStatus(@PathVariable UUID id, @Valid @RequestBody TenantStatusRequest request) {
        administrationService.changeStatus(id, request.action(), request.reason());
        return administrationService.detail(id);
    }

    @PutMapping("/{id}/plan")
    public TenantDetail changePlan(@PathVariable UUID id, @Valid @RequestBody ChangePlanRequest request) {
        administrationService.changePlan(id, request);
        return administrationService.detail(id);
    }

    @PutMapping("/{id}/subscription")
    public TenantDetail updateSubscription(@PathVariable UUID id, @Valid @RequestBody UpdateSubscriptionRequest request) {
        administrationService.updateSubscription(id, request);
        return administrationService.detail(id);
    }

    @PutMapping("/{id}/feature-overrides")
    public TenantDetail setFeatureOverride(@PathVariable UUID id, @Valid @RequestBody FeatureOverrideRequest request) {
        administrationService.setFeatureOverride(id, request.featureCode(), request.enabled());
        return administrationService.detail(id);
    }

    @PutMapping("/{id}/limit-overrides")
    public TenantDetail setLimitOverride(@PathVariable UUID id, @Valid @RequestBody LimitOverrideRequest request) {
        administrationService.setLimitOverride(id, request.limitCode(), request.value());
        return administrationService.detail(id);
    }

    @GetMapping("/{id}/users")
    public List<TenantUserView> users(@PathVariable UUID id) {
        return administrationService.users(id);
    }

    @GetMapping("/{id}/audit")
    public PageResponse<AuditEntryResponse> audit(@PathVariable UUID id,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(auditQueryService.searchPlatform(id, action, from, to, pageable));
    }
}
