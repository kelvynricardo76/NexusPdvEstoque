package com.nexus.pdv.customer.api;

import com.nexus.pdv.customer.api.CustomerDtos.CustomerDetail;
import com.nexus.pdv.customer.api.CustomerDtos.CustomerRequest;
import com.nexus.pdv.customer.api.CustomerDtos.CustomerSummary;
import com.nexus.pdv.customer.application.CustomerService;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.product.api.CatalogDtos.ActiveRequest;
import com.nexus.pdv.shared.access.RequiresPermission;
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

@Tag(name = "Clientes")
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @RequiresPermission(Permission.CUSTOMER_READ)
    public PageResponse<CustomerSummary> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(customerService.search(q, active, pageable));
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permission.CUSTOMER_READ)
    public CustomerDetail detail(@PathVariable UUID id) {
        return customerService.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.CUSTOMER_CREATE)
    public CustomerDetail create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.CUSTOMER_UPDATE)
    public CustomerDetail update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @PutMapping("/{id}/active")
    @RequiresPermission(Permission.CUSTOMER_DISABLE)
    public CustomerDetail setActive(@PathVariable UUID id, @RequestBody ActiveRequest request) {
        return customerService.setActive(id, request.active());
    }
}
