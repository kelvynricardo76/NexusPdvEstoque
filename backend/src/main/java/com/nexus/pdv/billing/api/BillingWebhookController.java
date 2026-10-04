package com.nexus.pdv.billing.api;

import com.nexus.pdv.billing.application.BillingService;
import com.nexus.pdv.shared.persistence.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recebe webhooks dos provedores de cobrança. A autenticidade é validada pelo provedor
 * ({@code BillingProvider.parseWebhook}); o processamento é idempotente.
 */
@Tag(name = "Billing — Webhooks")
@RestController
@RequestMapping("/api/billing/webhooks")
public class BillingWebhookController {

    private final BillingService billingService;

    public BillingWebhookController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<Map<String, Object>> receive(@PathVariable String provider, @RequestBody String payload,
            @RequestHeader Map<String, String> headers) {
        boolean processed = TenantContext.callAsSystem(() -> billingService.handleWebhook(provider, payload, headers));
        return ResponseEntity.ok(Map.of("processed", processed));
    }
}
