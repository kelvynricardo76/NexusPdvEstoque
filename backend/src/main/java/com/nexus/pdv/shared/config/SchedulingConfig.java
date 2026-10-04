package com.nexus.pdv.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Rotinas agendadas (desligáveis via {@code nexus.scheduling.enabled=false}, ex.: em testes). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "nexus.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
