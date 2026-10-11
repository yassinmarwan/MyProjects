import pandas as pd
import numpy as np

def prepare_time_series_features(df: pd.DataFrame, date_column: str = 'order_date') -> pd.DataFrame:
    """Prepares time-series DataFrames by ensuring datetime types and extracting period attributes."""
    df = df.copy()
    df[date_column] = pd.to_datetime(df[date_column])
    df['year_month'] = df[date_column].dt.to_period('M').astype(str)
    df['day_of_week'] = df[date_column].dt.day_name()
    return df

def categorize_rfm_segments(df_rfm: pd.DataFrame) -> pd.DataFrame:
    """Enriches customer 360 data with strategic business tier tags and monetary share."""
    df = df_rfm.copy()
    
    # Identify whether we are operating on raw customer data or pre-aggregated segment summaries
    revenue_col = 'total_revenue' if 'total_revenue' in df.columns else 'monetary_value'
    
    if revenue_col in df.columns:
        total_monetary = df[revenue_col].sum()
        df['revenue_contribution_pct'] = (df[revenue_col] / total_monetary) * 100

    # Define standardized segment display order
    segment_order = [
        'Champions', 'Loyal Customers', 'High-Value Recent', 
        'New / Promising', 'At-Risk Loyalists', 'Cant Lose Them', 
        'Hibernating / Lost', 'Needs Attention'
    ]
    df['rfm_segment'] = pd.Categorical(df['rfm_segment'], categories=segment_order, ordered=True)
    return df.sort_values('rfm_segment')

def calculate_pareto_product_impact(df_products: pd.DataFrame) -> pd.DataFrame:
    """Calculates cumulative revenue percentage to identify top-tier 80/20 Pareto products."""
    df = df_products.copy().sort_values('total_revenue', ascending=False)
    total_revenue = df['total_revenue'].sum()
    df['cum_revenue'] = df['total_revenue'].cumsum()
    df['cum_revenue_pct'] = (df['cum_revenue'] / total_revenue) * 100
    df['pareto_class'] = np.where(df['cum_revenue_pct'] <= 80, 'Top 80% (Core)', 'Bottom 20% (Tail)')
    return df