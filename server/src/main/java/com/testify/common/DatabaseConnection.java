package com.testify.common;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Singleton manager for JDBC connections to the local MySQL instance.
 *
 * Optional settings (all have defaults), resolved through {@link EnvConfig} —
 * a real environment variable first, then the workspace's {@code .env} file:
 *   HSTS_DB_URL   — full JDBC URL (default: jdbc:mysql://localhost:3306/hsts_db)
 *   HSTS_DB_USER  — database username (default: root)
 *   HSTS_DB_PASS  — database password (default: 123)
 */
public class DatabaseConnection {

    private static DatabaseConnection instance;
    private static Connection connection;

    private final String url;
    private final String user;
    private final String password;

    private DatabaseConnection() throws SQLException {
        // Through EnvConfig, not System.getenv directly: the workspace's
        // .env already carried these three and was silently doing nothing.
        this.url      = EnvConfig.get("HSTS_DB_URL",  "jdbc:mysql://localhost:3306/hsts_db");
        this.user     = EnvConfig.get("HSTS_DB_USER", "root");
        this.password = EnvConfig.get("HSTS_DB_PASS", "123");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver not found on classpath", e);
        }

        this.connection = DriverManager.getConnection(url, user, password);
    }

    public static synchronized DatabaseConnection getInstance() throws SQLException {
        if (instance == null || instance.connection == null || instance.connection.isClosed()) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    public static Connection getConnection() {
        return connection;
    }

    /**
     * Opens a NEW connection that the caller owns, separate from the shared
     * singleton one.
     *
     * Every DAO otherwise works through a single process-wide
     * {@link Connection} shared by all client threads. That is workable for
     * autocommit statements, but a transaction must not run on it:
     * {@code setAutoCommit(false)} is visible to every other thread, so an
     * unrelated thread's statements would be swept into the transaction and
     * a rollback would discard work that was never part of it. Anything that
     * has to be atomic takes a private connection from here instead, and
     * closes it when done.
     *
     * @return a fresh connection the caller is responsible for closing
     * @throws SQLException if the connection cannot be opened
     */
    public static Connection openDedicatedConnection() throws SQLException {
        DatabaseConnection settings = getInstance();
        return DriverManager.getConnection(settings.url, settings.user, settings.password);
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
