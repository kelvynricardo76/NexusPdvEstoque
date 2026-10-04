package com.nexus.pdv.tenant.api;

import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.shared.access.RequiresPermission;
import com.nexus.pdv.shared.access.TenantAuthenticated;
import com.nexus.pdv.tenant.api.TenantSettingsDtos.LogoRequest;
import com.nexus.pdv.tenant.api.TenantSettingsDtos.SettingsRequest;
import com.nexus.pdv.tenant.api.TenantSettingsDtos.SettingsResponse;
import com.nexus.pdv.tenant.application.TenantSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Configurações da empresa")
@RestController
@RequestMapping("/api/settings")
public class TenantSettingsController {

    private final TenantSettingsService settingsService;

    public TenantSettingsController(TenantSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @TenantAuthenticated
    public SettingsResponse get() {
        return settingsService.get();
    }

    @PutMapping
    @RequiresPermission(Permission.SETTINGS_MANAGE)
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        return settingsService.update(request);
    }

    @PutMapping("/logo")
    @RequiresPermission(Permission.SETTINGS_MANAGE)
    public SettingsResponse changeLogo(@Valid @RequestBody LogoRequest request) {
        return settingsService.changeLogo(request.dataUrl());
    }

    @DeleteMapping("/logo")
    @RequiresPermission(Permission.SETTINGS_MANAGE)
    public SettingsResponse removeLogo() {
        return settingsService.removeLogo();
    }
}
