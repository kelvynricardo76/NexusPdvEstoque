package com.nexus.pdv.tenant.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class TenantSettingsDtos {

    private TenantSettingsDtos() {
    }

    public record SettingsRequest(
            @NotBlank @Size(max = 150) String companyName,
            @Size(max = 150) String tradeName,
            @Size(max = 20) String document,
            @Size(max = 30) String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 300) String address,
            @NotBlank @Size(max = 60) String timezone,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor inválida (use #RRGGBB)") String primaryColor,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor inválida (use #RRGGBB)") String secondaryColor,
            boolean allowNegativeStock) {
    }

    /** Logo como data URL (PNG, JPEG ou WEBP). SVG não é aceito por risco de script embutido. */
    public record LogoRequest(@NotBlank @Size(max = 400000) String dataUrl) {
    }

    public record SettingsResponse(
            String companyName,
            String tradeName,
            String document,
            String phone,
            String email,
            String address,
            String timezone,
            String currency,
            String primaryColor,
            String secondaryColor,
            boolean allowNegativeStock,
            String logoDataUrl,
            Instant updatedAt) {
    }
}
