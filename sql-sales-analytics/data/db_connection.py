import os
import duckdb
import pandas as pd
from contextlib import contextmanager

# Absolute path resolution targeting project root's adventureworks.duckdb
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_PATH = os.path.join(BASE_DIR, "adventureworks.duckdb")
VIEWS_SQL_PATH = os.path.join(BASE_DIR, "sql", "08_views.sql")

@contextmanager
def get_db_connection():
    """Context manager for managing DuckDB database connections safely."""
    conn = duckdb.connect(DB_PATH)
    
    # Check if views exist; if not, auto-execute 08_views.sql
    try:
        views_check = conn.execute(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'vw_monthly_growth';"
        ).fetchone()[0]
        
        if views_check == 0 and os.path.exists(VIEWS_SQL_PATH):
            with open(VIEWS_SQL_PATH, "r") as f:
                conn.execute(f.read())
    except Exception as e:
        print(f"Warning during view initialization: {e}")
        
    try:
        yield conn
    finally:
        conn.close()

def query_to_dataframe(sql_query: str, params: tuple = None) -> pd.DataFrame:
    """Execute a SQL query against DuckDB and return a Pandas DataFrame."""
    with get_db_connection() as conn:
        if params:
            return conn.execute(sql_query, params).df()
        return conn.execute(sql_query).df()

def load_view(view_name: str) -> pd.DataFrame:
    """Helper function to load an entire reporting view into a DataFrame."""
    query = f"SELECT * FROM {view_name};"
    return query_to_dataframe(query)