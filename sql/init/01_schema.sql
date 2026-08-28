-- =====================================================================
-- Order Management System - MySQL schema
-- =====================================================================
-- One MySQL instance, three schemas. Each service owns exactly one schema
-- and never queries another at runtime, which keeps the service boundary
-- real. Cross-schema joins appear only in sql/reports.sql, which is analyst
-- tooling rather than application code. In production those reports would be
-- served from a read model fed by the Kafka order events.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS oms_user    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS oms_product CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS oms_order   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'oms'@'%' IDENTIFIED BY 'oms123';
GRANT ALL PRIVILEGES ON `oms\_%`.* TO 'oms'@'%';
FLUSH PRIVILEGES;

-- ---------------------------------------------------------------------
-- oms_user  (owned by user-service)
-- ---------------------------------------------------------------------
USE oms_user;

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL,
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt hash, never plaintext',
    phone       VARCHAR(20)  NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_role (role)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS addresses (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    label        VARCHAR(50)  NULL COMMENT 'HOME, WORK, ...',
    line1        VARCHAR(200) NOT NULL,
    line2        VARCHAR(200) NULL,
    city         VARCHAR(100) NOT NULL,
    state        VARCHAR(100) NOT NULL,
    postal_code  VARCHAR(20)  NOT NULL,
    country      VARCHAR(100) NOT NULL,
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_addresses_user (user_id),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- oms_product  (owned by product-service)
-- ---------------------------------------------------------------------
USE oms_product;

CREATE TABLE IF NOT EXISTS categories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_name (name)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS products (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    sku            VARCHAR(50)   NOT NULL,
    name           VARCHAR(200)  NOT NULL,
    description    TEXT          NULL,
    price          DECIMAL(12,2) NOT NULL,
    stock_quantity INT           NOT NULL DEFAULT 0,
    category_id    BIGINT        NULL,
    active         BOOLEAN       NOT NULL DEFAULT TRUE COMMENT 'FALSE = soft deleted',
    version        BIGINT        NOT NULL DEFAULT 0 COMMENT 'JPA @Version, guards concurrent stock updates',
    created_at     DATETIME      NOT NULL,
    updated_at     DATETIME      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_sku (sku),
    KEY idx_products_name (name),
    KEY idx_products_category (category_id),
    KEY idx_products_price (price),
    KEY idx_products_active (active),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- oms_order  (owned by order-service, populated from Phase 3)
-- ---------------------------------------------------------------------
USE oms_order;

CREATE TABLE IF NOT EXISTS orders (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    order_number        VARCHAR(40)   NOT NULL,
    user_id             BIGINT        NOT NULL COMMENT 'No FK: users live in another schema and another service',
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    total_amount        DECIMAL(14,2) NOT NULL,
    shipping_address    VARCHAR(500)  NULL,
    placed_at           DATETIME      NOT NULL,
    cancelled_at        DATETIME      NULL,
    cancellation_reason VARCHAR(255)  NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          DATETIME      NOT NULL,
    updated_at          DATETIME      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_number (order_number),
    KEY idx_orders_user (user_id),
    KEY idx_orders_status (status),
    KEY idx_orders_placed_at (placed_at)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS order_items (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    order_id     BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL COMMENT 'No FK: products live in another schema and another service',
    sku          VARCHAR(50)   NULL,
    product_name VARCHAR(200)  NULL COMMENT 'Denormalised on purpose - the order must survive a product rename',
    quantity     INT           NOT NULL,
    unit_price   DECIMAL(12,2) NOT NULL COMMENT 'Price at time of order, not current price',
    line_total   DECIMAL(14,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_items_order (order_id),
    KEY idx_order_items_product (product_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
) ENGINE = InnoDB;
