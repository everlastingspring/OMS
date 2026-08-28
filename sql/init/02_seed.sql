-- =====================================================================
-- Seed data for local development and the Postman collection
-- =====================================================================
-- Passwords are real BCrypt hashes (strength 10) so you can log in immediately:
--   admin@oms.com   / Admin@123   (ROLE_ADMIN - required to create products)
--   priya@oms.com   / User@123
--   rahul@oms.com   / User@123
-- Registration through the API always creates ROLE_USER, so an admin has to be
-- seeded here - there is no privilege-escalation endpoint by design.
-- =====================================================================

USE oms_user;

INSERT INTO users (id, name, email, password, phone, role, active, created_at, updated_at) VALUES
(1, 'OMS Admin', 'admin@oms.com', '$2b$10$D6sAlQVe0RoyF8e.VeKZPeuFVVzEcRJDiwr6fHNlvh/kXswjuBbpW', '9880000001', 'ADMIN', TRUE, NOW(), NOW()),
(2, 'Priya Nair',  'priya@oms.com', '$2b$10$u/PyVRiYKY1RU5wv5H9FHeDp9caMc/nBgzHsJBppGbPnRGJLO.NOG', '9880000002', 'USER', TRUE, NOW(), NOW()),
(3, 'Rahul Menon', 'rahul@oms.com', '$2b$10$u/PyVRiYKY1RU5wv5H9FHeDp9caMc/nBgzHsJBppGbPnRGJLO.NOG', '9880000003', 'USER', TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE email = VALUES(email);

INSERT INTO addresses (id, user_id, label, line1, line2, city, state, postal_code, country, is_default, created_at, updated_at) VALUES
(1, 2, 'HOME', '14, 3rd Cross, Indiranagar', 'Near Metro Station', 'Bengaluru', 'Karnataka', '560038', 'India', TRUE, NOW(), NOW()),
(2, 3, 'WORK', 'Prestige Tech Park, Block C', 'Marathahalli', 'Bengaluru', 'Karnataka', '560103', 'India', TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE line1 = VALUES(line1);

USE oms_product;

INSERT INTO categories (id, name, description, active, created_at, updated_at) VALUES
(1, 'Electronics', 'Phones, laptops, audio and accessories', TRUE, NOW(), NOW()),
(2, 'Home & Kitchen', 'Appliances and cookware', TRUE, NOW(), NOW()),
(3, 'Books', 'Print and reference titles', TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO products (id, sku, name, description, price, stock_quantity, category_id, active, version, created_at, updated_at) VALUES
(1, 'ELEC-PHN-001', 'Aurora 5G Smartphone 128GB',   '6.5 inch AMOLED, 5000 mAh battery',        24999.00, 40,  1, TRUE, 0, NOW(), NOW()),
(2, 'ELEC-LAP-002', 'Vector Ultrabook 14 i5',       '16 GB RAM, 512 GB NVMe SSD',              68999.00, 15,  1, TRUE, 0, NOW(), NOW()),
(3, 'ELEC-AUD-003', 'Pulse ANC Wireless Headphones','Active noise cancelling, 30 hr playback',  7499.00, 120, 1, TRUE, 0, NOW(), NOW()),
(4, 'HOME-KTC-001', 'SteelCore 5L Pressure Cooker', 'Induction and gas compatible',             2899.00, 75,  2, TRUE, 0, NOW(), NOW()),
(5, 'HOME-APP-002', 'BreezeMax Air Fryer 4L',       'Digital touch panel, 8 presets',           6499.00, 30,  2, TRUE, 0, NOW(), NOW()),
(6, 'BOOK-TEC-001', 'Designing Data-Intensive Applications', 'Reference title on distributed systems', 1299.00, 200, 3, TRUE, 0, NOW(), NOW()),
(7, 'BOOK-TEC-002', 'Effective Java, 3rd Edition',  'Java best practices',                       999.00, 150, 3, TRUE, 0, NOW(), NOW()),
(8, 'ELEC-ACC-004', 'Nimbus 65W GaN Charger',       'Dual USB-C fast charger',                  2199.00, 0,   1, TRUE, 0, NOW(), NOW()),
(9, 'HOME-KTC-003', 'Retired Ceramic Cookware Set', 'Kept to demonstrate soft delete',          4599.00, 10,  2, FALSE, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE sku = VALUES(sku);
