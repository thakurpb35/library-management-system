package library.db;

import library.config.AppConfig;
import library.service.LibraryException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

/** Opens connections to MySQL, creates the database on first run and runs work in transactions. */
public final class Database {

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private final AppConfig config;

    public Database(AppConfig config) {
        this.config = config;
        DriverManager.setLoginTimeout(5);
    }

    public AppConfig config() {
        return config;
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl(config.dbName()), config.dbUser(), config.dbPassword());
    }

    /**
     * Makes sure the database and its tables exist. When the database is created
     * for the first time it is filled with sample data so the app is not empty.
     *
     * @return true if the database was newly created
     */
    public boolean initialize(LocalDate today) throws SQLException {
        String name = config.dbName();
        if (!name.matches("[A-Za-z0-9_]{1,64}")) {
            throw new SQLException("The database name may only contain letters, digits and underscores.");
        }
        boolean created;
        try (Connection server = DriverManager.getConnection(config.jdbcUrl(null), config.dbUser(), config.dbPassword())) {
            boolean exists;
            try (PreparedStatement ps = server.prepareStatement(
                    "SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }
            if (!exists) {
                try (Statement st = server.createStatement()) {
                    st.executeUpdate("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4");
                }
            }
            created = !exists;
        }
        try (Connection c = open()) {
            Schema.create(c);
            if (created) {
                c.setAutoCommit(false);
                try {
                    SampleData.load(c, today, config);
                    c.commit();
                } catch (SQLException | RuntimeException e) {
                    c.rollback();
                    throw e;
                }
            }
        }
        return created;
    }

    /** Deletes the whole database. Only used by the automated checks. */
    public void dropDatabase() throws SQLException {
        String name = config.dbName();
        if (!name.matches("[A-Za-z0-9_]{1,64}")) {
            throw new SQLException("Bad database name");
        }
        try (Connection server = DriverManager.getConnection(config.jdbcUrl(null), config.dbUser(), config.dbPassword());
             Statement st = server.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS `" + name + "`");
        }
    }

    /** Runs read-only work on its own connection. */
    public <T> T read(SqlWork<T> work) {
        try (Connection c = open()) {
            return work.run(c);
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    /**
     * Runs work as one transaction: everything is saved together, or nothing is.
     * READ COMMITTED means every query sees what other users have already saved.
     */
    public <T> T transaction(SqlWork<T> work) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                    // the original problem is more useful
                }
                throw e;
            }
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    /** Turns MySQL errors into messages a library clerk can understand. */
    static LibraryException translate(SQLException e) {
        String msg = String.valueOf(e.getMessage());
        String lower = msg.toLowerCase();
        switch (e.getErrorCode()) {
            case 1062: // duplicate value in a UNIQUE column
                if (lower.contains("email")) {
                    return new LibraryException("Another member already uses that email address.", e);
                }
                if (lower.contains("isbn")) {
                    return new LibraryException("Another book already has that ISBN.", e);
                }
                return new LibraryException("That value already exists.", e);
            case 1451: // cannot delete: a foreign key still points at the row
                return new LibraryException("This record is used by issue records, so it cannot be deleted.", e);
            case 1452:
                return new LibraryException("That book or member does not exist.", e);
            default:
                return new LibraryException("Database problem: " + msg, e);
        }
    }
}
