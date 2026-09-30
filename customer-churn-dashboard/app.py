

import streamlit as st
from utils import preprocessing
import joblib
import pandas as pd
import plotly.express as px


# -----------------------------
# Page Configuration
# -----------------------------
st.set_page_config(
    page_title="Customer Churn Dashboard",
    page_icon="📊",
    layout="wide"
)


# -----------------------------
# Load Data
# -----------------------------
df = pd.read_csv("data/raw_customer_data.csv")


# -----------------------------
# Load Models
# -----------------------------
xgb_model = joblib.load("models/xgb_model.pkl")
rf_model = joblib.load("models/rf_model.pkl")
logistic_model = joblib.load("models/logistic_model.pkl")


# -----------------------------
# Original data for display
# -----------------------------
dashboard_df = df.copy()


# -----------------------------
# Processed data for model
# -----------------------------
X, Y = preprocessing(df)


# -----------------------------
# Logistic Regression Prediction
# -----------------------------
dashboard_df["xgboost Prediction"] = xgb_model.predict(X)

dashboard_df["Churn Probability"] = (
    xgb_model.predict_proba(X)[:, 1]
)

dashboard_df["Risk"] = dashboard_df["Churn Probability"].apply(
    lambda x: "High Risk" if x >= 0.7 else "Low Risk"
)


# =========================================================
# TITLE
# =========================================================

st.title("📊 Customer Churn Dashboard")

st.write(
    "An interactive dashboard for analyzing customer churn "
    "using machine learning."
)

st.divider()


# =========================================================
# KPI CALCULATIONS
# =========================================================

# Actual churn rate from the original dataset
churn_rate = (
    dashboard_df["Churn"]
    .value_counts(normalize=True)
    .get("Yes", 0)
    * 100
)


# Number of high-risk customers
high_risk_customers = (
    dashboard_df["Risk"] == "High Risk"
).sum()


# Average predicted churn probability
average_churn_probability = (
    dashboard_df["Churn Probability"].mean() * 100
)


# Top churn driver from XGBoost
feature_importance = pd.DataFrame({
    "Feature": X.columns,
    "Importance": xgb_model.feature_importances_
})

feature_importance = feature_importance.sort_values(
    by="Importance",
    ascending=False
)

top_churn_driver = feature_importance.iloc[0]["Feature"]


# =========================================================
# KPI CARDS
# =========================================================

col1, col2, col3, col4 = st.columns(4)


with col1:
    st.metric(
        label="Churn Rate",
        value=f"{churn_rate:.1f}%"
    )


with col2:
    st.metric(
        label="High-Risk Customers",
        value=f"{high_risk_customers:,}"
    )


with col3:
    st.metric(
        label="Average Churn Probability",
        value=f"{average_churn_probability:.1f}%"
    )


with col4:
    st.metric(
        label="Top Churn Driver",
        value=top_churn_driver
    )


st.divider()


# =========================================================
# CHART 1 — CHURN DISTRIBUTION
# =========================================================

churn_counts = (
    dashboard_df["Churn"]
    .value_counts()
    .reset_index()
)

churn_counts.columns = ["Churn", "Customers"]


fig_churn = px.bar(
    churn_counts,
    x="Churn",
    y="Customers",
    title="Customer Churn Distribution",
    text="Customers"
)

fig_churn.update_layout(
    xaxis_title="Churn",
    yaxis_title="Number of Customers"
)

fig_churn.update_traces(
    textposition="outside"
)


# =========================================================
# CHART 2 — CHURN BY CONTRACT
# =========================================================

contract_churn = (
    dashboard_df.groupby("Contract")["Churn"]
    .apply(lambda x: (x == "Yes").mean() * 100)
    .reset_index(name="Churn Rate")
)


fig_contract = px.bar(
    contract_churn,
    x="Contract",
    y="Churn Rate",
    title="Churn Rate by Contract",
    text="Churn Rate"
)

fig_contract.update_layout(
    xaxis_title="Contract Type",
    yaxis_title="Churn Rate (%)"
)

fig_contract.update_traces(
    texttemplate="%{text:.1f}%",
    textposition="outside"
)


# Display charts side by side
col1, col2 = st.columns(2)


with col1:
    st.plotly_chart(
        fig_churn,
        use_container_width=True
    )


with col2:
    st.plotly_chart(
        fig_contract,
        use_container_width=True
    )


st.divider()


# =========================================================
# CHART 3 — CHURN BY TENURE
# =========================================================

dashboard_df["Tenure Group"] = pd.cut(
    dashboard_df["tenure"],
    bins=[0, 12, 24, 36, 48, 60, 72],
    labels=[
        "0–12",
        "12–24",
        "24–36",
        "36–48",
        "48–60",
        "60–72"
    ],
    include_lowest=True
)


