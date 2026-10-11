import duckdb
import pandas as pd

# 1. Connect to DuckDB database file
con = duckdb.connect("adventureworks.duckdb")

BASE_URL = "https://raw.githubusercontent.com/PMinThant/AdventureWorks-datasets/main"

# Dimension Tables Mapping
dims = {
    "Customer": "AdventureWorks_Customers.csv",
    "Product": "AdventureWorks_Products.csv",
    "ProductCategory": "AdventureWorks_Product_Categories.csv",
    "ProductSubcategory": "AdventureWorks_Product_Subcategories.csv",
    "SalesTerritory": "AdventureWorks_Territories.csv"
}

print("Ingesting Dimension Tables...\n")

for table_name, csv_file in dims.items():
    url = f"{BASE_URL}/{csv_file}"
    try:
        # Read CSV with pandas using latin1 encoding to safely bypass character errors
        df = pd.read_csv(url, encoding="latin1")
        con.execute(f"CREATE OR REPLACE TABLE {table_name} AS SELECT * FROM df")
        count = con.execute(f"SELECT COUNT(*) FROM {table_name}").fetchone()[0]
        print(f"  Loaded '{table_name}' ({count:,} rows)")
    except Exception as e:
        print(f"  Error loading {table_name}: {e}")

print("\nCombining and Ingesting Sales Data (2015, 2016, 2017)...")
sales_files = [
    "AdventureWorks_Sales_2015.csv",
    "AdventureWorks_Sales_2016.csv",
    "AdventureWorks_Sales_2017.csv"
]

try:
    # Load and combine all sales years into a single DataFrame
    sales_dfs = [pd.read_csv(f"{BASE_URL}/{sf}", encoding="latin1") for sf in sales_files]
    combined_sales_df = pd.concat(sales_dfs, ignore_index=True)
    
    con.execute("CREATE OR REPLACE TABLE SalesOrderDetail AS SELECT * FROM combined_sales_df")
    sales_count = con.execute("SELECT COUNT(*) FROM SalesOrderDetail").fetchone()[0]
    print(f"  Loaded 'SalesOrderDetail' ({sales_count:,} combined rows)")
except Exception as e:
    print(f"  Error loading SalesOrderDetail: {e}")

print("\n--- Verified Tables in DuckDB ---")
print(con.execute("SHOW TABLES;").df())

con.close()
print("\nIngestion complete! Database is ready.")