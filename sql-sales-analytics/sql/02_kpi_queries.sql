-- ============================================================================
-- Day 2: 02_kpi_queries.sql
-- Core Business KPIs adapted for AdventureWorks
-- ============================================================================

-- 1. High-Level Executive Summary
SELECT 
    ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS total_revenue,
    COUNT(DISTINCT s.OrderNumber) AS total_orders,
    SUM(s.OrderQuantity) AS total_units_sold,
    ROUND(SUM(s.OrderQuantity * p.ProductPrice) / COUNT(DISTINCT s.OrderNumber), 2) AS average_order_value
FROM SalesOrderDetail s
JOIN Product p ON s.ProductKey = p.ProductKey;


-- 2. Sales & Revenue Breakdown by Product Category
SELECT 
    pc.CategoryName AS category_name,
    COUNT(DISTINCT s.OrderNumber) AS total_orders,
    SUM(s.OrderQuantity) AS total_units_sold,
    ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS total_revenue
FROM SalesOrderDetail s
JOIN Product p ON s.ProductKey = p.ProductKey
JOIN ProductSubcategory ps ON p.ProductSubcategoryKey = ps.ProductSubcategoryKey
JOIN ProductCategory pc ON ps.ProductCategoryKey = pc.ProductCategoryKey
GROUP BY pc.CategoryName
ORDER BY total_revenue DESC;


-- 3. Monthly Sales Revenue Trend
SELECT 
    EXTRACT(YEAR FROM strptime(s.OrderDate, '%m/%d/%Y')) AS sales_year,
    EXTRACT(MONTH FROM strptime(s.OrderDate, '%m/%d/%Y')) AS sales_month,
    COUNT(DISTINCT s.OrderNumber) AS total_orders,
    ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS monthly_revenue
FROM SalesOrderDetail s
JOIN Product p ON s.ProductKey = p.ProductKey
GROUP BY sales_year, sales_month
ORDER BY sales_year ASC, sales_month ASC;


-- 4. Top Performing Sales Territories
SELECT 
    st.Region AS territory_region,
    st.Country AS country,
    COUNT(DISTINCT s.OrderNumber) AS total_orders,
    ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS total_revenue
FROM SalesOrderDetail s
JOIN Product p ON s.ProductKey = p.ProductKey
JOIN SalesTerritory st ON s.TerritoryKey = st.SalesTerritoryKey
GROUP BY st.Region, st.Country
ORDER BY total_revenue DESC;