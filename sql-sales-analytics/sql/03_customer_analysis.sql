-- ============================================================================
-- Day 3: 03_customer_analysis.sql
-- Customer Aggregations, Lifetime Value & Repeat Purchasing Behavior
-- ============================================================================

-- 1. Top 10 High-Value Customers by Total Spend
SELECT 
    c.CustomerKey,
    c.FirstName || ' ' || c.LastName AS customer_name,
    COUNT(DISTINCT s.OrderNumber) AS total_orders,
    SUM(s.OrderQuantity) AS total_units_purchased,
    ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS total_spent,
    ROUND(SUM(s.OrderQuantity * p.ProductPrice) / COUNT(DISTINCT s.OrderNumber), 2) AS avg_order_value
FROM SalesOrderDetail s
JOIN Customer c ON s.CustomerKey = c.CustomerKey
JOIN Product p ON s.ProductKey = p.ProductKey
GROUP BY c.CustomerKey, c.FirstName, c.LastName
ORDER BY total_spent DESC
LIMIT 10;



-- 2. Overall Customer Purchasing Summary Metrics
SELECT 
    COUNT(DISTINCT c.CustomerKey) AS total_active_customers,
    COUNT(DISTINCT s.OrderNumber) AS total_orders_placed,
    ROUND(SUM(s.OrderQuantity * p.ProductPrice) / COUNT(DISTINCT c.CustomerKey), 2) AS avg_revenue_per_customer,
    ROUND(CAST(COUNT(DISTINCT s.OrderNumber) AS DOUBLE) / COUNT(DISTINCT c.CustomerKey), 2) AS avg_orders_per_customer
FROM SalesOrderDetail s
JOIN Customer c ON s.CustomerKey = c.CustomerKey
JOIN Product p ON s.ProductKey = p.ProductKey;


-- 3. Repeat Purchase Frequency Distribution
SELECT 
    order_bucket,
    COUNT(CustomerKey) AS customer_count,
    ROUND(SUM(total_spent), 2) AS total_revenue
FROM (
    SELECT 
        s.CustomerKey,
        COUNT(DISTINCT s.OrderNumber) AS total_orders,
        SUM(s.OrderQuantity * p.ProductPrice) AS total_spent,
        CASE 
            WHEN COUNT(DISTINCT s.OrderNumber) = 1 THEN '1 Single Order'
            WHEN COUNT(DISTINCT s.OrderNumber) BETWEEN 2 AND 3 THEN '2-3 Repeat Orders'
            ELSE '4+ Frequent Orders'
        END AS order_bucket
    FROM SalesOrderDetail s
    JOIN Product p ON s.ProductKey = p.ProductKey
    GROUP BY s.CustomerKey
) customer_summary
GROUP BY order_bucket
ORDER BY total_revenue DESC;