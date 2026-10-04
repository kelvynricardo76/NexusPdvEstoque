package com.nexus.pdv.platform.api;

import com.nexus.pdv.platform.api.SuperAdminDtos.FeatureResponse;
import com.nexus.pdv.platform.api.SuperAdminDtos.FeatureUpdateRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.LimitResponse;
import com.nexus.pdv.platform.api.SuperAdminDtos.LimitUpdateRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.PlanRequest;
import com.nexus.pdv.platform.api.SuperAdminDtos.PlanResponse;
import com.nexus.pdv.platform.application.PlanAdministrationService;
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

@Tag(name = "Super Admin — Planos e catálogo")
@RestController
@RequestMapping("/api/super-admin")
public class SuperAdminCatalogController {

    private final PlanAdministrationService planService;

    public SuperAdminCatalogController(PlanAdministrationService planService) {
        this.planService = planService;
    }

    @GetMapping("/plans")
    public List<PlanResponse> plans() {
        return planService.list();
    }

    @GetMapping("/plans/{id}")
    public PlanResponse plan(@PathVariable UUID id) {
        return planService.get(id);
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse createPlan(@Valid @RequestBody PlanRequest request) {
        return planService.create(request);
    }

    /** {@code confirmImpact=true} é obrigatório quando a alteração reduz direitos de planos em uso. */
    @PutMapping("/plans/{id}")
    public PlanResponse updatePlan(@PathVariable UUID id, @Valid @RequestBody PlanRequest request,
            @RequestParam(defaultValue = "false") boolean confirmImpact) {
        return planService.update(id, request, confirmImpact);
    }

    @GetMapping("/features")
    public List<FeatureResponse> features() {
        return planService.features();
    }

    @PutMapping("/features/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateFeature(@PathVariable String code, @Valid @RequestBody FeatureUpdateRequest request) {
        planService.updateFeature(code, request);
    }

    @GetMapping("/limits")
    public List<LimitResponse> limits() {
        return planService.limits();
    }

    @PutMapping("/limits/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateLimit(@PathVariable String code, @Valid @RequestBody LimitUpdateRequest request) {
        planService.updateLimit(code, request);
    }
}
