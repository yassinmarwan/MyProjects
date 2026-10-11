-- ============================================================================
-- Day 3: 07_ctes.sql
-- Multi-Step RFM (Recency, Frequency, Monetary) Customer Segmentation
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Granular Customer RFM Scores & Segment Classification
-- ----------------------------------------------------------------------------
WITH max_snapshot_date AS (
    -- Reference date: set to the latest order date in the dataset
    SELECT MAX(strptime(OrderDate, '%m/%d/%Y')) AS max_date 
    FROM SalesOrderDetail
),
rfm_base AS (
    -- Calculate raw Recency (days), Frequency (orders), and Monetary (spend) per customer
    SELECT 
        s.CustomerKey,
        c.FirstName || ' ' || c.LastName AS customer_name,
        MAX(strptime(s.OrderDate, '%m/%d/%Y')) AS last_order_date,
        DATEDIFF('day', MAX(strptime(s.OrderDate, '%m/%d/%Y')), m.max_date) AS recency_days,
        COUNT(DISTINCT s.OrderNumber) AS frequency,
        ROUND(SUM(s.OrderQuantity * p.ProductPrice), 2) AS monetary
    FROM SalesOrderDetail s
    JOIN Customer c ON s.CustomerKey = c.CustomerKey
    JOIN Product p ON s.ProductKey = p.ProductKey
    CROSS JOIN max_snapshot_date m
    GROUP BY s.CustomerKey, c.FirstName, c.LastName, m.max_date
),
rfm_scores AS (
    -- Assign quartile scores (1-4) for R, F, and M
    -- For Recency: lower days = higher score (ORDER BY recency_days DESC)
    SELECT 
        CustomerKey,
        customer_name,
        last_order_date,
        recency_days,
        frequency,
        monetary,
        NTILE(4) OVER (ORDER BY recency_days DESC) AS r_score,
        NTILE(4) OVER (ORDER BY frequency ASC) AS f_score,
        NTILE(4) OVER (ORDER BY monetary ASC) AS m_score
    FROM rfm_base
),
rfm_segments AS (
    -- Combine scores into composite RFM strings and map to strategic segments
    SELECT 
        CustomerKey,
        customer_name,
        recency_days,
        frequency,
        monetary,
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
    FROM rfm_scores
)
SELECT 
    CustomerKey,
    customer_name,
    recency_days,
    frequency,
    monetary,
    rfm_code,
    rfm_segment
FROM rfm_segments
ORDER BY monetary DESC
LIMIT 20;


-- ----------------------------------------------------------------------------
-- 2. Executive RFM Segment Aggregation & Revenue Impact Summary
-- ----------------------------------------------------------------------------
WITH max_snapshot_date AS (
    SELECT MAX(strptime(OrderDate, '%m/%d/%Y')) AS max_date 
    FROM SalesOrderDetail
),
rfm_base AS (
    SELECT 
        s.CustomerKey,
        DATEDIFF('day', MAX(strptime(s.OrderDate, '%m/%d/%Y')), m.max_date) AS recency_days,
        COUNT(DISTINCT s.OrderNumber) AS frequency,
        SUM(s.OrderQuantity * p.ProductPrice) AS monetary
    FROM SalesOrderDetail s
    JOIN Product p ON s.ProductKey = p.ProductKey
    CROSS JOIN max_snapshot_date m
    GROUP BY s.CustomerKey, m.max_date
),
rfm_scores AS (
    SELECT 
        CustomerKey,
        recency_days,
        frequency,
        monetary,
        NTILE(4) OVER (ORDER BY recency_days DESC) AS r_score,
        NTILE(4) OVER (ORDER BY frequency ASC) AS f_score,
        NTILE(4) OVER (ORDER BY monetary ASC) AS m_score
    FROM rfm_base
),
rfm_segments AS (
    SELECT 
        monetary,
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
    FROM rfm_scores
)
SELECT 
    rfm_segment,
    COUNT(*) AS total_customers,
    ROUND(SUM(monetary), 2) AS total_segment_revenue,
    ROUND(AVG(monetary), 2) AS avg_revenue_per_customer,
    ROUND(
        (SUM(monetary) / (SELECT SUM(monetary) FROM rfm_segments)) * 100, 2
    ) AS pct_of_total_revenue
FROM rfm_segments
GROUP BY rfm_segment
ORDER BY total_segment_revenue DESC;