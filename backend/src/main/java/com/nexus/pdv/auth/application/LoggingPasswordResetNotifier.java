package com.nexus.pdv.auth.application;

import com.nexus.pdv.shared.security.NexusSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementação sem servidor de e-mail. O link (que contém o token) só é escrito no log quando
 * {@code nexus.security.password-reset.log-links=true} — habilitado apenas no perfil dev.
 */
@Component
public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);

    private final boolean logLinks;

    public LoggingPasswordResetNotifier(NexusSecurityProperties properties) {
        this.logLinks = properties.getPasswordReset().isLogLinks();
    }

    @Override
    public void sendResetLink(String email, String name, String link) {
        if (logLinks) {
            log.info("[DEV] Link de redefinição de senha para {}: {}", email, link);
        } else {
            log.info("Link de redefinição de senha gerado (entrega por e-mail não configurada).");
        }
    }
}
