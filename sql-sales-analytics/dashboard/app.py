import os
import sys
import streamlit as st
import plotly.express as px

# Anchor project root to import database pipeline modules
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from data.db_connection import load_view, query_to_dataframe

# Streamlit Page Config
st.set_page_config(
    page_title="Executive Sales Analytics | AdventureWorks",
    page_icon="📊",
    layout="wide",
    initial_sidebar_state="expanded"
)

st.title("📊 Executive Sales Performance Overview")
st.markdown("Real-time revenue metrics, regional distribution, and high-level KPIs powered by DuckDB.")

# -----------------------------------------------------------------------------
# Global Sidebar Filters
# -----------------------------------------------------------------------------
st.sidebar.header("Global Filters")

# Fetch unique regions for the filter dropdown
df_kpis_raw = load_view("vw_daily_kpis")
available_regions = ["All"] + sorted(df_kpis_raw["territory_region"].dropna().unique().tolist())
selected_region = st.sidebar.selectbox("Select Territory Region", available_regions)

# Date Filter
min_date = df_kpis_raw["order_date"].min().date()
max_date = df_kpis_raw["order_date"].max().date()
selected_date_range = st.sidebar.date_input(
    "Select Date Range",
    value=(min_date, max_date),
    min_value=min_date,
    max_value=max_date
)

# Filter Dataset based on sidebar inputs
df_kpis = df_kpis_raw.copy()
if selected_region != "All":
    df_kpis = df_kpis[df_kpis["territory_region"] == selected_region]

if len(selected_date_range) == 2:
    start_date, end_date = selected_date_range
    df_kpis = df_kpis[
        (df_kpis["order_date"].dt.date >= start_date) & 
        (df_kpis["order_date"].dt.date <= end_date)
    ]

# -----------------------------------------------------------------------------
# Executive KPI Cards
# -----------------------------------------------------------------------------
total_revenue = df_kpis["total_revenue"].sum()
total_profit = df_kpis["total_profit"].sum()
total_orders = df_kpis["total_orders"].sum()
overall_margin = (total_profit / total_revenue * 100) if total_revenue > 0 else 0

col1, col2, col3, col4 = st.columns(4)
col1.metric("Total Revenue", f"${total_revenue:,.2f}")
col2.metric("Total Profit", f"${total_profit:,.2f}")
col3.metric("Total Orders", f"{int(total_orders):,}")
col4.metric("Profit Margin", f"{overall_margin:.2f}%")

st.markdown("---")

# -----------------------------------------------------------------------------
# Visualizations
# -----------------------------------------------------------------------------
col_left, col_right = st.columns([6, 4])

with col_left:
    st.subheader("Daily Revenue Trajectory")
    df_daily = df_kpis.groupby("order_date")["total_revenue"].sum().reset_index()
    fig_daily = px.line(
        df_daily, 
        x="order_date", 
        y="total_revenue",
        labels={"order_date": "Date", "total_revenue": "Revenue ($)"},
        template="plotly_white"
    )
    fig_daily.update_traces(line_color="#1f77b4")
    st.plotly_chart(fig_daily, use_container_width=True)

with col_right:
    st.subheader("Revenue by Territory")
    df_region = df_kpis.groupby("territory_region")["total_revenue"].sum().reset_index().sort_values("total_revenue", ascending=True)
    fig_region = px.bar(
        df_region,
        x="total_revenue",
        y="territory_region",
        orientation="h",
        labels={"total_revenue": "Revenue ($)", "territory_region": "Region"},
        color="total_revenue",
        color_continuous_scale="Viridis",
        template="plotly_white"
    )
    fig_region.update_layout(showlegend=False)
    st.plotly_chart(fig_region, use_container_width=True)