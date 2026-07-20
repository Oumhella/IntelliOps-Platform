package org.example.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonOpenApiConfig {

    @Value("${spring.application.name:Microservice}")
    private String applicationName;

    @Value("${openapi.service.title:${spring.application.name:Microservice API}}")
    private String title;

    @Value("${openapi.service.description:Documentation OpenAPI centralisée}")
    private String description;

    @Value("${openapi.service.version:1.0.0}")
    private String version;

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .version(version)
                        .description(description))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}