package com.ledgerx.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.ledgerx")
@EntityScan(basePackages = {
        "com.ledgerx.ledger.domain.entity",
        "com.ledgerx.reconciliation.domain.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.ledgerx.ledger.repository",
        "com.ledgerx.reconciliation.repository"
})
public class LedgerXApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerXApplication.class, args);
    }
}
