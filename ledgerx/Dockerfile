# Multi-stage build for LedgerX
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper & pom files
COPY pom.xml .
COPY ledgerx-common/pom.xml ledgerx-common/
COPY ledgerx-ledger-service/pom.xml ledgerx-ledger-service/
COPY ledgerx-risk-engine/pom.xml ledgerx-risk-engine/
COPY ledgerx-reconciliation-service/pom.xml ledgerx-reconciliation-service/
COPY ledgerx-app/pom.xml ledgerx-app/

# Copy source code
COPY ledgerx-common/src ledgerx-common/src
COPY ledgerx-ledger-service/src ledgerx-ledger-service/src
COPY ledgerx-risk-engine/src ledgerx-risk-engine/src
COPY ledgerx-reconciliation-service/src ledgerx-reconciliation-service/src
COPY ledgerx-app/src ledgerx-app/src

# Install maven and build jar
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests -Drevision=1.0.0

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S ledgerx && adduser -S ledgerx -G ledgerx
USER ledgerx:ledgerx

COPY --from=builder /build/ledgerx-app/target/ledgerx-app-1.0.0.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
