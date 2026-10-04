package com.nexus.pdv.shared.access;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AccessWebConfig implements WebMvcConfigurer {

    private final TenantAccessInterceptor tenantAccessInterceptor;
    private final PlatformAccessInterceptor platformAccessInterceptor;

    public AccessWebConfig(TenantAccessInterceptor tenantAccessInterceptor,
            PlatformAccessInterceptor platformAccessInterceptor) {
        this.tenantAccessInterceptor = tenantAccessInterceptor;
        this.platformAccessInterceptor = platformAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantAccessInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/super-admin/**", "/api/billing/webhooks/**");
        registry.addInterceptor(platformAccessInterceptor)
                .addPathPatterns("/api/super-admin/**")
                .excludePathPatterns("/api/super-admin/auth/login");
    }
}
