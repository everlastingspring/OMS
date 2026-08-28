# Order Management System — Microservices

A Java 8 / Spring Boot microservices Order Management System: user accounts, a product
catalogue, orders, and an audit trail driven by Kafka and Amazon SQS.

> **Status — Phases 0–2 delivered.** `oms-common`, `user-service` and `product-service`
> are complete and runnable. `order-service` (Kafka producer, Resilience4j) and
> `audit-service` (MongoDB audit, SQS + DLQ) are Phases 3 and 4 and are not in this
> drop. The MySQL schema, the report queries and the compose file already cover all
> four services so nothing has to be rewritten when they land.

---

## 1. Technology and versions

| Component | Version | Note |
|---|---|---|
| Java | 8 (compiler target `1.8`) | Mandated by the brief |
| Spring Boot | 2.7.18 | Final 2.7 release, and the last Spring Boot line supporting Java 8 |
| Spring Cloud | 2021.0.9 | The release train that pairs with Boot 2.7.x |
| Hibernate / JPA | managed by Boot (5.6.x) | |
| springdoc-openapi | 1.8.0 | 2.x requires Spring Boot 3 and the Jakarta namespace |
| jjwt | 0.11.5 | HS256 access tokens |
| MySQL | 8.0 | Connector/J managed by Boot |
| MongoDB | 6.0 | Phase 4 |
| Apache Kafka | 3.x, KRaft mode (no ZooKeeper) | Phase 3 |
| Amazon SQS | LocalStack 3.4 | Phase 4 |
| Resilience4j | 1.7.1 | Phase 3, circuit breaker on inter-service calls |

**Spring Boot 2.7.18 reached end of open-source support in November 2023.** It is used
here because the brief mandates Java 8, and no later Spring Boot supports Java 8. That
is a constraint, not a recommendation — a greenfield build today would target Java 17
and Spring Boot 3.x.

---

## 2. Modules

```
Order-management/
├── pom.xml                 parent POM, dependency management, Java 8 target
├── docker-compose.yml      MySQL, MongoDB, Kafka, Kafka UI, LocalStack, services
├── oms-common/             shared library (no main class)
├── user-service/     :8081 registration, login, JWT, profiles, addresses
├── product-service/  :8082 catalogue, search, stock reservation
├── order-service/    :8083 Phase 3
├── audit-service/    :8084 Phase 4
├── sql/init/               schema + seed, auto-loaded by MySQL on first boot
├── sql/reports.sql         the five reporting queries
├── localstack/init/        SQS queue + DLQ creation
├── postman/                collection and environment
└── docs/                   architecture and schema
```

### What lives in `oms-common`

Everything that would otherwise be copy-pasted into four services:

- `ApiResponse<T>` / `ValidationError` / `PageResponse<T>` — one response envelope everywhere
- `BusinessException` hierarchy carrying its own HTTP status and error code
- `GlobalExceptionHandler` — a single `@RestControllerAdvice` covering validation,
  malformed bodies, type mismatches, data-integrity violations, optimistic-lock
  conflicts, authentication, authorisation and the unexpected case
- `JwtTokenProvider`, `JwtAuthenticationFilter`, `UserPrincipal` — stateless auth
- `CorrelationIdFilter` — puts an `X-Correlation-Id` into the SLF4J MDC and echoes it back
- `RequestLoggingFilter` — one line in, one line out per request (never the body)
- `InternalApiKeyInterceptor` — guards `/api/v1/internal/**`
- Kafka event models for Phase 3

Each service scans it explicitly:
`@SpringBootApplication(scanBasePackages = {"com.oms.user", "com.oms.common"})`.

---

## 3. Running it

### Prerequisites

- **JDK 8.** Newer JDKs will not build this as-is.
  ```bash
  sdk install java 8.0.442-tem && sdk use java 8.0.442-tem
  java -version   # expect 1.8.0_xxx
  ```
- Maven 3.6+
- Docker Desktop or an equivalent (Colima, Rancher Desktop, OrbStack)

### Start the databases

```bash
docker compose --profile infra up -d
docker compose logs -f mysql        # wait for "ready for connections"
```

On first boot MySQL runs `sql/init/01_schema.sql` and `sql/init/02_seed.sql`, which
create the three schemas, all tables, and the seed data. To re-seed from scratch:

```bash
docker compose --profile infra down -v && docker compose --profile infra up -d
```

### Build and run the services

```bash
mvn clean install                # builds oms-common, then both services, and runs the tests

mvn -pl user-service    spring-boot:run     # http://localhost:8081
mvn -pl product-service spring-boot:run     # http://localhost:8082
```

