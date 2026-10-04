package com.nexus.pdv.supplier.api;

import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.product.api.CatalogDtos.ActiveRequest;
import com.nexus.pdv.product.api.CatalogDtos.SupplierRequest;
import com.nexus.pdv.product.api.CatalogDtos.SupplierResponse;
import com.nexus.pdv.shared.access.RequiresPermission;
import com.nexus.pdv.shared.web.PageResponse;
import com.nexus.pdv.supplier.application.SupplierService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

/** Fornecedores — exige a feature SUPPLIERS (via permissões). */
@Tag(name = "Fornecedores")
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    @RequiresPermission(Permission.SUPPLIER_READ)
    public PageResponse<SupplierResponse> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "legalName", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(supplierService.search(q, active, pageable));
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permission.SUPPLIER_READ)
    public SupplierResponse get(@PathVariable UUID id) {
        return supplierService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.SUPPLIER_CREATE)
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request) {
        return supplierService.create(request);
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.SUPPLIER_UPDATE)
    public SupplierResponse update(@PathVariable UUID id, @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(id, request);
    }

    @PutMapping("/{id}/active")
    @RequiresPermission(Permission.SUPPLIER_DISABLE)
    public SupplierResponse setActive(@PathVariable UUID id, @RequestBody ActiveRequest request) {
        return supplierService.setActive(id, request.active());
    }
}
