-- ============================================================================
-- Day 4: 04_product_analysis.sql
-- Product Aggregations, Profit Margins & Catalog Performance
-- ============================================================================

-- 1. Top 10 Best-Selling Products by Revenue and Profit
SELECT 
    p.ProductKey,
    p.ProductName,
    ps.SubcategoryName,
    SUM(s.OrderQuantity) AS total_units_sold,
    ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS total_revenue,
    ROUND(SUM(s.OrderQuantity * (p.ProductPrice - p.ProductCost)), 2) AS estimated_profit
FROM SalesOrderDetail s
JOIN Product p ON s.ProductKey = p.ProductKey
LEFT JOIN ProductSubcategory ps ON p.ProductSubcategoryKey = ps.ProductSubcategoryKey
GROUP BY p.ProductKey, p.ProductName, ps.SubcategoryName
ORDER BY total_revenue DESC
LIMIT 10;

-- 2. Subcategory Revenue and Profitability Matrix
SELECT 
    pc.CategoryName,
    ps.SubcategoryName,
    COUNT(DISTINCT p.ProductKey) AS product_count,
    SUM(s.OrderQuantity) AS units_sold,
    ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS total_revenue,
    ROUND(SUM(s.OrderQuantity * (p.ProductPrice - p.ProductCost)), 2) AS total_profit,
    ROUND(
        (SUM(s.OrderQuantity * (p.ProductPrice - p.ProductCost)) / 
        NULLIF(SUM(s.OrderQuantity * p.ProductPrice), 0)) * 100, 2
    ) AS profit_margin_pct
FROM Product p
JOIN ProductSubcategory ps ON p.ProductSubcategoryKey = ps.ProductSubcategoryKey
JOIN ProductCategory pc ON ps.ProductCategoryKey = pc.ProductCategoryKey
LEFT JOIN SalesOrderDetail s ON p.ProductKey = s.ProductKey
GROUP BY pc.CategoryName, ps.SubcategoryName
ORDER BY total_revenue DESC;


-- 3. Product Catalog Utilization (Sold vs Unsold Items)
SELECT 
    CASE 
        WHEN s.ProductKey IS NOT NULL THEN 'Sold at least once'
        ELSE 'Unsold Catalog Item'
    END AS catalog_status,
    COUNT(DISTINCT p.ProductKey) AS product_count,
    ROUND(AVG(p.ProductPrice), 2) AS avg_product_price
FROM Product p
LEFT JOIN SalesOrderDetail s ON p.ProductKey = s.ProductKey
GROUP BY catalog_status;