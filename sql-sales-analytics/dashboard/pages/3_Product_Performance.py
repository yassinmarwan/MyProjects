import os
import sys
import streamlit as st
import plotly.express as px

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
from data.db_connection import load_view
from data.transformations import calculate_pareto_product_impact

st.set_page_config(page_title="Product Catalog Performance", page_icon="📦", layout="wide")

st.title("📦 Product Catalog Analytics")
st.markdown("Evaluate product profitability, margin distributions, and catalog revenue concentration.")

df_products = load_view("vw_product_performance")

# Category Selector Filter
categories = ["All"] + sorted(df_products["CategoryName"].dropna().unique().tolist())
selected_category = st.selectbox("Select Product Category", categories)

if selected_category != "All":
    df_products = df_products[df_products["CategoryName"] == selected_category]

col1, col2 = st.columns([6, 4])

with col1:
    st.subheader("Top 10 Products by Revenue")
    df_top_products = df_products.sort_values("total_revenue", ascending=False).head(10)
    fig_top = px.bar(
        df_top_products,
        x="total_revenue",
        y="ProductName",
        orientation="h",
        color="unit_margin",
        labels={"total_revenue": "Total Revenue ($)", "ProductName": "Product"},
        color_continuous_scale="Blues",
        template="plotly_white"
    )
    fig_top.update_layout(yaxis={"categoryorder": "total ascending"})
    st.plotly_chart(fig_top, use_container_width=True)

with col2:
    st.subheader("Pareto Revenue Analysis (80/20)")
    df_pareto = calculate_pareto_product_impact(df_products)
    fig_pareto = px.scatter(
        df_pareto,
        x=list(range(len(df_pareto))),
        y="cum_revenue_pct",
        color="pareto_class",
        labels={"x": "Product Index", "cum_revenue_pct": "Cumulative Revenue %"},
        template="plotly_white"
    )
    st.plotly_chart(fig_pareto, use_container_width=True)