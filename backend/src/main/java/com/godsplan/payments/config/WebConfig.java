package com.godsplan.payments.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {
    @Bean
    WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**").allowedOrigins("http://localhost:3000", "http://localhost:5173")
                        .allowedMethods("GET", "POST", "PATCH", "OPTIONS").allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }

    @Bean
    OpenAPI paymentsOpenApi() {
        return new OpenAPI().info(new Info().title("Payments Processing API").version("v1")
                .description("Auditable, idempotent simulated payment lifecycle API."));
    }
}
