.PHONY: setup build test integration-test up down restart load-test demo clean help

help:
	@echo "PayFlow — Fault-Tolerant Payment Orchestration & Transaction Gateway"
	@echo ""
	@echo "Available commands:"
	@echo "  make setup            - Initialize environment files"
	@echo "  make build            - Compile and package all Maven modules"
	@echo "  make test             - Run unit, state machine, and concurrency tests"
	@echo "  make up               - Build and start all services via Docker Compose"
	@echo "  make down             - Stop all services and remove containers"
	@echo "  make restart          - Restart all Docker services"
	@echo "  make load-test        - Run k6 load benchmark test suite"
	@echo "  make demo             - Execute full end-to-end demo and failure scenarios"
	@echo "  make clean            - Clean Maven targets and docker volumes"

setup:
	cp -n .env.example .env || copy .env.example .env

build:
	mvn clean install -DskipTests

test:
	mvn test

up: build
	docker compose up -d --build

down:
	docker compose down -v

restart:
	docker compose down
	docker compose up -d

load-test:
	docker run --rm -i --network=payflow-network grafana/k6 run - < load-tests/payment-flow-benchmark.js
	docker run --rm -i --network=payflow-network grafana/k6 run - < load-tests/idempotency-concurrency.js
	docker run --rm -i --network=payflow-network grafana/k6 run - < load-tests/rate-limiting-test.js
	docker run --rm -i --network=payflow-network grafana/k6 run - < load-tests/circuit-breaker-failover.js

demo:
	bash scripts/demo.sh || powershell -ExecutionPolicy Bypass -File scripts/demo.ps1

clean:
	mvn clean
	docker compose down -v --remove-orphans