Or in containers:

```bash
mvn clean package -DskipTests
docker compose --profile infra --profile apps up -d --build
```

### Seeded logins

| Email | Password | Role |
|---|---|---|
| `admin@oms.com` | `Admin@123` | ADMIN — required to create or edit products |
| `priya@oms.com` | `User@123` | USER |
| `rahul@oms.com` | `User@123` | USER |

Registration through the API always creates `ROLE_USER`. There is no endpoint that
grants ADMIN, which is why an admin is seeded.

---

## 4. API documentation

| | |
|---|---|
| user-service Swagger UI | http://localhost:8081/swagger-ui.html |
| user-service OpenAPI JSON | http://localhost:8081/v3/api-docs |
| product-service Swagger UI | http://localhost:8082/swagger-ui.html |
| product-service OpenAPI JSON | http://localhost:8082/v3/api-docs |

### Postman

Import both files from `postman/`:

- `OMS.postman_collection.json`
- `OMS.postman_environment.json`

Run **Auth → Login (admin)** first. A test script writes `accessToken` into the
environment, and every protected request inherits it through collection-level bearer
auth, so nothing has to be pasted by hand. Requests are ordered so the folders can be
run top to bottom with the Collection Runner.

---

## 5. Endpoints

### user-service (`:8081`)

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/auth/register` | public | 201; 409 on a duplicate email |
| POST | `/api/v1/auth/login` | public | 200 with a JWT; 401 otherwise |
| GET | `/api/v1/users/me` | JWT | Caller's own profile |
| GET | `/api/v1/users/{id}` | JWT, self or ADMIN | |
| GET | `/api/v1/users?keyword=&page=&size=&sort=` | ADMIN | Paged search |
| PUT | `/api/v1/users/{id}` | JWT, self or ADMIN | Name and phone only |
| DELETE | `/api/v1/users/{id}` | JWT, self or ADMIN | 204, soft delete |
| GET | `/api/v1/users/{id}/addresses` | JWT, self or ADMIN | |
| POST | `/api/v1/users/{id}/addresses` | JWT, self or ADMIN | 201; first address becomes the default |
| DELETE | `/api/v1/users/{id}/addresses/{addressId}` | JWT, self or ADMIN | 204 |
| GET | `/api/v1/internal/users/{id}` | `X-Internal-Api-Key` | For order-service |

### product-service (`:8082`)

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/products/{id}` | public | |
| GET | `/api/v1/products/sku/{sku}` | public | |
| GET | `/api/v1/products/search` | public | `keyword`, `categoryId`, `category`, `minPrice`, `maxPrice`, `inStock`, `page`, `size`, `sort` |
| POST | `/api/v1/products` | ADMIN | 201; 409 on a duplicate SKU |
| PUT | `/api/v1/products/{id}` | ADMIN | SKU is immutable |
| DELETE | `/api/v1/products/{id}` | ADMIN | 204, soft delete |
| GET | `/api/v1/categories` | public | |
| POST/PUT/DELETE | `/api/v1/categories/{id}` | ADMIN | Deletion refused while active products remain |
| POST | `/api/v1/internal/products/reserve-stock` | `X-Internal-Api-Key` | All-or-nothing, optimistic-locked |
| POST | `/api/v1/internal/products/release-stock` | `X-Internal-Api-Key` | Compensating action |

### Response envelope

Every endpoint, success or failure, returns the same shape:

```json
{
  "success": true,
  "message": "Request processed successfully",
  "data": { },
  "timestamp": "2026-08-27T10:15:30.123"
}
```

```json
{
  "success": false,
  "message": "Validation failed",
  "errorCode": "VALIDATION_ERROR",
  "errors": [
    { "field": "email", "rejectedValue": "not-an-email", "message": "Email must be a valid address" }
  ],
  "path": "/api/v1/auth/register",
  "timestamp": "2026-08-27T10:15:30.123"
}
```

