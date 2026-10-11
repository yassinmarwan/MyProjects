
SELECT 
    table_name, 
    column_name, 
    data_type, 
    is_nullable
FROM information_schema.columns
WHERE table_schema = 'main'
ORDER BY table_name, ordinal_position;



SELECT 'Customer' AS table_name, COUNT(*) AS total_rows FROM Customer
UNION ALL
SELECT 'Product', COUNT(*) FROM Product
UNION ALL
SELECT 'ProductCategory', COUNT(*) FROM ProductCategory
UNION ALL
SELECT 'ProductSubcategory', COUNT(*) FROM ProductSubcategory
UNION ALL
SELECT 'SalesOrderDetail', COUNT(*) FROM SalesOrderDetail
UNION ALL
SELECT 'SalesTerritory', COUNT(*) FROM SalesTerritory;



SELECT 
    'Customer' AS table_name, 
    COUNT(*) AS total_rows, 
    COUNT(DISTINCT CustomerKey) AS distinct_pk_count,
    COUNT(*) - COUNT(CustomerKey) AS null_pk_count
FROM Customer
UNION ALL
SELECT 'Product', COUNT(*), COUNT(DISTINCT ProductKey), COUNT(*) - COUNT(ProductKey) FROM Product
UNION ALL
SELECT 'ProductCategory', COUNT(*), COUNT(DISTINCT ProductCategoryKey), COUNT(*) - COUNT(ProductCategoryKey) FROM ProductCategory
UNION ALL
SELECT 'ProductSubcategory', COUNT(*), COUNT(DISTINCT ProductSubcategoryKey), COUNT(*) - COUNT(ProductSubcategoryKey) FROM ProductSubcategory
UNION ALL
SELECT 'SalesTerritory', COUNT(*), COUNT(DISTINCT SalesTerritoryKey), COUNT(*) - COUNT(SalesTerritoryKey) FROM SalesTerritory;



SELECT COUNT(*) AS orphan_products
FROM Product p
LEFT JOIN ProductSubcategory ps ON p.ProductSubcategoryKey = ps.ProductSubcategoryKey
WHERE p.ProductSubcategoryKey IS NOT NULL AND ps.ProductSubcategoryKey IS NULL;

SELECT COUNT(*) AS orphan_sales_products
FROM SalesOrderDetail s
LEFT JOIN Product p ON s.ProductKey = p.ProductKey
WHERE s.ProductKey IS NOT NULL AND p.ProductKey IS NULL;

SELECT COUNT(*) AS orphan_sales_customers
FROM SalesOrderDetail s
LEFT JOIN Customer c ON s.CustomerKey = c.CustomerKey
WHERE s.CustomerKey IS NOT NULL AND c.CustomerKey IS NULL;

SELECT COUNT(*) AS orphan_sales_territories
FROM SalesOrderDetail s
LEFT JOIN SalesTerritory st ON s.TerritoryKey = st.SalesTerritoryKey
WHERE s.TerritoryKey IS NOT NULL AND st.SalesTerritoryKey IS NULL;