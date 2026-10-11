-- ============================================================================
-- Day 3: 06_window_functions.sql
-- Advanced Analytics: Window Functions (Ranking, Percentiles & Order Sequences)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Top 3 Best-Selling Products per Category (DENSE_RANK)
-- ----------------------------------------------------------------------------
WITH product_revenue AS (
    SELECT 
        pc.CategoryName,
        p.ProductKey,
        p.ProductName,
        SUM(s.OrderQuantity) AS total_units_sold,
        ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS total_revenue
    FROM SalesOrderDetail s
    JOIN Product p ON s.ProductKey = p.ProductKey
    JOIN ProductSubcategory ps ON p.ProductSubcategoryKey = ps.ProductSubcategoryKey
    JOIN ProductCategory pc ON ps.ProductCategoryKey = pc.ProductCategoryKey
    GROUP BY pc.CategoryName, p.ProductKey, p.ProductName
),
ranked_products AS (
    SELECT 
        CategoryName,
        ProductKey,
        ProductName,
        total_units_sold,
        total_revenue,
        DENSE_RANK() OVER (
            PARTITION BY CategoryName 
            ORDER BY total_revenue DESC
        ) AS product_rank
    FROM product_revenue
)
SELECT 
    CategoryName,
    product_rank,
    ProductName,
    total_units_sold,
    total_revenue
FROM ranked_products
WHERE product_rank <= 3
ORDER BY CategoryName, product_rank;


-- ----------------------------------------------------------------------------
-- 2. Customer Spend Quartiles & Percentiles (NTILE & PERCENT_RANK)
-- ----------------------------------------------------------------------------
WITH customer_totals AS (
    SELECT 
        c.CustomerKey,
        c.FirstName || ' ' || c.LastName AS customer_name,
        COUNT(DISTINCT s.OrderNumber) AS total_orders,
        ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS total_spend
    FROM SalesOrderDetail s
    JOIN Customer c ON s.CustomerKey = c.CustomerKey
    JOIN Product p ON s.ProductKey = p.ProductKey
    GROUP BY c.CustomerKey, c.FirstName, c.LastName
),
customer_percentiles AS (
    SELECT 
        CustomerKey,
        customer_name,
        total_orders,
        total_spend,
        NTILE(4) OVER (ORDER BY total_spend DESC) AS spend_quartile,
        ROUND(PERCENT_RANK() OVER (ORDER BY total_spend ASC) * 100, 2) AS spend_percentile
    FROM customer_totals
)
SELECT 
    CustomerKey,
    customer_name,
    total_orders,
    total_spend,
    spend_percentile,
    CASE spend_quartile
        WHEN 1 THEN 'Tier 1: Top 25% (VIP)'
        WHEN 2 THEN 'Tier 2: High Value'
        WHEN 3 THEN 'Tier 3: Mid Value'
        WHEN 4 THEN 'Tier 4: Low Value'
    END AS customer_tier
FROM customer_percentiles
ORDER BY total_spend DESC
LIMIT 20;


-- ----------------------------------------------------------------------------
-- 3. Customer Sequential Order History & Spend Deltas (ROW_NUMBER & LAG)
-- ----------------------------------------------------------------------------
WITH customer_orders AS (
    SELECT 
        s.CustomerKey,
        s.OrderNumber,
        strptime(s.OrderDate, '%m/%d/%Y') AS order_date,
        ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS order_value
    FROM SalesOrderDetail s
    JOIN Product p ON s.ProductKey = p.ProductKey
    GROUP BY s.CustomerKey, s.OrderNumber, s.OrderDate
),
sequenced_orders AS (
    SELECT 
        CustomerKey,
        OrderNumber,
        order_date,
        order_value,
        ROW_NUMBER() OVER (
            PARTITION BY CustomerKey 
            ORDER BY order_date ASC, OrderNumber ASC
        ) AS order_sequence,
        LAG(order_value, 1) OVER (
            PARTITION BY CustomerKey 
            ORDER BY order_date ASC, OrderNumber ASC
        ) AS prev_order_value
    FROM customer_orders
)
SELECT 
    CustomerKey,
    OrderNumber,
    order_date,
    order_sequence,
    order_value,
    prev_order_value,
    ROUND(order_value - prev_order_value, 2) AS spend_change_from_last_order
FROM sequenced_orders
WHERE order_sequence > 1
ORDER BY CustomerKey, order_sequence
LIMIT 20;