# Payment Infrastructure Lab

A production-grade distributed payment systems portfolio containing two complementary enterprise platforms:

---

### Project 1: [PayFlow — Fault-Tolerant Payment Orchestration & Transaction Gateway](./payflow/README.md)
*Focus: Transaction reliability, dynamic smart routing, dual-tier Redis/PostgreSQL distributed idempotency, resilience engineering (Circuit Breakers & Retries), and asynchronous webhook deduplication.*

- **[Full Documentation & Architecture](./payflow/README.md)**
- **Tech Stack:** Java 21, Spring Boot 3.3.4, PostgreSQL, Redis, Kafka, Resilience4j, Micrometer, Prometheus, Grafana.
- **Empirical Benchmark:** 200 requests at 20 concurrent workers; 44–58 TPS across three live runs, with 99.5%–100% success, 0 double charges.

---

### Project 2: [LedgerX — Event-Driven Payment Reconciliation & Risk Engine](./ledgerx/README.md)
*Focus: Post-processing financial correctness, immutable double-entry bookkeeping ($\sum \text{Debits} == \sum \text{Credits}$), multi-category provider settlement reconciliation (8 discrepancy states), deterministic rule-based risk scoring, DLQ handling, and safe idempotent event replay.*

- **[Full Documentation & Architecture](./ledgerx/README.md)**
- **Tech Stack:** Java 21, Spring Boot 3.3.4, PostgreSQL, Redis, Apache Kafka, Micrometer, Prometheus, Grafana.
- **Empirical Benchmark:** 500 transactions, 100% invariant compliance (0 balanced ledger violations), 26.51 TPS ingestion, 259+ records/sec batch reconciliation throughput.

---

### Quick Directory Structure
```
payment-infrastructure-lab/
├── payflow/                       # Payment Orchestrator & Gateway
│   ├── src/
│   ├── docker-compose.yml
│   └── README.md
└── ledgerx/                       # Reconciliation & Ledger Engine
    ├── ledgerx-common/
    ├── ledgerx-ledger-service/
    ├── ledgerx-risk-engine/
    ├── ledgerx-reconciliation-service/
    ├── ledgerx-app/
    ├── docker-compose.yml
    └── README.md
```

