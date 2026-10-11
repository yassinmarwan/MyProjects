import os
import sys
import streamlit as st
import plotly.express as px

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
from data.db_connection import load_view
from data.transformations import categorize_rfm_segments

st.set_page_config(page_title="Customer 360 & RFM", page_icon="👥", layout="wide")

st.title("👥 Customer 360 & RFM Segmentation")
st.markdown("Explore customer lifetime value, purchasing habits, and RFM behavioral clusters.")

df_customers = load_view("vw_customer_360")

# Segment Distribution Chart
df_rfm_summary = df_customers.groupby("rfm_segment", observed=False).agg(
    customer_count=("CustomerKey", "count"),
    total_revenue=("monetary_value", "sum"),
    avg_revenue=("monetary_value", "mean")
).reset_index()

df_rfm_summary = categorize_rfm_segments(df_rfm_summary)

st.subheader("RFM Segment Revenue Contribution")
fig_rfm = px.bar(
    df_rfm_summary,
    x="rfm_segment",
    y="total_revenue",
    color="avg_revenue",
    labels={
        "rfm_segment": "Segment",
        "total_revenue": "Total Revenue ($)",
        "avg_revenue": "Avg Spend / Customer ($)"
    },
    color_continuous_scale="Viridis",
    template="plotly_white"
)
st.plotly_chart(fig_rfm, use_container_width=True)

st.markdown("---")

# Customer Table Search & Drill-Down
st.subheader("Customer Directory Drill-Down")
selected_segment = st.selectbox("Filter by RFM Segment", ["All"] + df_rfm_summary["rfm_segment"].tolist())

df_display = df_customers.copy()
if selected_segment != "All":
    df_display = df_display[df_display["rfm_segment"] == selected_segment]

st.dataframe(
    df_display[[
        "CustomerKey", "customer_name", "EmailAddress", 
        "rfm_segment", "total_orders", "monetary_value", "recency_days"
    ]].sort_values("monetary_value", ascending=False),
    use_container_width=True
)