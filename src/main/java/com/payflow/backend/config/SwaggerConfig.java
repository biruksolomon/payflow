package com.payflow.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {


    @Value("${app.api.base-url}")
    private String baseUrl;


    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayFlow Backend API")
                        .description("Payment Processing Platform - REST API Documentation\n\n" +
                                "A comprehensive payment processing system with support for multiple payment methods, " +
                                "real-time order tracking, and secure transaction management.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("PayFlow Team")
                                .email("support@payflow.com")
                                .url("https://payflow.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("Development Server"))
                .addServersItem(new Server()
                        .url("https://api.payflow.com")
                        .description("Production Server"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT token")));
//                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"));
    }
}
