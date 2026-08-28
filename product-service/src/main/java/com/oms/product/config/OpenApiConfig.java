package com.oms.product.config;

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
    public OpenAPI productServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OMS Product Service API")
                        .version("1.0.0")
                        .description("Catalogue management, search and stock reservation. "
                                + "Reads are public; writes require an ADMIN token issued by user-service.")
                        .contact(new Contact().name("Order Management System")))
                .servers(Collections.singletonList(
                        new Server().url("http://localhost:8082").description("Local")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Log in at user-service as admin@oms.com and paste the accessToken")));
    }
}
