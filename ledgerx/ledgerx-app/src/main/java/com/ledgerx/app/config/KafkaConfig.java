package com.ledgerx.app.config;

import com.ledgerx.common.events.LedgerTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean
    public NewTopic transactionsRawTopic() {
        return TopicBuilder.name(LedgerTopics.TRANSACTIONS_RAW)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionsValidatedTopic() {
        return TopicBuilder.name(LedgerTopics.TRANSACTIONS_VALIDATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ledgerPostedTopic() {
        return TopicBuilder.name(LedgerTopics.LEDGER_POSTED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic riskEvaluatedTopic() {
        return TopicBuilder.name(LedgerTopics.RISK_EVALUATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic reconciliationCompletedTopic() {
        return TopicBuilder.name(LedgerTopics.RECONCILIATION_COMPLETED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic reconciliationDiscrepancyTopic() {
        return TopicBuilder.name(LedgerTopics.RECONCILIATION_DISCREPANCY)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic deadLetterEventsTopic() {
        return TopicBuilder.name(LedgerTopics.DEAD_LETTER_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
