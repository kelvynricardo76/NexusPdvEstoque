package com.nexus.pdv.tenant.application;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import com.nexus.pdv.shared.security.CurrentUser;
import com.nexus.pdv.shared.text.Texts;
import com.nexus.pdv.tenant.api.TenantSettingsDtos.SettingsRequest;
import com.nexus.pdv.tenant.api.TenantSettingsDtos.SettingsResponse;
import com.nexus.pdv.tenant.domain.TenantSettings;
import com.nexus.pdv.tenant.infrastructure.TenantSettingsRepository;
import com.nexus.pdv.user.domain.User;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Currency;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Personalização do tenant. O registro é sempre o do tenant autenticado. */
@Service
public class TenantSettingsService {

    private static final Pattern DATA_URL = Pattern.compile("^data:(image/(?:png|jpeg|webp));base64,([A-Za-z0-9+/=]+)$");
    private static final int MAX_LOGO_BYTES = 256 * 1024;

    private final TenantSettingsRepository repository;
    private final AuditService auditService;

    public TenantSettingsService(TenantSettingsRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        return toResponse(current());
    }

    @Transactional(readOnly = true)
    public ZoneId zone() {
        return repository.findById(CurrentUser.requireTenantId())
                .map(settings -> ZoneId.of(settings.getTimezone()))
                .orElse(ZoneId.of(TenantSettings.DEFAULT_TIMEZONE));
    }

    @Transactional(readOnly = true)
    public boolean allowNegativeStock() {
        return repository.findById(CurrentUser.requireTenantId()).map(TenantSettings::isAllowNegativeStock).orElse(false);
    }

    @Transactional
    public SettingsResponse update(SettingsRequest request) {
        validateZone(request.timezone());
        validateCurrency(request.currency());
        TenantSettings settings = current();
        settings.update(Texts.clean(request.companyName()), Texts.clean(request.tradeName()),
                Texts.digits(request.document()), Texts.clean(request.phone()),
                User.normalizeEmail(Texts.clean(request.email())), Texts.clean(request.address()), request.timezone(),
                request.currency(), normalizeColor(request.primaryColor()), normalizeColor(request.secondaryColor()),
                request.allowNegativeStock());
        auditService.record(AuditAction.UPDATE, "TenantSettings", settings.getTenantId(),
                Map.of("allowNegativeStock", request.allowNegativeStock()));
        return toResponse(settings);
    }

    @Transactional
    public SettingsResponse changeLogo(String dataUrl) {
        Matcher matcher = DATA_URL.matcher(dataUrl.trim());
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Envie uma imagem PNG, JPEG ou WEBP.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(matcher.group(2));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Imagem inválida.");
        }
        if (bytes.length > MAX_LOGO_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "A logo deve ter no máximo 256 KB.");
        }
        if (!hasValidSignature(matcher.group(1), bytes)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "O conteúdo não corresponde ao tipo da imagem.");
        }
        TenantSettings settings = current();
        settings.changeLogo(dataUrl.trim());
        auditService.record(AuditAction.UPDATE, "TenantSettings", settings.getTenantId(), Map.of("logo", "updated"));
        return toResponse(settings);
    }

    @Transactional
    public SettingsResponse removeLogo() {
        TenantSettings settings = current();
        settings.changeLogo(null);
        auditService.record(AuditAction.UPDATE, "TenantSettings", settings.getTenantId(), Map.of("logo", "removed"));
        return toResponse(settings);
    }

    private TenantSettings current() {
        UUID tenantId = CurrentUser.requireTenantId();
        return repository.findById(tenantId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static boolean hasValidSignature(String mime, byte[] bytes) {
        return switch (mime) {
            case "image/png" -> bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
            case "image/jpeg" -> bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8;
            case "image/webp" -> bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[8] == 'W' && bytes[9] == 'E';
            default -> false;
        };
    }

    private static void validateZone(String zone) {
        try {
            ZoneId.of(zone);
        } catch (DateTimeException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Fuso horário inválido.");
        }
    }

    private static void validateCurrency(String currency) {
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Moeda inválida.");
        }
    }

    private static String normalizeColor(String color) {
        String cleaned = Texts.clean(color);
        return cleaned == null ? null : cleaned.toUpperCase();
    }

    private static SettingsResponse toResponse(TenantSettings s) {
        return new SettingsResponse(s.getCompanyName(), s.getTradeName(), s.getDocument(), s.getPhone(), s.getEmail(),
                s.getAddress(), s.getTimezone(), s.getCurrency(), s.getPrimaryColor(), s.getSecondaryColor(),
                s.isAllowNegativeStock(), s.getLogoDataUrl(), s.getUpdatedAt());
    }
}
