package com.oms.audit.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI auditServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OMS Audit Service API")
                        .version("1.0.0")
                        .description("Order audit trail and activity history. Consumes Kafka events and SQS "
                                + "notifications, writes idempotently to MongoDB.")
                        .contact(new Contact().name("Order Management System")))
                .servers(Collections.singletonList(
                        new Server().url("http://localhost:8084").description("Local")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Log in at user-service and paste the accessToken")));
    }
}
