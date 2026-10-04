package com.nexus.pdv.auth.application;

/**
 * Canal de entrega do link de redefinição de senha (e-mail em produção).
 * A implementação padrão apenas registra o evento; ver {@link LoggingPasswordResetNotifier}.
 */
public interface PasswordResetNotifier {

    void sendResetLink(String email, String name, String link);
}
