package org.infra.audit.config;

import org.infra.audit.interceptor.ApiLoggingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration class for audit functionality.
 * Registers interceptors and configures audit-related beans.
 */
@Configuration
public class AuditConfiguration implements WebMvcConfigurer {

    private final ApiLoggingInterceptor apiLoggingInterceptor;

    public AuditConfiguration(ApiLoggingInterceptor apiLoggingInterceptor) {
        this.apiLoggingInterceptor = apiLoggingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiLoggingInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/metrics");
    }
}
