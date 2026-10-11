-- ============================================================================
-- Day 4: 08_views.sql
-- Materialized & Logical Views for Streamlit Dashboard & Reporting
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Executive Summary Daily KPIs View
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_daily_kpis AS
SELECT 
    strptime(s.OrderDate, '%m/%d/%Y') AS order_date,
    st.Region AS territory_region,
    st.Country AS territory_country,
    COUNT(DISTINCT s.OrderNumber) AS total_orders,
    SUM(s.OrderQuantity) AS total_units_sold,
    ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS total_revenue,
    ROUND(SUM(s.OrderQuantity * (p.ProductPrice - COALESCE(p.ProductCost, 0))), 2) AS total_profit
FROM SalesOrderDetail s
JOIN Product p ON s.ProductKey = p.ProductKey
JOIN SalesTerritory st ON s.TerritoryKey = st.SalesTerritoryKey
GROUP BY strptime(s.OrderDate, '%m/%d/%Y'), st.Region, st.Country;


-- ----------------------------------------------------------------------------
-- 2. Customer 360 & RFM Profile View
-- ----------------------------------------------------------------------------
-- ----------------------------------------------------------------------------
-- 2. Customer 360 & RFM Profile View
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_customer_360 AS
WITH max_snapshot_date AS (
    SELECT MAX(strptime(OrderDate, '%m/%d/%Y')) AS max_date 
    FROM SalesOrderDetail
),
customer_summary AS (
    SELECT 
        s.CustomerKey,
        c.FirstName || ' ' || c.LastName AS customer_name,
        c.EmailAddress,
        MAX(strptime(s.OrderDate, '%m/%d/%Y')) AS last_order_date,
        DATEDIFF('day', MAX(strptime(s.OrderDate, '%m/%d/%Y')), m.max_date) AS recency_days,
        COUNT(DISTINCT s.OrderNumber) AS total_orders,
        SUM(s.OrderQuantity) AS total_units_purchased,
        ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS monetary_value
    FROM SalesOrderDetail s
    JOIN Customer c ON s.CustomerKey = c.CustomerKey
    JOIN Product p ON s.ProductKey = p.ProductKey
    CROSS JOIN max_snapshot_date m
    GROUP BY s.CustomerKey, c.FirstName, c.LastName, c.EmailAddress, m.max_date
),
scores AS (
    SELECT 
        *,
        NTILE(4) OVER (ORDER BY recency_days DESC) AS r_score,
        NTILE(4) OVER (ORDER BY total_orders ASC) AS f_score,
        NTILE(4) OVER (ORDER BY monetary_value ASC) AS m_score
    FROM customer_summary
)
SELECT 
    CustomerKey,
    customer_name,
    EmailAddress,
    last_order_date,
    recency_days,
    total_orders,
    total_units_purchased,
    monetary_value,
    r_score,
    f_score,
    m_score,
    CAST(r_score AS VARCHAR) || CAST(f_score AS VARCHAR) || CAST(m_score AS VARCHAR) AS rfm_code,
    CASE 
        WHEN r_score = 4 AND f_score = 4 AND m_score = 4 THEN 'Champions'
        WHEN r_score >= 3 AND f_score >= 3 THEN 'Loyal Customers'
        WHEN r_score >= 3 AND f_score < 3 AND m_score >= 3 THEN 'High-Value Recent'
        WHEN r_score >= 3 AND f_score < 3 AND m_score < 3 THEN 'New / Promising'
        WHEN r_score = 2 AND f_score >= 3 THEN 'At-Risk Loyalists'
        WHEN r_score = 1 AND f_score >= 3 THEN 'Cant Lose Them'
        WHEN r_score = 1 AND f_score < 3 THEN 'Hibernating / Lost'
        ELSE 'Needs Attention'
    END AS rfm_segment
FROM scores;


-- ----------------------------------------------------------------------------
-- 3. Product Catalog Performance View
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_product_performance AS
SELECT 
    p.ProductKey,
    p.ProductName,
    p.ModelName,
    pc.CategoryName,
    ps.SubcategoryName,
    p.ProductPrice,
    COALESCE(p.ProductCost, 0) AS ProductCost,
    ROUND(p.ProductPrice - COALESCE(p.ProductCost, 0), 2) AS unit_margin,
    COALESCE(SUM(s.OrderQuantity), 0) AS total_units_sold,
    COALESCE(COUNT(DISTINCT s.OrderNumber), 0) AS total_orders,
    ROUND(COALESCE(SUM(s.OrderQuantity * p.ProductPrice), 0), 2) AS total_revenue,
    ROUND(COALESCE(SUM(s.OrderQuantity * (p.ProductPrice - COALESCE(p.ProductCost, 0))), 0), 2) AS total_profit
FROM Product p
LEFT JOIN ProductSubcategory ps ON p.ProductSubcategoryKey = ps.ProductSubcategoryKey
LEFT JOIN ProductCategory pc ON ps.ProductCategoryKey = pc.ProductCategoryKey
LEFT JOIN SalesOrderDetail s ON p.ProductKey = s.ProductKey
GROUP BY p.ProductKey, p.ProductName, p.ModelName, pc.CategoryName, ps.SubcategoryName, p.ProductPrice, p.ProductCost;


-- ----------------------------------------------------------------------------
-- 4. Monthly YoY/MoM Growth View
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_monthly_growth AS
WITH monthly_revenue AS (
    SELECT 
        EXTRACT(YEAR FROM strptime(s.OrderDate, '%m/%d/%Y')) AS sales_year,
        EXTRACT(MONTH FROM strptime(s.OrderDate, '%m/%d/%Y')) AS sales_month,
        ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS current_revenue
    FROM SalesOrderDetail s
    JOIN Product p ON s.ProductKey = p.ProductKey
    GROUP BY sales_year, sales_month
)
SELECT 
    sales_year,
    sales_month,
    current_revenue,
    LAG(current_revenue, 1) OVER (ORDER BY sales_year, sales_month) AS prior_month_revenue,
    ROUND(
        ((current_revenue - LAG(current_revenue, 1) OVER (ORDER BY sales_year, sales_month)) / 
        NULLIF(LAG(current_revenue, 1) OVER (ORDER BY sales_year, sales_month), 0)) * 100, 2
    ) AS mom_growth_pct,
    LAG(current_revenue, 12) OVER (ORDER BY sales_year, sales_month) AS prior_year_revenue,
    ROUND(
        ((current_revenue - LAG(current_revenue, 12) OVER (ORDER BY sales_year, sales_month)) / 
        NULLIF(LAG(current_revenue, 12) OVER (ORDER BY sales_year, sales_month), 0)) * 100, 2
    ) AS yoy_growth_pct
FROM monthly_revenue;