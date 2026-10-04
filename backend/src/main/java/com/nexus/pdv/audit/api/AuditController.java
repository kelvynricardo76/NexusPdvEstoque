package com.nexus.pdv.audit.api;

import com.nexus.pdv.audit.application.AuditQueryService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.shared.access.RequiresPermission;
import com.nexus.pdv.shared.security.CurrentUser;
import com.nexus.pdv.shared.web.PageResponse;
import com.nexus.pdv.tenant.application.TenantSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Auditoria do próprio tenant (o tenant vem da sessão, nunca de parâmetro). */
@Tag(name = "Auditoria")
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditQueryService queryService;
    private final TenantSettingsService settingsService;

    public AuditController(AuditQueryService queryService, TenantSettingsService settingsService) {
        this.queryService = queryService;
        this.settingsService = settingsService;
    }

    @GetMapping
    @RequiresPermission(Permission.AUDIT_READ)
    public PageResponse<AuditEntryResponse> search(@RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 30) Pageable pageable) {
        return PageResponse.of(queryService.searchTenant(CurrentUser.requireTenantId(), action, entity, actorId, from,
                to, settingsService.zone(), pageable));
    }
}
