package com.team22.eventticketing.sales.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
public class EnumTypeInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    public EnumTypeInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String dbName = conn.getMetaData().getDatabaseProductName();
            if (!dbName.toLowerCase().contains("postgresql")) return;

            try (Statement stmt = conn.createStatement()) {

                // Create enum types if they don't exist
                stmt.execute("""
                    DO $$ BEGIN
                        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_method') THEN
                            CREATE TYPE payment_method AS ENUM ('CREDIT_CARD', 'DEBIT_CARD', 'WALLET');
                        END IF;
                    END $$
                    """);

                stmt.execute("""
                    DO $$ BEGIN
                        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'sale_status') THEN
                            CREATE TYPE sale_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED');
                        END IF;
                    END $$
                    """);

                stmt.execute("""
                    DO $$ BEGIN
                        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'discount_type') THEN
                            CREATE TYPE discount_type AS ENUM ('PERCENTAGE', 'FIXED');
                        END IF;
                    END $$
                    """);

                // Drop Hibernate check constraints if they exist
                stmt.execute("ALTER TABLE IF EXISTS ticket_sales DROP CONSTRAINT IF EXISTS ticket_sales_method_check");
                stmt.execute("ALTER TABLE IF EXISTS ticket_sales DROP CONSTRAINT IF EXISTS ticket_sales_status_check");
                stmt.execute("ALTER TABLE IF EXISTS promotions DROP CONSTRAINT IF EXISTS promotions_discount_type_check");

                // Alter columns to native enum type if still varchar
                stmt.execute("""
                    DO $$ BEGIN
                        IF EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_name='ticket_sales' AND column_name='method' AND data_type='character varying'
                        ) THEN
                            ALTER TABLE ticket_sales ALTER COLUMN method TYPE payment_method USING method::payment_method;
                        END IF;
                    END $$
                    """);

                stmt.execute("""
                    DO $$ BEGIN
                        IF EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_name='ticket_sales' AND column_name='status' AND data_type='character varying'
                        ) THEN
                            ALTER TABLE ticket_sales ALTER COLUMN status TYPE sale_status USING status::sale_status;
                        END IF;
                    END $$
                    """);

                stmt.execute("""
                    DO $$ BEGIN
                        IF EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_name='promotions' AND column_name='discount_type' AND data_type='character varying'
                        ) THEN
                            ALTER TABLE promotions ALTER COLUMN discount_type TYPE discount_type USING discount_type::discount_type;
                        END IF;
                    END $$
                    """);

                // Add implicit casts so Hibernate string inserts keep working
                stmt.execute("""
                    DO $$ BEGIN
                        IF NOT EXISTS (
                            SELECT 1 FROM pg_cast c
                            JOIN pg_type s ON s.oid = c.castsource
                            JOIN pg_type t ON t.oid = c.casttarget
                            WHERE s.typname = 'varchar' AND t.typname = 'payment_method'
                        ) THEN
                            CREATE CAST (varchar AS payment_method) WITH INOUT AS IMPLICIT;
                        END IF;
                    END $$
                    """);

                stmt.execute("""
                    DO $$ BEGIN
                        IF NOT EXISTS (
                            SELECT 1 FROM pg_cast c
                            JOIN pg_type s ON s.oid = c.castsource
                            JOIN pg_type t ON t.oid = c.casttarget
                            WHERE s.typname = 'varchar' AND t.typname = 'sale_status'
                        ) THEN
                            CREATE CAST (varchar AS sale_status) WITH INOUT AS IMPLICIT;
                        END IF;
                    END $$
                    """);

                stmt.execute("""
                    DO $$ BEGIN
                        IF NOT EXISTS (
                            SELECT 1 FROM pg_cast c
                            JOIN pg_type s ON s.oid = c.castsource
                            JOIN pg_type t ON t.oid = c.casttarget
                            WHERE s.typname = 'varchar' AND t.typname = 'discount_type'
                        ) THEN
                            CREATE CAST (varchar AS discount_type) WITH INOUT AS IMPLICIT;
                        END IF;
                    END $$
                    """);
            }
        }
    }
}