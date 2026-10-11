package com.team22.eventticketing.user.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Component
public class SchemaRecoveryFilter implements Filter {

    private final DataSource dataSource;

    public SchemaRecoveryFilter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        ensureSchema();
        chain.doFilter(request, response);
    }

    private void ensureSchema() {
        try (Connection conn = dataSource.getConnection()) {
            String db = conn.getMetaData().getDatabaseProductName();
            if (!db.toLowerCase().contains("postgresql")) return;

            ResultSet rs = conn.getMetaData().getTables(null, "public", "users", null);
            if (!rs.next()) {
                initSchema(conn);
            }
        } catch (Exception ignored) {
        }
    }

    private void initSchema(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            // Create enum types
            stmt.execute(
                "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role') " +
                "THEN CREATE TYPE user_role AS ENUM ('ATTENDEE', 'ADMIN'); END IF; END $$"
            );
            stmt.execute(
                "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_status') " +
                "THEN CREATE TYPE user_status AS ENUM ('ACTIVE', 'DEACTIVATED'); END IF; END $$"
            );
            // Create implicit casts so Hibernate varchar params work with enum columns
            stmt.execute(
                "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_cast c " +
                "JOIN pg_type s ON s.oid=c.castsource JOIN pg_type t ON t.oid=c.casttarget " +
                "WHERE s.typname='varchar' AND t.typname='user_role') " +
                "THEN CREATE CAST (varchar AS user_role) WITH INOUT AS IMPLICIT; END IF; END $$"
            );
            stmt.execute(
                "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_cast c " +
                "JOIN pg_type s ON s.oid=c.castsource JOIN pg_type t ON t.oid=c.casttarget " +
                "WHERE s.typname='varchar' AND t.typname='user_status') " +
                "THEN CREATE CAST (varchar AS user_status) WITH INOUT AS IMPLICIT; END IF; END $$"
            );
            // Create tables with native enum columns
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    password VARCHAR(255) NOT NULL,
                    phone VARCHAR(255) NOT NULL UNIQUE,
                    role user_role NOT NULL,
                    status user_status NOT NULL DEFAULT 'ACTIVE',
                    preferences JSONB,
                    created_at TIMESTAMP NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS favorite_venues (
                    id BIGSERIAL PRIMARY KEY,
                    label VARCHAR(255) NOT NULL,
                    venue_name VARCHAR(255) NOT NULL,
                    location VARCHAR(255) NOT NULL,
                    capacity INT,
                    is_default BOOLEAN DEFAULT false,
                    metadata JSONB,
                    created_at TIMESTAMP NOT NULL,
                    user_id BIGINT NOT NULL REFERENCES users(id)
                )
                """);
        }
    }
}
