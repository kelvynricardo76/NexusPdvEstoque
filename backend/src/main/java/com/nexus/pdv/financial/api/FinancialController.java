package com.nexus.pdv.financial.api;

import com.nexus.pdv.financial.api.FinancialDtos.EntryRequest;
import com.nexus.pdv.financial.api.FinancialDtos.EntryResponse;
import com.nexus.pdv.financial.api.FinancialDtos.PayRequest;
import com.nexus.pdv.financial.api.FinancialDtos.SummaryResponse;
import com.nexus.pdv.financial.application.FinancialService;
import com.nexus.pdv.financial.domain.FinancialEntry;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.shared.access.RequiresPermission;
import com.nexus.pdv.shared.time.Period;
import com.nexus.pdv.shared.web.PageResponse;
import com.nexus.pdv.tenant.application.TenantSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

/** Financeiro do cliente — exige a feature FINANCIAL (via permissões). */
@Tag(name = "Financeiro")
@RestController
@RequestMapping("/api/financial")
public class FinancialController {

    private final FinancialService financialService;
    private final TenantSettingsService settingsService;
    private final Clock clock;

    public FinancialController(FinancialService financialService, TenantSettingsService settingsService, Clock clock) {
        this.financialService = financialService;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @GetMapping("/summary")
    @RequiresPermission(Permission.FINANCIAL_READ)
    public SummaryResponse summary(@RequestParam(required = false) Period.Preset period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return financialService.summary(Period.resolve(period, from, to, settingsService.zone(), clock));
    }

    /** Filtra pelo vencimento. Sem período informado: últimos 30 dias até 90 dias à frente. */
    @GetMapping("/entries")
    @RequiresPermission(Permission.FINANCIAL_READ)
    public PageResponse<EntryResponse> search(@RequestParam(required = false) FinancialEntry.Type type,
            @RequestParam(required = false) FinancialEntry.DisplayStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable) {
        LocalDate today = LocalDate.now(clock.withZone(settingsService.zone()));
        Period range = Period.custom(from != null ? from : today.minusDays(30), to != null ? to : today.plusDays(90),
                settingsService.zone());
        return PageResponse.of(financialService.search(type, status, q, range, pageable));
    }

    @GetMapping("/entries/{id}")
    @RequiresPermission(Permission.FINANCIAL_READ)
    public EntryResponse get(@PathVariable UUID id) {
        return financialService.get(id);
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.FINANCIAL_CREATE)
    public EntryResponse create(@Valid @RequestBody EntryRequest request) {
        return financialService.create(request);
    }

    @PutMapping("/entries/{id}")
    @RequiresPermission(Permission.FINANCIAL_UPDATE)
    public EntryResponse update(@PathVariable UUID id, @Valid @RequestBody EntryRequest request) {
        return financialService.update(id, request);
    }

    @PostMapping("/entries/{id}/pay")
    @RequiresPermission(Permission.FINANCIAL_UPDATE)
    public EntryResponse pay(@PathVariable UUID id, @RequestBody(required = false) PayRequest request) {
        return financialService.pay(id, request == null ? null : request.paymentDate());
    }

    @PostMapping("/entries/{id}/cancel")
    @RequiresPermission(Permission.FINANCIAL_CANCEL)
    public EntryResponse cancel(@PathVariable UUID id) {
        return financialService.cancel(id);
    }
}
