# Order Management System — Microservices

![Java](https://img.shields.io/badge/Java-8-orange?logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen?logo=springboot)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?logo=mysql)
![MongoDB](https://img.shields.io/badge/MongoDB-6.0-green?logo=mongodb)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-KRaft-black?logo=apachekafka)
![Docker](https://img.shields.io/badge/Docker-Compose-blue?logo=docker)

A Java 8 / Spring Boot microservices Order Management System covering user accounts,
a product catalogue, orders, and an event-driven audit trail via Kafka and Amazon SQS.

---

## Tech Stack

| Component | Version | Note |
|---|---|---|
| Java | 8 (`1.8` compiler target) | Mandated by brief |
| Spring Boot | 2.7.18 | Last Boot release supporting Java 8 |
| Spring Cloud | 2021.0.9 | Pairs with Boot 2.7.x |
| Hibernate / JPA | managed by Boot 5.6.x | |
| springdoc-openapi | 1.8.0 | v2 requires Boot 3 / Jakarta |
| jjwt | 0.11.5 | HS256 access tokens |
| Resilience4j | 1.7.1 | Circuit breaker on inter-service calls |
| MySQL | 8.0 | Three schemas, one instance (local dev) |
| MongoDB | 6.0 | Audit trail (Phase 4) |
| Apache Kafka | 3.x KRaft | No ZooKeeper |
| Amazon SQS | LocalStack 3.4 | Local simulation |

> **Note:** Spring Boot 2.7.18 is EOL (Nov 2023). It is used here because the brief
> mandates Java 8 — no later Boot line supports it. A greenfield project today would
> target Java 21 + Spring Boot 3.x.

---

## Project Structure

```
Order-management/
├── pom.xml                 parent POM — dependency management, Java 8 target
├── docker-compose.yml      MySQL, MongoDB, Kafka, Kafka UI, LocalStack
├── oms-common/             shared library (JWT, exceptions, filters, event models)
├── user-service/     :8081 registration, JWT login, profiles, addresses
├── product-service/  :8082 catalogue, stock management (coming next)
├── order-service/    :8083 order lifecycle, Kafka producer   (Phase 3)
├── audit-service/    :8084 Kafka consumer → MongoDB          (Phase 4)
├── sql/
│   ├── init/               schema DDL + seed data (auto-loaded by MySQL on first boot)
│   └── reports.sql         five cross-entity reporting queries
├── localstack/init/        SQS queue + DLQ creation script
└── postman/                collection + environment
```

### `oms-common` — shared library

Shared code that would otherwise be duplicated across every service:

| Class / Package | Purpose |
|---|---|
| `ApiResponse<T>` | Single response envelope for every endpoint |
| `PageResponse<T>` | Stable paged wrapper (Spring's `PageImpl` JSON is version-unstable) |
| `BusinessException` hierarchy | Typed exceptions that carry their own HTTP status and error code |
| `GlobalExceptionHandler` | One `@RestControllerAdvice` covering validation, security and unexpected cases |
| `JwtTokenProvider` / `JwtAuthenticationFilter` | Stateless JWT auth shared across services |
| `CorrelationIdFilter` | Injects `X-Correlation-Id` into MDC; echoed on every response |
| `RequestLoggingFilter` | One line per request/response (headers only, never the body) |
| `InternalApiKeyInterceptor` | Guards `/api/v1/internal/**` with `X-Internal-Api-Key` |
| `OrderEvent` / `KafkaTopics` | Shared Kafka event model for Phase 3+ |

Each service scans it explicitly:
```java
@SpringBootApplication(scanBasePackages = {"com.oms.<service>", "com.oms.common"})
```

---

## Prerequisites

- **JDK 8** — newer JDKs will not build this as-is
  ```bash
  sdk install java 8.0.442-tem
  sdk use java 8.0.442-tem
  java -version   # expect 1.8.0_xxx
  ```
- Maven 3.6+
- Docker Desktop (or Colima / OrbStack)

---

## Quick Start

### 1. Start infrastructure

```bash
# MySQL + MongoDB
docker compose --profile infra up -d

# Kafka + Kafka UI + LocalStack (needed from Phase 3 onwards)
docker compose --profile messaging up -d
```

MySQL auto-runs `sql/init/` on first boot — creates all three schemas and seeds test data.

### 2. Build

```bash
mvn clean install
```

### 3. Run services

```bash
mvn -pl user-service    spring-boot:run   # :8081
mvn -pl product-service spring-boot:run   # :8082
mvn -pl order-service   spring-boot:run   # :8083
mvn -pl audit-service   spring-boot:run   # :8084
```

### 4. Explore

| URL | What |
|---|---|
| http://localhost:8081/swagger-ui.html | user-service API |
| http://localhost:8082/swagger-ui.html | product-service API |
| http://localhost:8083/swagger-ui.html | order-service API |
| http://localhost:8084/swagger-ui.html | audit-service API |
| http://localhost:8090 | Kafka UI |

---

## Seeded Credentials

| Email | Password | Role |
|---|---|---|
| `admin@oms.com` | `Admin@123` | ADMIN |
| `priya@oms.com` | `User@123` | USER |
| `rahul@oms.com` | `User@123` | USER |

Registration always creates `ROLE_USER` — there is no privilege-escalation endpoint.

---

## Postman

Import both files from `postman/`:
- `OMS.postman_collection.json`
- `OMS.postman_environment.json`

Select **OMS Local** environment → Run **Auth → Login (admin)** first (stores `accessToken` automatically) → run the full collection top to bottom. No manual edits needed.

---

## Database Design

Three schemas on one MySQL instance: `oms_user`, `oms_product`, `oms_order`.
Each service touches only its own schema at runtime.

Key decisions:
- **No foreign keys across schema boundaries** — `orders.user_id` and `order_items.product_id` reference rows in other schemas; cross-schema FKs are not supported
- **`order_items` denormalises `product_name` and `unit_price`** — orders must render correctly after a product is renamed or repriced
- **Optimistic locking on `products.version`** — two concurrent orders for the last unit yield one 409 rather than an oversell
- **Soft deletes everywhere** (`active = FALSE`) — historic orders keep resolving

---

## Security

- Passwords: BCrypt strength 10, never returned in any response
- JWT: HS256, claims `uid` + `sub` + `role`, stateless (no DB lookup on auth path)
- Same error message for wrong password, unknown email, and deactivated account — prevents email enumeration
- `/api/v1/internal/**` requires `X-Internal-Api-Key` header, never publicly routed

---

## Running Reports

```bash
mysql -h 127.0.0.1 -u root -proot < sql/reports.sql
```

Covers: top 5 products by revenue, users by order count, low-stock products,
daily order totals, and cancelled order analysis.

---

## Re-seed from Scratch

```bash
docker compose --profile infra down -v
docker compose --profile infra up -d
```
