package com.nexus.pdv.category.api;

import com.nexus.pdv.category.application.CategoryService;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.product.api.CatalogDtos.ActiveRequest;
import com.nexus.pdv.product.api.CatalogDtos.CategoryRequest;
import com.nexus.pdv.product.api.CatalogDtos.CategoryResponse;
import com.nexus.pdv.shared.access.RequiresPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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

@Tag(name = "Categorias")
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** Leitura também liberada para quem cadastra produtos (seleção de categoria no formulário). */
    @GetMapping
    @RequiresPermission(value = {Permission.CATEGORY_READ, Permission.PRODUCT_READ}, any = true)
    public List<CategoryResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active) {
        return categoryService.list(q, active);
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permission.CATEGORY_READ)
    public CategoryResponse get(@PathVariable UUID id) {
        return categoryService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.CATEGORY_CREATE)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return categoryService.create(request);
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.CATEGORY_UPDATE)
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @PutMapping("/{id}/active")
    @RequiresPermission(Permission.CATEGORY_DISABLE)
    public CategoryResponse setActive(@PathVariable UUID id, @RequestBody ActiveRequest request) {
        return categoryService.setActive(id, request.active());
    }
}
