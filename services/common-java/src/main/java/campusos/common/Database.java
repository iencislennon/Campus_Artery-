package campusos.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQLite access for one service's private database file. Each call gets its own short-lived connection. */
public final class Database {
    @FunctionalInterface public interface RowMapper<T> { T map(ResultSet row) throws SQLException; }
    @FunctionalInterface public interface Work<T> { T run(Tx tx) throws SQLException; }

    private final String url;

    public Database(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot create data directory for " + file, e);
        }
        this.url = "jdbc:sqlite:" + file.toAbsolutePath();
    }

    public void executeScript(String script) {
        transaction(tx -> {
            for (String statement : script.split(";")) {
                if (!statement.isBlank()) tx.update(statement);
            }
            return null;
        });
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        return transaction(tx -> tx.query(sql, mapper, params));
    }

    public int update(String sql, Object... params) {
        return transaction(tx -> tx.update(sql, params));
    }

    public <T> T transaction(Work<T> work) {
        try (Connection connection = DriverManager.getConnection(url)) {
            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON");
                pragma.execute("PRAGMA busy_timeout = 5000");
            }
            connection.setAutoCommit(false);
            try {
                T result = work.run(new Tx(connection));
                connection.commit();
                return result;
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint")) {
                throw ApiException.conflict("That record already exists.");
            }
            throw new IllegalStateException("Database error: " + e.getMessage(), e);
        }
    }

    public static final class Tx {
        private final Connection connection;

        private Tx(Connection connection) { this.connection = connection; }

        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) throws SQLException {
            try (PreparedStatement statement = prepare(sql, params); ResultSet rows = statement.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rows.next()) result.add(mapper.map(rows));
                return result;
            }
        }

        public int update(String sql, Object... params) throws SQLException {
            try (PreparedStatement statement = prepare(sql, params)) {
                return statement.executeUpdate();
            }
        }

        private PreparedStatement prepare(String sql, Object[] params) throws SQLException {
            PreparedStatement statement = connection.prepareStatement(sql);
            for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
            return statement;
        }
    }
}
