-- =====================================================================
-- Reporting queries (Phase 5 deliverable, delivered early)
-- =====================================================================
-- These are analyst queries, not application queries. They deliberately join
-- across the three service schemas, which no service is allowed to do at
-- runtime. In a production system these would run against a read model built
-- from the Kafka order events rather than against the operational tables.
--
-- Run with:  mysql -h 127.0.0.1 -u root -proot < sql/reports.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Top 5 selling products by units sold
--    Cancelled orders are excluded - they were never fulfilled.
-- ---------------------------------------------------------------------
SELECT  p.id                      AS product_id,
        p.sku,
        p.name                    AS product_name,
        c.name                    AS category,
        SUM(oi.quantity)          AS units_sold,
        SUM(oi.line_total)        AS revenue,
        COUNT(DISTINCT o.id)      AS order_count
FROM        oms_order.order_items oi
JOIN        oms_order.orders      o  ON o.id = oi.order_id
JOIN        oms_product.products  p  ON p.id = oi.product_id
LEFT JOIN   oms_product.categories c ON c.id = p.category_id
WHERE       o.status <> 'CANCELLED'
GROUP BY    p.id, p.sku, p.name, c.name
ORDER BY    units_sold DESC, revenue DESC
LIMIT 5;

-- ---------------------------------------------------------------------
-- 2. Users with the highest order count
--    LEFT JOIN so that users who never ordered still appear, with zero.
-- ---------------------------------------------------------------------
SELECT  u.id                                   AS user_id,
        u.name,
        u.email,
        COUNT(o.id)                            AS total_orders,
        COALESCE(SUM(CASE WHEN o.status <> 'CANCELLED' THEN o.total_amount END), 0) AS lifetime_value,
        SUM(CASE WHEN o.status = 'CANCELLED' THEN 1 ELSE 0 END)                     AS cancelled_orders,
        MAX(o.placed_at)                       AS last_order_at
FROM        oms_user.users   u
LEFT JOIN   oms_order.orders o ON o.user_id = u.id
GROUP BY    u.id, u.name, u.email
ORDER BY    total_orders DESC, lifetime_value DESC
LIMIT 20;

-- ---------------------------------------------------------------------
-- 3. Monthly sales report - orders, units, revenue and average order value
-- ---------------------------------------------------------------------
SELECT  DATE_FORMAT(o.placed_at, '%Y-%m')       AS sales_month,
        COUNT(DISTINCT o.id)                    AS orders_placed,
        COUNT(DISTINCT o.user_id)               AS distinct_customers,
        COALESCE(SUM(oi.quantity), 0)           AS units_sold,
        ROUND(SUM(o.total_amount), 2)           AS gross_revenue,
        ROUND(AVG(o.total_amount), 2)           AS average_order_value
FROM        oms_order.orders      o
LEFT JOIN   oms_order.order_items oi ON oi.order_id = o.id
WHERE       o.status <> 'CANCELLED'
GROUP BY    DATE_FORMAT(o.placed_at, '%Y-%m')
ORDER BY    sales_month DESC;

-- ---------------------------------------------------------------------
-- 4. Pending orders - placed but not yet shipped, oldest first,
--    with the age in hours so an ops team can spot what is stuck.
-- ---------------------------------------------------------------------
SELECT  o.id                                        AS order_id,
        o.order_number,
        o.status,
        u.name                                      AS customer,
        u.email,
        o.total_amount,
        o.placed_at,
        TIMESTAMPDIFF(HOUR, o.placed_at, NOW())     AS age_hours,
        COUNT(oi.id)                                AS line_items
FROM        oms_order.orders      o
JOIN        oms_user.users        u  ON u.id = o.user_id
LEFT JOIN   oms_order.order_items oi ON oi.order_id = o.id
WHERE       o.status IN ('PENDING', 'CONFIRMED')
GROUP BY    o.id, o.order_number, o.status, u.name, u.email, o.total_amount, o.placed_at
ORDER BY    o.placed_at ASC;

-- ---------------------------------------------------------------------
-- 5. Revenue by month, with month-over-month growth
--    Written without window functions so it also runs on MySQL 5.7.
-- ---------------------------------------------------------------------
SELECT  curr.sales_month,
        curr.revenue,
        prev.revenue                                            AS previous_month_revenue,
        ROUND(curr.revenue - COALESCE(prev.revenue, 0), 2)      AS change_amount,
        CASE WHEN prev.revenue IS NULL OR prev.revenue = 0 THEN NULL
             ELSE ROUND(((curr.revenue - prev.revenue) / prev.revenue) * 100, 2)
        END                                                     AS growth_percent
FROM (
        SELECT DATE_FORMAT(placed_at, '%Y-%m')  AS sales_month,
               ROUND(SUM(total_amount), 2)      AS revenue
        FROM   oms_order.orders
        WHERE  status <> 'CANCELLED'
        GROUP BY DATE_FORMAT(placed_at, '%Y-%m')
     ) curr
LEFT JOIN (
        SELECT DATE_FORMAT(placed_at, '%Y-%m')  AS sales_month,
               ROUND(SUM(total_amount), 2)      AS revenue
        FROM   oms_order.orders
        WHERE  status <> 'CANCELLED'
        GROUP BY DATE_FORMAT(placed_at, '%Y-%m')
     ) prev
     ON prev.sales_month = DATE_FORMAT(
            DATE_SUB(STR_TO_DATE(CONCAT(curr.sales_month, '-01'), '%Y-%m-%d'), INTERVAL 1 MONTH),
            '%Y-%m')
ORDER BY curr.sales_month DESC;