| Error code | Status |
|---|---|
| `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `MISSING_PARAMETER`, `TYPE_MISMATCH`, `INVALID_OPERATION` | 400 |
| `UNAUTHORIZED` | 401 |
| `FORBIDDEN` | 403 |
| `RESOURCE_NOT_FOUND`, `ENDPOINT_NOT_FOUND` | 404 |
| `DUPLICATE_RESOURCE`, `DATA_INTEGRITY_VIOLATION`, `INSUFFICIENT_STOCK`, `CONCURRENT_MODIFICATION` | 409 |
| `INTERNAL_ERROR` | 500 |

---

## 6. Database

Three schemas on one MySQL instance: `oms_user`, `oms_product`, `oms_order`. **Each
service reads and writes exactly one schema and never queries another.** The service
boundary is real; the shared instance is a local-development convenience.

`sql/reports.sql` deliberately breaks that rule. Reports such as *top 5 selling
products* and *users with the highest order count* have to join across all three, and
they are analyst queries rather than application queries. In production these would be
served from a read model built from the Kafka order events, not from the operational
tables. The queries are written to run on MySQL 5.7 as well as 8.0 — no window
functions.

```bash
mysql -h 127.0.0.1 -u root -proot < sql/reports.sql
```

### Notable schema decisions

- `orders.user_id` and `order_items.product_id` carry **no foreign key**. The
  referenced rows live in another schema owned by another service.
- `order_items` denormalises `product_name` and `unit_price`. An order must still
  render correctly after the product is renamed or repriced.
- `products.version` is the JPA `@Version` column. Two orders racing for the last unit
  do not both succeed; the loser gets a `409 CONCURRENT_MODIFICATION` after three retries.
- Deletion is always soft (`active = FALSE`). Historic orders must keep resolving.

`spring.jpa.hibernate.ddl-auto` is `update` so a fresh clone starts without running
compose first. Once the schema settles, switch it to `validate` — the DDL in
`sql/init/01_schema.sql` is the authoritative definition.

---

## 7. Security

- Passwords are BCrypt (strength 10) and the hash never leaves user-service — there is
  no `password` field on any response DTO.
- Login returns one message for a wrong password, an unknown email and a deactivated
  account, so the endpoint cannot be used to enumerate registered addresses.
- JWTs are HS256 with `uid`, `sub` (email) and `role` claims. Both services must share
  the same `oms.jwt.secret`; product-service verifies tokens it never issued.
- No session is created (`SessionCreationPolicy.STATELESS`) and no database lookup
  happens on the auth path.
- `/api/v1/internal/**` requires the `X-Internal-Api-Key` header and would not be
  routed publicly in a real deployment.

The development secrets in `application.yml` are defaults for local runs only. In
Docker they are overridden by `OMS_JWT_SECRET` and `OMS_INTERNAL_API_KEY`.

---

## 8. Logging

SLF4J over Logback, configured in each service's `logback-spring.xml`:

- Console, a rolling file (`logs/<service>.log`, 10 MB × 7 days), and a separate
  WARN-and-above file so an on-call engineer does not have to grep.
- Every line carries the correlation id from the MDC:
  `2026-08-27 10:15:30.123 INFO [user-service] [3f9c…] [http-nio-8081-exec-1] c.o.u.s.i.AuthServiceImpl - Issued token for user id=2`
- Pass `X-Correlation-Id` yourself and it is preserved end to end; otherwise one is
  generated and returned on the response.
- Request bodies are never logged. They routinely carry passwords and addresses.

---

## 9. Tests

```bash
mvn test                      # everything
mvn -pl user-service test     # one module
```

| Layer | Approach | Why |
|---|---|---|
| Service | JUnit 5 + Mockito | Business rules in isolation |
| Repository | `@DataJpaTest` + H2 in MySQL mode | Real SQL, real constraints, no Docker needed for `mvn test` |
| Controller | Standalone MockMvc + the shared `GlobalExceptionHandler` | Mapping, binding, validation and error translation without booting a context |

Controller tests use `MockMvcBuilders.standaloneSetup` rather than `@WebMvcTest` so
they cannot be broken by unrelated auto-configuration. The consequence is that
`@PreAuthorize` role checks are **not** covered by the controller slice — that is a
deliberate trade, and the authorisation rules live in each service's `SecurityConfig`.

Testcontainers was considered and rejected: it would make `mvn test` require a running
Docker daemon, which is a poor trade for a repository slice.

---

## 10. Bonus items implemented

1. **Dockerised** — `docker-compose.yml` with profiles (`infra`, `messaging`, `apps`),
   healthchecks, memory limits, and a non-root multi-stage-free image per service.
2. **Circuit breaker (Resilience4j)** — Phase 3, on the order → user and order → product
   calls. The dependency is already managed in the parent POM.

---

## 11. What is not here yet

- `order-service` — order aggregate, transactional creation, status transitions,
  cancellation, history, Resilience4j, Kafka producer (Phase 3)
- `audit-service` — Kafka consumer into MongoDB, idempotent writes, dead-letter topic,
  SQS producer and consumer with retry and DLQ (Phase 4)
- Architecture and ER diagrams, MongoDB collection definitions (Phase 5)

The Kafka event models, topic names, MySQL order tables, SQS queue setup and compose
services for all of the above are already in place.
