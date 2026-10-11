import os
import sys
import streamlit as st
import plotly.express as px

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
from data.db_connection import load_view

st.set_page_config(page_title="Revenue Analytics", page_icon="📈", layout="wide")

st.title("📈 Time-Series & Growth Analytics")
st.markdown("Analyze monthly performance, Month-over-Month (MoM) growth velocity, and Year-over-Year (YoY) metrics.")

df_monthly = load_view("vw_monthly_growth")
df_monthly["period"] = df_monthly["sales_year"].astype(str) + "-" + df_monthly["sales_month"].astype(str).str.zfill(2)

# Monthly Revenue Trajectory
st.subheader("Monthly Revenue Comparison")
fig_monthly = px.line(
    df_monthly,
    x="period",
    y="current_revenue",
    markers=True,
    labels={"period": "Year-Month", "current_revenue": "Revenue ($)"},
    template="plotly_white"
)
st.plotly_chart(fig_monthly, use_container_width=True)

# Growth Rate Multi-Axis
col_mom, col_yoy = st.columns(2)

with col_mom:
    st.subheader("Month-over-Month (MoM) Growth %")
    fig_mom = px.bar(
        df_monthly.dropna(subset=["mom_growth_pct"]),
        x="period",
        y="mom_growth_pct",
        color="mom_growth_pct",
        color_continuous_scale="RdYlGn",
        labels={"mom_growth_pct": "MoM Growth (%)", "period": "Period"},
        template="plotly_white"
    )
    st.plotly_chart(fig_mom, use_container_width=True)

with col_yoy:
    st.subheader("Year-over-Year (YoY) Growth %")
    fig_yoy = px.bar(
        df_monthly.dropna(subset=["yoy_growth_pct"]),
        x="period",
        y="yoy_growth_pct",
        color="yoy_growth_pct",
        color_continuous_scale="RdYlGn",
        labels={"yoy_growth_pct": "YoY Growth (%)", "period": "Period"},
        template="plotly_white"
    )
    st.plotly_chart(fig_yoy, use_container_width=True)