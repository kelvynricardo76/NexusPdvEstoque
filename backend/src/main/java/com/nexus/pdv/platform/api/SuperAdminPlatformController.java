package com.nexus.pdv.platform.api;

import com.nexus.pdv.audit.api.AuditEntryResponse;
import com.nexus.pdv.audit.application.AuditQueryService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.billing.application.BillingService;
import com.nexus.pdv.billing.domain.InvoiceStatus;
import com.nexus.pdv.billing.domain.SubscriptionInvoice;
import com.nexus.pdv.platform.api.SuperAdminDtos.CreatePlatformAdminRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.DashboardResponse;
import com.nexus.pdv.platform.api.SuperAdminDtos.InvoiceResponse;
import com.nexus.pdv.platform.api.SuperAdminDtos.IssueInvoiceRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.PlatformAdminResponse;
import com.nexus.pdv.platform.api.SuperAdminDtos.PlatformAdminStatusRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.PlatformSettingsRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.PlatformSettingsResponse;
import com.nexus.pdv.platform.api.SuperAdminDtos.SubscriptionView;
import com.nexus.pdv.platform.application.PlatformAdminService;
import com.nexus.pdv.platform.application.PlatformDashboardService;
import com.nexus.pdv.platform.application.PlatformSettingsService;
import com.nexus.pdv.platform.application.TenantAdministrationService;
import com.nexus.pdv.shared.web.PageResponse;
import com.nexus.pdv.subscription.domain.SubscriptionStatus;
import com.nexus.pdv.tenant.domain.Tenant;
import com.nexus.pdv.tenant.infrastructure.TenantRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Super Admin — Plataforma")
@RestController
@RequestMapping("/api/super-admin")
public class SuperAdminPlatformController {

    private final PlatformDashboardService dashboardService;
    private final TenantAdministrationService tenantService;
    private final BillingService billingService;
    private final PlatformAdminService adminService;
    private final PlatformSettingsService settingsService;
    private final AuditQueryService auditQueryService;
    private final TenantRepository tenantRepository;

    public SuperAdminPlatformController(PlatformDashboardService dashboardService,
            TenantAdministrationService tenantService, BillingService billingService, PlatformAdminService adminService,
            PlatformSettingsService settingsService, AuditQueryService auditQueryService,
            TenantRepository tenantRepository) {
        this.dashboardService = dashboardService;
        this.tenantService = tenantService;
        this.billingService = billingService;
        this.adminService = adminService;
        this.settingsService = settingsService;
        this.auditQueryService = auditQueryService;
        this.tenantRepository = tenantRepository;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return dashboardService.dashboard();
    }

    @GetMapping("/subscriptions")
    public PageResponse<SubscriptionView> subscriptions(@RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) String planCode, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(tenantService.subscriptions(status, planCode, pageable));
    }

    // ---------- Cobranças ----------

    @GetMapping("/billing/invoices")
    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> invoices(@RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) InvoiceStatus status, @PageableDefault(size = 20) Pageable pageable) {
        Page<SubscriptionInvoice> page = billingService.search(tenantId, status, pageable);
        Map<UUID, Tenant> tenants = tenantRepository.findAllById(page.getContent().stream()
                        .map(SubscriptionInvoice::getTenantId).distinct().toList())
                .stream().collect(Collectors.toMap(Tenant::getId, Function.identity()));
        return PageResponse.of(page, invoice -> toInvoice(invoice, tenants.get(invoice.getTenantId())));
    }

    @PostMapping("/billing/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse issueInvoice(@Valid @RequestBody IssueInvoiceRequest request) {
        SubscriptionInvoice invoice = billingService.issueInvoice(request.tenantId(), request.amount(),
                request.dueDate(), request.description());
        return toInvoice(invoice, tenantRepository.findById(invoice.getTenantId()).orElse(null));
    }

    @PostMapping("/billing/invoices/{id}/pay")
    public InvoiceResponse payInvoice(@PathVariable UUID id) {
        SubscriptionInvoice invoice = billingService.markPaid(id);
        return toInvoice(invoice, tenantRepository.findById(invoice.getTenantId()).orElse(null));
    }

    @PostMapping("/billing/invoices/{id}/cancel")
    public InvoiceResponse cancelInvoice(@PathVariable UUID id) {
        SubscriptionInvoice invoice = billingService.cancel(id);
        return toInvoice(invoice, tenantRepository.findById(invoice.getTenantId()).orElse(null));
    }

    // ---------- Administradores ----------

    @GetMapping("/admins")
    public List<PlatformAdminResponse> admins() {
        return adminService.list();
    }

    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformAdminResponse createAdmin(@Valid @RequestBody CreatePlatformAdminRequest request) {
        return adminService.create(request);
    }

    @PutMapping("/admins/{id}/status")
    public PlatformAdminResponse changeAdminStatus(@PathVariable UUID id,
            @RequestBody PlatformAdminStatusRequest request) {
        return adminService.changeStatus(id, request.active());
    }

    // ---------- Auditoria e configurações ----------

    @GetMapping("/audit")
    public PageResponse<AuditEntryResponse> audit(@RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 30) Pageable pageable) {
        return PageResponse.of(auditQueryService.searchPlatform(tenantId, action, from, to, pageable));
    }

    @GetMapping("/settings")
    public PlatformSettingsResponse settings() {
        return new PlatformSettingsResponse(settingsService.defaultTrialDays(), settingsService.pastDueGraceDays());
    }

    @PutMapping("/settings")
    public PlatformSettingsResponse updateSettings(@Valid @RequestBody PlatformSettingsRequest request) {
        settingsService.update(request.defaultTrialDays(), request.pastDueGraceDays());
        return settings();
    }

    private static InvoiceResponse toInvoice(SubscriptionInvoice invoice, Tenant tenant) {
        return new InvoiceResponse(invoice.getId(), invoice.getTenantId(), tenant == null ? null : tenant.displayName(),
                invoice.getDescription(), invoice.getAmount(), invoice.getDueDate(), invoice.getPeriodStart(),
                invoice.getPeriodEnd(), invoice.getStatus(), invoice.getPaidAt(), invoice.getBillingProvider(),
                invoice.getCreatedAt());
    }
}
