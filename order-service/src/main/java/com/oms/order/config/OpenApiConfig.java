package com.oms.order.config;

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
    public OpenAPI orderServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OMS Order Service API")
                        .version("1.0.0")
                        .description("Order lifecycle management. Place, track, cancel orders. "
                                + "Stock is reserved atomically via product-service. "
                                + "Order events are published to Kafka after commit.")
                        .contact(new Contact().name("Order Management System")))
                .servers(Collections.singletonList(
                        new Server().url("http://localhost:8083").description("Local")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Log in at user-service and paste the accessToken")));
    }
}
