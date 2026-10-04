package com.nexus.pdv.shared.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configurações de segurança ({@code nexus.security.*}). */
@ConfigurationProperties(prefix = "nexus.security")
public class NexusSecurityProperties {

    /** Expõe Swagger UI e console H2 (apenas desenvolvimento). */
    private boolean devTools = false;

    private final Login login = new Login();
    private final RateLimit rateLimit = new RateLimit();
    private final Cors cors = new Cors();
    private final PasswordReset passwordReset = new PasswordReset();

    public boolean isDevTools() {
        return devTools;
    }

    public void setDevTools(boolean devTools) {
        this.devTools = devTools;
    }

    public Login getLogin() {
        return login;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Cors getCors() {
        return cors;
    }

    public PasswordReset getPasswordReset() {
        return passwordReset;
    }

    public static class Login {
        private int maxAttemptsPerEmail = 5;
        private int maxAttemptsPerIp = 50;
        private int lockMinutes = 15;

        public int getMaxAttemptsPerEmail() {
            return maxAttemptsPerEmail;
        }

        public void setMaxAttemptsPerEmail(int maxAttemptsPerEmail) {
            this.maxAttemptsPerEmail = maxAttemptsPerEmail;
        }

        public int getMaxAttemptsPerIp() {
            return maxAttemptsPerIp;
        }

        public void setMaxAttemptsPerIp(int maxAttemptsPerIp) {
            this.maxAttemptsPerIp = maxAttemptsPerIp;
        }

        public int getLockMinutes() {
            return lockMinutes;
        }

        public void setLockMinutes(int lockMinutes) {
            this.lockMinutes = lockMinutes;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private int requestsPerMinute = 600;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class PasswordReset {
        private int tokenTtlMinutes = 30;
        private String frontendBaseUrl = "http://localhost:4200";
        /** Registra o link de redefinição no log (somente desenvolvimento, sem servidor de e-mail). */
        private boolean logLinks = false;

        public int getTokenTtlMinutes() {
            return tokenTtlMinutes;
        }

        public void setTokenTtlMinutes(int tokenTtlMinutes) {
            this.tokenTtlMinutes = tokenTtlMinutes;
        }

        public String getFrontendBaseUrl() {
            return frontendBaseUrl;
        }

        public void setFrontendBaseUrl(String frontendBaseUrl) {
            this.frontendBaseUrl = frontendBaseUrl;
        }

        public boolean isLogLinks() {
            return logLinks;
        }

        public void setLogLinks(boolean logLinks) {
            this.logLinks = logLinks;
        }
    }
}
