# 1. Delete the folder-version of README.md in both locations
rm -rf ~/Desktop/sql-sales-analytics/README.md
rm -rf sql-sales-analytics/README.md

# 2. Write a proper README.md file directly into both directories
cat << 'EOF' > sql-sales-analytics/README.md
# 📊 Sales Analytics Engine & Interactive Executive Dashboard

An end-to-end, high-performance sales analytics platform built on top of the **AdventureWorks** dataset. This project transitions from raw OLAP queries in **DuckDB** to a Python data-transformation pipeline, culminating in an interactive multi-page **Streamlit** dashboard for executive decision-making.

---

## 🛠️ Architecture & Tech Stack

* **Database / OLAP Engine:** DuckDB (In-process columnar SQL processing)
* **Data Processing & Pipelines:** Python 3.13, Pandas, NumPy, SQLAlchemy
* **Visualization & Dashboarding:** Streamlit, Plotly Express
* **Environment & Version Control:** macOS, VS Code, Git

---

## 📂 Project Structure

```text
sql-sales-analytics/
├── adventureworks.duckdb        # DuckDB embedded database engine
├── dashboard/
│   ├── app.py                   # Streamlit main entrypoint & Executive Overview
│   └── pages/
│       ├── 1_📈_Revenue_Analytics.py   # MoM / YoY time-series growth metrics
│       ├── 2_👥_Customer_360.py        # RFM segmentation & customer drill-downs
│       └── 3_📦_Product_Performance.py # Catalog margins & Pareto 80/20 analysis
├── data/
│   ├── db_connection.py         # DuckDB connection manager
│   ├── ingest_adventureworks.py # Data ingestion & schema creation script
│   └── transformations.py      # Pandas pipelines (RFM, Pareto metrics)
├── notebooks/
│   └── exploratory_analysis.ipynb # Interactive chart prototyping & EDA
├── requirements.txt             # Project dependencies
└── sql/                         # Modular SQL queries and analytical layers