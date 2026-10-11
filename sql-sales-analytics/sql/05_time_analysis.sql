-- ============================================================================
-- Day 3: 05_time_analysis.sql
-- Time-Series Analytics: MoM / YoY Revenue Growth & Rolling Metrics
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Monthly Revenue, Month-over-Month (MoM) & Year-over-Year (YoY) Growth
-- ----------------------------------------------------------------------------
WITH monthly_revenue AS (
    SELECT 
        EXTRACT(YEAR FROM strptime(s.OrderDate, '%m/%d/%Y')) AS sales_year,
        EXTRACT(MONTH FROM strptime(s.OrderDate, '%m/%d/%Y')) AS sales_month,
        ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS current_revenue
    FROM SalesOrderDetail s
    JOIN Product p ON s.ProductKey = p.ProductKey
    GROUP BY sales_year, sales_month
),
monthly_growth AS (
    SELECT 
        sales_year,
        sales_month,
        current_revenue,
        LAG(current_revenue, 1) OVER (ORDER BY sales_year, sales_month) AS prior_month_revenue,
        LAG(current_revenue, 12) OVER (ORDER BY sales_year, sales_month) AS prior_year_revenue
    FROM monthly_revenue
)
SELECT 
    sales_year,
    sales_month,
    current_revenue,
    prior_month_revenue,
    ROUND(
        ((current_revenue - prior_month_revenue) / NULLIF(prior_month_revenue, 0)) * 100, 2
    ) AS mom_growth_pct,
    prior_year_revenue,
    ROUND(
        ((current_revenue - prior_year_revenue) / NULLIF(prior_year_revenue, 0)) * 100, 2
    ) AS yoy_growth_pct
FROM monthly_growth
ORDER BY sales_year ASC, sales_month ASC;


-- ----------------------------------------------------------------------------
-- 2. Quarterly Revenue & Year-over-Year (YoY) Quarterly Growth
-- ----------------------------------------------------------------------------
WITH quarterly_revenue AS (
    SELECT 
        EXTRACT(YEAR FROM strptime(s.OrderDate, '%m/%d/%Y')) AS sales_year,
        EXTRACT(QUARTER FROM strptime(s.OrderDate, '%m/%d/%Y')) AS sales_quarter,
        ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS current_quarter_revenue
    FROM SalesOrderDetail s
    JOIN Product p ON s.ProductKey = p.ProductKey
    GROUP BY sales_year, sales_quarter
),
quarterly_growth AS (
    SELECT 
        sales_year,
        sales_quarter,
        current_quarter_revenue,
        LAG(current_quarter_revenue, 4) OVER (ORDER BY sales_year, sales_quarter) AS same_quarter_prior_year
    FROM quarterly_revenue
)
SELECT 
    sales_year,
    'Q' || CAST(sales_quarter AS VARCHAR) AS quarter,
    current_quarter_revenue,
    same_quarter_prior_year,
    ROUND(
        ((current_quarter_revenue - same_quarter_prior_year) / NULLIF(same_quarter_prior_year, 0)) * 100, 2
    ) AS yoy_quarterly_growth_pct
FROM quarterly_growth
ORDER BY sales_year ASC, sales_quarter ASC;


-- ----------------------------------------------------------------------------
-- 3. Running Total (Cumulative Revenue) & 3-Month Moving Average
-- ----------------------------------------------------------------------------
WITH monthly_sales AS (
    SELECT 
        EXTRACT(YEAR FROM strptime(s.OrderDate, '%m/%d/%Y')) AS sales_year,
        EXTRACT(MONTH FROM strptime(s.OrderDate, '%m/%d/%Y')) AS sales_month,
        ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS monthly_revenue
    FROM SalesOrderDetail s
    JOIN Product p ON s.ProductKey = p.ProductKey
    GROUP BY sales_year, sales_month
)
SELECT 
    sales_year,
    sales_month,
    monthly_revenue,
    ROUND(
        SUM(monthly_revenue) OVER (
            ORDER BY sales_year, sales_month 
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
        ), 2
    ) AS cumulative_running_revenue,
    ROUND(
        AVG(monthly_revenue) OVER (
            ORDER BY sales_year, sales_month 
            ROWS BETWEEN 2 PRECEDING AND CURRENT ROW
        ), 2
    ) AS rolling_3_month_avg_revenue
FROM monthly_sales
ORDER BY sales_year ASC, sales_month ASC;