tenure_churn = (
    dashboard_df.groupby(
        "Tenure Group",
        observed=False
    )["Churn"]
    .apply(lambda x: (x == "Yes").mean() * 100)
    .reset_index(name="Churn Rate")
)


fig_tenure = px.bar(
    tenure_churn,
    x="Tenure Group",
    y="Churn Rate",
    title="Churn Rate by Tenure",
    text="Churn Rate"
)

fig_tenure.update_layout(
    xaxis_title="Tenure (Months)",
    yaxis_title="Churn Rate (%)"
)

fig_tenure.update_traces(
    texttemplate="%{text:.1f}%",
    textposition="outside"
)


st.plotly_chart(
    fig_tenure,
    use_container_width=True
)


st.divider()


# =========================================================
# CHART 4 — TOP 10 CHURN DRIVERS
# =========================================================

top_features = (
    feature_importance
    .head(10)
    .sort_values(by="Importance")
)


fig_importance = px.bar(
    top_features,
    x="Importance",
    y="Feature",
    orientation="h",
    title="Top 10 Churn Drivers",
    text="Importance"
)

fig_importance.update_layout(
    xaxis_title="Feature Importance",
    yaxis_title="Feature"
)

fig_importance.update_traces(
    texttemplate="%{text:.3f}",
    textposition="outside"
)


st.plotly_chart(
    fig_importance,
    use_container_width=True
)

# =========================================================
# BUSINESS INSIGHTS
# =========================================================

st.header("📈 Business Insights")

# -----------------------------
# Churn by Internet Service
# -----------------------------
internet_churn = (
    dashboard_df.groupby("InternetService")["Churn"]
    .apply(lambda x: (x == "Yes").mean() * 100)
    .reset_index(name="Churn Rate")
)

fig_internet = px.bar(
    internet_churn,
    x="InternetService",
    y="Churn Rate",
    text="Churn Rate",
    title="Churn Rate by Internet Service"
)

fig_internet.update_traces(
    texttemplate="%{text:.1f}%",
    textposition="outside"
)

# -----------------------------
# Churn by Payment Method
# -----------------------------
payment_churn = (
    dashboard_df.groupby("PaymentMethod")["Churn"]
    .apply(lambda x: (x == "Yes").mean() * 100)
    .reset_index(name="Churn Rate")
)

fig_payment = px.bar(
    payment_churn,
    x="PaymentMethod",
    y="Churn Rate",
    text="Churn Rate",
    title="Churn Rate by Payment Method"
)

fig_payment.update_traces(
    texttemplate="%{text:.1f}%",
    textposition="outside"
)

col1, col2 = st.columns(2)

with col1:
    st.plotly_chart(fig_internet, use_container_width=True)

with col2:
    st.plotly_chart(fig_payment, use_container_width=True)


st.header("⚠️ Customer Risk Prediction")

def classify_risk(prob):

    if prob >= 0.70:
        return "High"

    elif prob >= 0.40:
        return "Medium"

    else:
        return "Low"



dashboard_df["Risk"] = dashboard_df["Churn Probability"].apply(classify_risk)    

st.sidebar.header("Filters")

risk_filter = st.sidebar.multiselect(
    "Risk Level",
    options=["Low", "Medium", "High"],
    default=["Low", "Medium", "High"]
)

contract_filter = st.sidebar.multiselect(
    "Contract",
    options=sorted(dashboard_df["Contract"].unique()),
    default=sorted(dashboard_df["Contract"].unique())
)

internet_filter = st.sidebar.multiselect(
    "Internet Service",
    options=sorted(dashboard_df["InternetService"].unique()),
    default=sorted(dashboard_df["InternetService"].unique())
)

tenure_filter = st.sidebar.multiselect(
    "Tenure Group",
    options=dashboard_df["Tenure Group"].astype(str).unique(),
    default=dashboard_df["Tenure Group"].astype(str).unique()
)


filtered_df = dashboard_df[
    (dashboard_df["Risk"].isin(risk_filter))
    &
    (dashboard_df["Contract"].isin(contract_filter))
    &
    (dashboard_df["InternetService"].isin(internet_filter))
    &
    (dashboard_df["Tenure Group"].astype(str).isin(tenure_filter))
]

st.subheader("Customer Risk Table")

st.dataframe(
    filtered_df[
        [
            "customerID",
            "Churn Probability",
            "Risk",
            "Contract",
            "InternetService",
            "tenure",
            "MonthlyCharges",
        ]
    ],
    use_container_width=True
)

filtered_df = filtered_df.sort_values(
    "Churn Probability",
    ascending=False
)

# =========================================================
# INFO
# =========================================================

st.info(
    "This overview combines customer-level churn data, "
    "logistic regression risk predictions, and XGBoost "
    "feature importance."
)