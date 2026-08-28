# Architecture

## Service topology

```mermaid
flowchart TB
    Client["Client / Postman / Swagger UI"]

    subgraph Phase02["Delivered - Phases 0-2"]
        USER["user-service :8081<br/>register, login, JWT, profiles"]
        PROD["product-service :8082<br/>catalogue, search, stock"]
    end

    subgraph Phase34["Phases 3-4"]
        ORDER["order-service :8083<br/>orders, status, history"]
        AUDIT["audit-service :8084<br/>Kafka + SQS consumers"]
    end

    subgraph Data["Data stores"]
        MU[("MySQL<br/>oms_user")]
        MP[("MySQL<br/>oms_product")]
        MO[("MySQL<br/>oms_order")]
        MONGO[("MongoDB<br/>oms_audit")]
    end

    KAFKA{{"Kafka<br/>oms.order.events"}}
    SQS{{"Amazon SQS<br/>oms-order-notifications<br/>+ DLQ"}}

    Client -->|JWT| USER
    Client -->|JWT| PROD
    Client -->|JWT| ORDER

    USER --> MU
    PROD --> MP
    ORDER --> MO
    AUDIT --> MONGO

    ORDER -.->|"GET /internal/users/{id}<br/>X-Internal-Api-Key"| USER
    ORDER -.->|"POST /internal/products/reserve-stock<br/>Resilience4j circuit breaker"| PROD

    ORDER -->|"publish after commit"| KAFKA
    KAFKA -->|"idempotent consumer"| AUDIT
    ORDER -->|"notification message"| SQS
    SQS -->|"retry x3, then DLQ"| AUDIT

    style Phase34 stroke-dasharray: 5 5
```

**Every service owns exactly one schema.** No service reaches into another's tables;
cross-service reads go through the `/api/v1/internal/**` endpoints, which are guarded
by a shared key and would not be routed publicly in a real deployment.

`oms-common` is a plain library, not a service: DTO envelope, exception hierarchy and
handler, JWT components, correlation-id and request logging filters, Kafka event models.

---

## Authentication flow

```mermaid
sequenceDiagram
    participant C as Client
    participant U as user-service
    participant P as product-service
    participant DB as oms_user

    C->>U: POST /api/v1/auth/login
    U->>DB: findByEmail
    DB-->>U: user + BCrypt hash
    U->>U: passwordEncoder.matches(raw, hash)
    U->>U: sign HS256 JWT {sub, uid, role}
    U-->>C: 200 { accessToken, tokenType, expiresInMs, user }

    C->>P: POST /api/v1/products (Bearer token)
    P->>P: JwtAuthenticationFilter verifies the signature
    Note over P: No database lookup and no session.<br/>Both services share oms.jwt.secret.
    P->>P: @PreAuthorize("hasRole('ADMIN')")
    P-->>C: 201 Created
```

product-service verifies tokens it never issued. That is the whole point of a signed
JWT: user-service is not on the critical path for any other service's authorisation.

---

## Concurrent stock reservation

The case that matters: two orders for the last unit, arriving at the same moment.

```mermaid
sequenceDiagram
    participant A as Order A
    participant B as Order B
    participant PS as product-service
    participant DB as oms_product

    A->>PS: reserve-stock (product 3, qty 1)
    B->>PS: reserve-stock (product 3, qty 1)
    PS->>DB: SELECT ... version = 7, stock = 1
    PS->>DB: SELECT ... version = 7, stock = 1
    PS->>DB: UPDATE ... SET stock = 0, version = 8 WHERE id = 3 AND version = 7
    DB-->>PS: 1 row - Order A commits
    PS->>DB: UPDATE ... SET stock = 0, version = 8 WHERE id = 3 AND version = 7
    DB-->>PS: 0 rows - OptimisticLockingFailureException
    Note over PS: Retry in a fresh transaction (up to 3 attempts).<br/>Re-read shows stock = 0.
    PS-->>B: 409 INSUFFICIENT_STOCK
```

Two details that make this correct rather than merely plausible:

- The retry loop sits **outside** the transaction. A failed optimistic lock marks the
  transaction rollback-only, so the row has to be re-read in a new one. That is why
  `ProductServiceImpl` uses `TransactionTemplate` rather than `@Transactional` — a
  self-invocation would bypass the proxy and silently run without a retry.
- Duplicate lines for the same product are merged before any decrement, so one request
  never races itself.

---

## Entity relationships

```mermaid
erDiagram
    USERS ||--o{ ADDRESSES : "has"
    CATEGORIES ||--o{ PRODUCTS : "groups"
    ORDERS ||--|{ ORDER_ITEMS : "contains"

    USERS {
        bigint id PK
        varchar email UK
        varchar password "BCrypt"
        varchar role "USER or ADMIN"
        boolean active "soft delete"
    }
    ADDRESSES {
        bigint id PK
        bigint user_id FK
        boolean is_default
    }
    CATEGORIES {
        bigint id PK
        varchar name UK
        boolean active
    }
    PRODUCTS {
        bigint id PK
        varchar sku UK
        decimal price
        int stock_quantity
        bigint category_id FK
        bigint version "JPA optimistic lock"
        boolean active "soft delete"
    }
    ORDERS {
        bigint id PK
        varchar order_number UK
        bigint user_id "no FK - other schema"
        varchar status
        decimal total_amount
    }
    ORDER_ITEMS {
        bigint id PK
        bigint order_id FK
        bigint product_id "no FK - other schema"
        varchar product_name "denormalised snapshot"
        decimal unit_price "price at order time"
    }
```

`USERS`/`ADDRESSES` live in `oms_user`, `CATEGORIES`/`PRODUCTS` in `oms_product`, and
`ORDERS`/`ORDER_ITEMS` in `oms_order`. The two relationships that cross a schema
boundary carry no foreign key by design — enforcing them in the database would couple
services that are meant to deploy independently.

---

## Request lifecycle

```mermaid
flowchart LR
    R[Request] --> CID[CorrelationIdFilter<br/>MDC + response header]
    CID --> RL[RequestLoggingFilter<br/>one line in, one out]
    RL --> JWT[JwtAuthenticationFilter<br/>populates SecurityContext]
    JWT --> SEC[authorizeRequests<br/>path rules]
    SEC --> INT[InternalApiKeyInterceptor<br/>/api/v1/internal/** only]
    INT --> PRE["@PreAuthorize<br/>role and ownership"]
    PRE --> VAL["@Valid<br/>Bean Validation"]
    VAL --> CTRL[Controller]
    CTRL --> SVC["Service @Transactional"]
    SVC --> REPO[Repository]

    CTRL -.throws.-> GEH[GlobalExceptionHandler]
    SVC -.throws.-> GEH
    REPO -.throws.-> GEH
    GEH --> RESP[ApiResponse with the right status]
```

Anything thrown at any layer lands in one `@RestControllerAdvice`, so no controller
contains a try/catch and every failure reaches the client in the same envelope.
