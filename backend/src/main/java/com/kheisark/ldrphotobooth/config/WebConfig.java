package com.kheisark.ldrphotobooth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.stream.Stream;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.cors.allowed-origins:}") String additionalOrigins
    ) {
        this.allowedOrigins = Stream.concat(
                        Stream.of(frontendUrl, "http://localhost:5173", "http://127.0.0.1:5173"),
                        Arrays.stream(additionalOrigins.split(","))
                )
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .map(origin -> origin.replaceAll("/+$", ""))
                .distinct()
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
