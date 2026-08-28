# CLAUDE.md — Order Management System

Context for any Claude Code session working in this repo. Read this before changing anything.

## What this is

A Microservices Order Management System built to an assignment brief. Mandated stack:
Java 8, Spring Boot, Spring MVC, Hibernate/JPA, MySQL, MongoDB, Kafka, Amazon SQS.

Owner: Prashanth (Java backend engineer). Prefers honest, direct assessments and
pushback over agreement. Industry-standard patterns, secure defaults, DRY, full
observability. Always wants working Postman requests alongside any API work.

## Hard version constraints — do not "upgrade" these

| | | |
|---|---|---|
| Java | **8** (`maven.compiler.source/target = 1.8`) | Mandated by the brief |
| Spring Boot | **2.7.18** | Final 2.7 release and the last Boot line supporting Java 8. It is EOL (Nov 2023). That is a known, documented constraint, not an oversight. |
| Spring Cloud | 2021.0.9 | Pairs with Boot 2.7.x |
| springdoc-openapi-ui | 1.8.0 | 2.x needs Boot 3 / Jakarta |
| jjwt | 0.11.5 | |
| Resilience4j | 1.7.1 (spring-boot2) | |
| H2 | 2.1.214 (test scope) | MySQL compatibility mode |

Build with JDK 8: `sdk use java 8.0.442-tem`. A newer JDK will not build this as-is.

## Deliberate exclusions — do not add without asking

- **MapStruct** — annotation processor alongside Lombok is a common build-breaker.
  Mappers are hand-written (`UserMapper`, `ProductMapper`). Keep them that way.
- **Testcontainers** — would make `mvn test` require a running Docker daemon.
  Repository tests use H2 in MySQL mode instead.
- **Lombok `@Data` on JPA entities** — generated equals/hashCode over lazy collections
  is a footgun. Entities have explicit getters/setters. Lombok is fine on DTOs.

## Layout

```
pom.xml              parent, packaging=pom, dependencyManagement
oms-common/          shared library, no main class
user-service/  :8081 oms_user schema     [DONE]
product-service/:8082 oms_product schema [DONE]
order-service/ :8083 oms_order schema    [PHASE 3, not started]
audit-service/ :8084 MongoDB oms_audit   [PHASE 4, not started]
sql/init/            schema + seed, auto-loaded by MySQL on first boot
sql/reports.sql      the five reporting queries
localstack/init/     SQS queue + DLQ creation
postman/             collection + environment
docs/architecture.md mermaid: topology, auth flow, stock race, ERD, request lifecycle
```

Each service scans the shared library explicitly:
`@SpringBootApplication(scanBasePackages = {"com.oms.<service>", "com.oms.common"})`.
Forget that and the GlobalExceptionHandler and JWT filter silently do not register.

## Conventions to follow when adding code

- Every endpoint returns `ApiResponse<T>` from `oms-common`. No bare DTOs, no bare lists.
- Never catch exceptions in a controller. Throw a `BusinessException` subclass; it
  carries its own HTTP status and error code and `GlobalExceptionHandler` does the rest.
- Paged endpoints return `PageResponse<T>`, never Spring's `PageImpl` (unstable JSON).
- Deletion is always soft (`active = false`). Historic orders must keep resolving.
- `/api/v1/internal/**` is service-to-service, guarded by `InternalApiKeyInterceptor`
  (`X-Internal-Api-Key`), permitted at the security layer, never routed publicly.
- New services need their own `SecurityConfig`; `JwtAuthenticationFilter` is
  constructed there with `new`, deliberately not a Spring bean (Boot auto-registers
  Filter beans into the servlet chain and it would run twice).
- Tests: service = Mockito, repository = `@DataJpaTest` + `@Import(JpaAuditingConfig.class)`,
  controller = `MockMvcBuilders.standaloneSetup(...)` with the shared exception handler
  (not `@WebMvcTest` — the standalone slice cannot be broken by auto-configuration).
  Consequence: `@PreAuthorize` rules are not covered by controller tests. Known and stated.

## Architecture decisions already made

1. **Three schemas on one MySQL instance** (`oms_user`, `oms_product`, `oms_order`).
   Each service touches only its own at runtime. `sql/reports.sql` joins across all
   three because the brief demands cross-entity reports — that is analyst tooling, and
   the README says production would use a Kafka-fed read model instead.
2. **No foreign keys across schema boundaries.** `orders.user_id` and
   `order_items.product_id` carry none, on purpose.
3. **`order_items` denormalises `product_name` and `unit_price`.** An order must render
   correctly after the product is renamed or repriced.
4. **Optimistic locking on `products.version`.** The retry loop in `ProductServiceImpl`
   sits *outside* the transaction using `TransactionTemplate`, because a failed
   optimistic lock marks the transaction rollback-only and `@Transactional`
   self-invocation would bypass the proxy and skip the retry entirely. Do not
   "simplify" this to `@Transactional`.
5. **`ddl-auto: update`** so a fresh clone starts without compose. `sql/init/01_schema.sql`
   is the authoritative DDL — switch to `validate` once the schema settles.

## Running it

```bash
sdk use java 8.0.442-tem
mvn clean install
docker compose --profile infra up -d              # MySQL + MongoDB
docker compose --profile messaging up -d          # Kafka + Kafka UI + LocalStack (Phase 3+)
mvn -pl user-service    spring-boot:run           # :8081
mvn -pl product-service spring-boot:run           # :8082
```

Swagger: http://localhost:8081/swagger-ui.html and http://localhost:8082/swagger-ui.html

Seeded logins: `admin@oms.com`/`Admin@123` (ADMIN, needed to create products),
`priya@oms.com`/`User@123`, `rahul@oms.com`/`User@123`.
Registration always creates ROLE_USER — there is no privilege-escalation endpoint.

Postman: import both files in `postman/`, run `1. Auth > Login (admin)` first (it stores
`accessToken`), then the whole collection runs top to bottom with assertions.

## Current state and next work

Phases 0–2 are committed (`git log` shows one commit per phase). **They have never been
compiled** — they were written in an environment without Maven Central access. The first
job of a terminal session is `mvn clean install`, then fix whatever breaks.

Next, in order:

- **Phase 3 — order-service (:8083)**: Order/OrderItem aggregate with cascade,
  transactional create that calls `POST /api/v1/internal/products/reserve-stock` and
  `GET /api/v1/internal/users/{id}` behind a Resilience4j circuit breaker + retry;
  status transitions; cancel (compensating `release-stock`); paged order history.
  Publish `OrderEvent` to `oms.order.events` **after commit**
  (`@TransactionalEventListener(phase = AFTER_COMMIT)`), not inside the transaction.
  The event models, topic names and MySQL tables already exist.
- **Phase 4 — audit-service (:8084)**: Kafka consumer writing `order_audit_logs`,
  `activity_history` and `application_logs` to MongoDB. Idempotent via a unique index on
  `eventId`, manual ack, `DeadLetterPublishingRecoverer` to `oms.order.events.DLT`.
  SQS producer/consumer against LocalStack; the queue and DLQ redrive policy are already
  created by `localstack/init/01-create-queues.sh`.
- **Phase 5**: MongoDB collection definitions and indexes, merge the new endpoints into
  the Postman collection, refresh the README.

Keep the one-commit-per-phase history.
