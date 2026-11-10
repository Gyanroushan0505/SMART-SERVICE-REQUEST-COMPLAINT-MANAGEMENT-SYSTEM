package com.smartcomplaint.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DB {
    private DB() {}

    public static Connection get() throws SQLException {
        // Local development defaults are safe; production values must be
        // supplied through the hosting provider's environment variables.
        String url = System.getenv().getOrDefault(
            "DB_URL",
            "jdbc:mysql://localhost:3306/smart_complaint?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        );
        String user = System.getenv().getOrDefault("DB_USER", "root");
        String pass = System.getenv().getOrDefault("DB_PASSWORD", "");

        if (pass.isEmpty()) {
            throw new SQLException(
                "DB_PASSWORD is not configured. Set DB_URL, DB_USER and DB_PASSWORD in the environment."
            );
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver not found. Check WEB-INF/lib.", e);
        }

        return DriverManager.getConnection(url, user, pass);
    }
}
