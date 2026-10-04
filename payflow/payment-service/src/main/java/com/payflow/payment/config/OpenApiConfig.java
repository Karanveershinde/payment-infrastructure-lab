package com.payflow.payment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI payFlowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayFlow — Fault-Tolerant Payment Gateway API")
                        .description("High-availability payment orchestration engine with explicit state machines, strict idempotency, resilient provider routing, and event-driven architecture.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("PayFlow Engineering Team")
                                .url("https://github.com/Karanveershinde/payment-infrastructure-lab")))
                .components(new Components()
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("Merchant Secret API Key (e.g. pk_test_acme_123456)")));
    }
}
