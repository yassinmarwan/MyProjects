# # import duckdb

# # con = duckdb.connect("adventureworks.duckdb")
# # print(con.execute("SHOW TABLES;").df())

# # # Read SQL query file
# # with open("sql/02_kpi_queries.sql", "r") as f:
# #     sql_script = f.read()

# # # Split individual SQL queries by semicolon and execute
# # queries = [q.strip() for q in sql_script.split(";") if q.strip()]

# # titles = [
# #     "1. Executive Summary KPIs",
# #     "2. Revenue by Product Category",
# #     "3. Monthly Revenue Trend",
# #     "4. Top Sales Territories"
# # ]

# # for title, query in zip(titles, queries):
# #     print(f"\n--- {title} ---")
# #     df = con.execute(query).df()
# #     print(df)

# # con.close()


# # # import duckdb

# # # con = duckdb.connect("adventureworks.duckdb")

# # # with open("sql/01_schema_exploration.sql", "r") as f:
# # #     sql_script = f.read()

# # # # Execute non-empty statements
# # # for statement in sql_script.split(";"):
# # #     stmt = statement.strip()
# # #     if stmt:
# # #         print(f"\n>>> Executing:\n{stmt[:60]}...")
# # #         res = con.execute(stmt).df()
# # #         print(res)

# # # con.close()



# # import duckdb

# # def run_sql_file(connection, file_path):
# #     print(f"\n==================================================")
# #     print(f" Executing: {file_path}")
# #     print(f"==================================================")
    
# #     with open(file_path, "r") as f:
# #         sql_script = f.read()

# #     # Split by semicolon to execute queries individually
# #     queries = [q.strip() for q in sql_script.split(";") if q.strip()]

# #     for idx, query in enumerate(queries, 1):
# #         print(f"\n--- Query #{idx} ---")
# #         try:
# #             df = connection.execute(query).df()
# #             print(df)
# #         except Exception as e:
# #             print(f"Error executing query #{idx}: {e}")

# # def main():
# #     con = duckdb.connect("adventureworks.duckdb")
    
# #     # Test Customer Analysis
# #     run_sql_file(con, "sql/03_customer_analysis.sql")
    
# #     # Test Product Analysis
# #     run_sql_file(con, "sql/04_product_analysis.sql")
    
# #     con.close()
# #     print("\nAll tests completed successfully!")

# # if __name__ == "__main__":
# #     main()



# import duckdb

# def run_sql_file(connection, file_path):
#     print(f"\n==================================================")
#     print(f" Executing: {file_path}")
#     print(f"==================================================")
    
#     with open(file_path, "r") as f:
#         sql_script = f.read()

#     queries = [q.strip() for q in sql_script.split(";") if q.strip()]

#     for idx, query in enumerate(queries, 1):
#         print(f"\n--- Query #{idx} ---")
#         try:
#             df = connection.execute(query).df()
#             print(df.head(15))  # Display top 15 rows for readability
#         except Exception as e:
#             print(f"Error executing query #{idx}: {e}")

# def main():
#     con = duckdb.connect("adventureworks.duckdb")
#     run_sql_file(con, "sql/07_ctes.sql")
#     con.close()

# if __name__ == "__main__":
#     main()



import duckdb

def run_sql_file(connection, file_path):
    print(f"\n==================================================")
    print(f" Executing: {file_path}")
    print(f"==================================================")
    
    with open(file_path, "r") as f:
        sql_script = f.read()

    queries = [q.strip() for q in sql_script.split(";") if q.strip()]

    for idx, query in enumerate(queries, 1):
        print(f"\n--- Query #{idx} ---")
        try:
            df = connection.execute(query).df()
            print(df.head(10))
        except Exception as e:
            print(f"Error executing query #{idx}: {e}")

def main():
    con = duckdb.connect("adventureworks.duckdb")
    run_sql_file(con, "sql/08_views.sql")
    con.close()

if __name__ == "__main__":
    main()