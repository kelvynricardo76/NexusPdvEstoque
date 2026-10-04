package com.nexus.pdv.sale.api;

import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.sale.api.SaleDtos.CancelSaleRequest;
import com.nexus.pdv.sale.api.SaleDtos.FinalizeSaleRequest;
import com.nexus.pdv.sale.api.SaleDtos.ReceiptResponse;
import com.nexus.pdv.sale.api.SaleDtos.ReturnSaleRequest;
import com.nexus.pdv.sale.api.SaleDtos.SaleResponse;
import com.nexus.pdv.sale.api.SaleDtos.SaleSummary;
import com.nexus.pdv.sale.application.SaleService;
import com.nexus.pdv.sale.domain.SaleStatus;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Vendas / PDV")
@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;
    private final TenantSettingsService settingsService;
    private final Clock clock;

    public SaleController(SaleService saleService, TenantSettingsService settingsService, Clock clock) {
        this.saleService = saleService;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    /** Finaliza uma venda. Envie {@code Idempotency-Key} para tornar reenvios seguros. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission({Permission.PDV_ACCESS, Permission.SALE_CREATE})
    public SaleResponse finalizeSale(@Valid @RequestBody FinalizeSaleRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return saleService.finalizeSale(request, idempotencyKey);
    }

    @GetMapping
    @RequiresPermission(Permission.SALE_READ)
    public PageResponse<SaleSummary> search(@RequestParam(required = false) Long number,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID operatorId,
            @RequestParam(required = false) Period.Preset period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Period range = Period.resolve(period, from, to, settingsService.zone(), clock);
        return PageResponse.of(saleService.search(number, status, customerId, operatorId, range, pageable));
    }

    @GetMapping("/{id}")
    @RequiresPermission(value = {Permission.SALE_READ, Permission.SALE_CREATE}, any = true)
    public SaleResponse get(@PathVariable UUID id) {
        return saleService.get(id);
    }

    @GetMapping("/{id}/receipt")
    @RequiresPermission(value = {Permission.SALE_READ, Permission.SALE_CREATE}, any = true)
    public ReceiptResponse receipt(@PathVariable UUID id) {
        return saleService.receipt(id);
    }

    @PostMapping("/{id}/cancel")
    @RequiresPermission(Permission.SALE_CANCEL)
    public SaleResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelSaleRequest request) {
        return saleService.cancel(id, request.reason());
    }

    /** Devolução total ou parcial. Envie {@code Idempotency-Key} para tornar reenvios seguros. */
    @PostMapping("/{id}/returns")
    @RequiresPermission(Permission.SALE_REFUND)
    public SaleResponse registerReturn(@PathVariable UUID id, @Valid @RequestBody ReturnSaleRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return saleService.registerReturn(id, request, idempotencyKey);
    }
}
