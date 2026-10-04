package com.nexus.pdv.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados da documentação OpenAPI. A UI só é habilitada no perfil dev.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI nexusOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Nexus PDV & Estoque API")
                .description("API da plataforma SaaS Nexus PDV & Estoque")
                .version("v1")
                .contact(new Contact().name("Nexus Development")));
    }
}
