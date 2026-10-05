package com.ledgerx.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerxOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LedgerX — Event-Driven Payment Reconciliation & Risk Engine")
                        .description("Production-grade financial platform maintaining an immutable double-entry ledger, "
                                + "reconciling external payment-provider settlement reports, detecting discrepancies, "
                                + "evaluating deterministic risk rules, managing DLQ, and supporting non-corrupting event replay.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("LedgerX Core Engineering Team")
                                .url("https://github.com/Karanveershinde/payment-infrastructure-lab"))
                        .license(new License().name("Apache 2.0")))
                .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"))
                .components(new Components()
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-KEY")
                                .description("API Key for authenticating administrative and operational endpoints")));
    }
}
