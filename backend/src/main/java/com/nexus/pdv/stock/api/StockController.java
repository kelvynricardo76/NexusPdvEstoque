package com.nexus.pdv.stock.api;

import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.product.api.CatalogDtos.StockAdjustRequest;
import com.nexus.pdv.product.api.CatalogDtos.StockEntryRequest;
import com.nexus.pdv.product.api.CatalogDtos.StockItemResponse;
import com.nexus.pdv.product.api.CatalogDtos.StockMovementResponse;
import com.nexus.pdv.product.api.CatalogDtos.StockSummaryResponse;
import com.nexus.pdv.product.domain.StockSituation;
import com.nexus.pdv.shared.access.RequiresPermission;
import com.nexus.pdv.shared.time.Period;
import com.nexus.pdv.shared.web.PageResponse;
import com.nexus.pdv.stock.application.StockService;
import com.nexus.pdv.stock.domain.StockMovementType;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Estoque")
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;
    private final TenantSettingsService settingsService;
    private final Clock clock;

    public StockController(StockService stockService, TenantSettingsService settingsService, Clock clock) {
        this.stockService = stockService;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @GetMapping
    @RequiresPermission(Permission.STOCK_READ)
    public PageResponse<StockItemResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) StockSituation situation,
            @RequestParam(required = false) UUID categoryId,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(stockService.list(q, situation, categoryId, pageable));
    }

    @GetMapping("/summary")
    @RequiresPermission(Permission.STOCK_READ)
    public StockSummaryResponse summary() {
        return stockService.summary();
    }

    @GetMapping("/movements")
    @RequiresPermission(Permission.STOCK_MOVEMENT_READ)
    public PageResponse<StockMovementResponse> movements(@RequestParam(required = false) UUID productId,
            @RequestParam(required = false) StockMovementType type,
            @RequestParam(required = false) Period.Preset period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 30) Pageable pageable) {
        Period range = Period.resolve(period, from, to, settingsService.zone(), clock);
        return PageResponse.of(stockService.movements(productId, type, range, pageable));
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.STOCK_ENTRY)
    public StockMovementResponse entry(@Valid @RequestBody StockEntryRequest request) {
        return stockService.registerEntry(request);
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.STOCK_ADJUST)
    public StockMovementResponse adjust(@Valid @RequestBody StockAdjustRequest request) {
        return stockService.adjust(request);
    }
}
