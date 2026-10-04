package com.nexus.pdv.product.api;

import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.product.api.CatalogDtos.ActiveRequest;
import com.nexus.pdv.product.api.CatalogDtos.ProductRequest;
import com.nexus.pdv.product.api.CatalogDtos.ProductResponse;
import com.nexus.pdv.product.application.ProductService;
import com.nexus.pdv.product.domain.StockSituation;
import com.nexus.pdv.shared.access.RequiresPermission;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.web.PageResponse;
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

@Tag(name = "Produtos")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @RequiresPermission(value = {Permission.PRODUCT_READ, Permission.PDV_ACCESS}, any = true)
    public PageResponse<ProductResponse> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) StockSituation situation,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(productService.search(q, categoryId, active, situation, pageable));
    }

    /** Busca exata por código de barras ou SKU (leitor de código de barras no PDV). */
    @GetMapping("/by-code/{code}")
    @RequiresPermission(value = {Permission.PRODUCT_READ, Permission.PDV_ACCESS}, any = true)
    public ProductResponse byCode(@PathVariable String code) {
        return productService.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Produto não encontrado para o código informado."));
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permission.PRODUCT_READ)
    public ProductResponse get(@PathVariable UUID id) {
        return productService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.PRODUCT_CREATE)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.PRODUCT_UPDATE)
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @PutMapping("/{id}/active")
    @RequiresPermission(Permission.PRODUCT_DISABLE)
    public ProductResponse setActive(@PathVariable UUID id, @RequestBody ActiveRequest request) {
        return productService.setActive(id, request.active());
    }
}
