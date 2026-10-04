package com.nexus.pdv.platform.application;

import com.nexus.pdv.platform.domain.PlatformAdmin;
import com.nexus.pdv.platform.infrastructure.PlatformAdminRepository;
import com.nexus.pdv.shared.security.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o primeiro SUPER_ADMIN a partir de variáveis de ambiente quando a plataforma ainda não
 * possui nenhum administrador. Nenhuma credencial padrão é embutida no código.
 */
@Component
@Order(0)
public class PlatformBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformBootstrap.class);

    private final PlatformAdminRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String name;
    private final String email;
    private final String password;

    public PlatformBootstrap(PlatformAdminRepository repository, PasswordEncoder passwordEncoder,
            @Value("${nexus.bootstrap.admin-name:Administrador Nexus}") String name,
            @Value("${nexus.bootstrap.admin-email:}") String email,
            @Value("${nexus.bootstrap.admin-password:}") String password) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            log.warn("Nenhum SUPER_ADMIN cadastrado. Defina NEXUS_BOOTSTRAP_ADMIN_EMAIL e NEXUS_BOOTSTRAP_ADMIN_PASSWORD.");
            return;
        }
        PasswordPolicy.validate(password, email);
        repository.save(new PlatformAdmin(name, email, passwordEncoder.encode(password)));
        log.info("SUPER_ADMIN inicial criado para {}", email);
    }
}
