-- ============================================================================
-- Day 4: 09_final_business_questions.sql
-- Strategic Business Insights & Dashboard Reporting Queries
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Executive Revenue & Profit Contribution by Territory Region
-- ----------------------------------------------------------------------------
SELECT 
    territory_region,
    territory_country,
    SUM(total_orders) AS total_orders,
    SUM(total_units_sold) AS total_units_sold,
    ROUND(SUM(total_revenue), 2) AS total_revenue,
    ROUND(SUM(total_profit), 2) AS total_profit,
    ROUND((SUM(total_profit) / NULLIF(SUM(total_revenue), 0)) * 100, 2) AS profit_margin_pct
FROM vw_daily_kpis
GROUP BY territory_region, territory_country
ORDER BY total_revenue DESC;


-- ----------------------------------------------------------------------------
-- 2. Customer RFM Segment Distribution & Total Lifetime Spend
-- ----------------------------------------------------------------------------
SELECT 
    rfm_segment,
    COUNT(CustomerKey) AS customer_count,
    ROUND(AVG(recency_days), 1) AS avg_recency_days,
    ROUND(AVG(total_orders), 2) AS avg_orders_per_customer,
    ROUND(SUM(monetary_value), 2) AS total_segment_revenue,
    ROUND(AVG(monetary_value), 2) AS avg_revenue_per_customer,
    ROUND((SUM(monetary_value) / (SELECT SUM(monetary_value) FROM vw_customer_360)) * 100, 2) AS revenue_share_pct
FROM vw_customer_360
GROUP BY rfm_segment
ORDER BY total_segment_revenue DESC;


-- ----------------------------------------------------------------------------
-- 3. Top 10 Most Profitable Products & Their Margin Impact
-- ----------------------------------------------------------------------------
SELECT 
    ProductKey,
    ProductName,
    CategoryName,
    SubcategoryName,
    total_units_sold,
    total_revenue,
    total_profit,
    ROUND((total_profit / NULLIF(total_revenue, 0)) * 100, 2) AS margin_pct
FROM vw_product_performance
ORDER BY total_profit DESC
LIMIT 10;


-- ----------------------------------------------------------------------------
-- 4. Annual & Monthly Growth Summary with Sequential MoM Trajectory
-- ----------------------------------------------------------------------------
SELECT 
    sales_year,
    sales_month,
    current_revenue,
    prior_month_revenue,
    mom_growth_pct,
    prior_year_revenue,
    yoy_growth_pct
FROM vw_monthly_growth
ORDER BY sales_year ASC, sales_month ASC;