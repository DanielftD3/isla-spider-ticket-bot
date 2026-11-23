package com.islaspider.bot.db;

import com.islaspider.bot.config.BotConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class Database implements AutoCloseable {
    private static final DateTimeFormatter SQLITE_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSSSSSSSS");
    private final HikariDataSource dataSource;
    private final boolean sqlite;

    public Database(BotConfig config) {
        sqlite = config.databaseUrl().startsWith("jdbc:sqlite:");
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.databaseUrl());
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(30_000);
        hikari.setValidationTimeout(5_000);
        if (sqlite) {
            createParentDirectory(config.databaseUrl());
            hikari.setDriverClassName("org.sqlite.JDBC");
            // A single connection serializes writes; SQLite is for one bot process.
            hikari.setMaximumPoolSize(1);
            hikari.setPoolName("ark-dinosauria-sqlite");
            hikari.addDataSourceProperty("foreign_keys", "true");
            hikari.addDataSourceProperty("busy_timeout", "10000");
            hikari.addDataSourceProperty("journal_mode", "WAL");
            hikari.addDataSourceProperty("synchronous", "FULL");
        } else {
            hikari.setDriverClassName("org.postgresql.Driver");
            hikari.setUsername(config.databaseUser());
            hikari.setPassword(config.databasePassword());
            hikari.setMaximumPoolSize(config.databasePoolSize());
            hikari.setPoolName("isla-spider-postgres");
            hikari.addDataSourceProperty("ApplicationName", "isla-spider-ticket-bot");
        }
        this.dataSource = new HikariDataSource(hikari);
    }

    private static void createParentDirectory(String url) {
        String location = url.substring("jdbc:sqlite:".length());
        if (location.equals(":memory:") || location.startsWith("file:")) return;
        try {
            Path parent = Path.of(location).toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo crear el directorio de SQLite", exception);
        }
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    public void bindTimestamp(PreparedStatement statement, int index, OffsetDateTime value) throws SQLException {
        if (sqlite) {
            statement.setString(index, value.withOffsetSameInstant(ZoneOffset.UTC).format(SQLITE_TIME));
        } else {
            statement.setObject(index, value);
        }
    }

    public OffsetDateTime readTimestamp(ResultSet result, String column) throws SQLException {
        if (!sqlite) return result.getObject(column, OffsetDateTime.class);
        String value = result.getString(column);
        return value == null ? null : LocalDateTime.parse(value.replace(' ', 'T')).atOffset(ZoneOffset.UTC);
    }

    public static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    public boolean isDuplicateOpenTicket(SQLException exception) {
        if ("23505".equals(exception.getSQLState())) return true;
        String message = exception.getMessage();
        return sqlite && (exception.getErrorCode() & 0xff) == 19 && message != null
                && message.contains("tickets.guild_id") && message.contains("tickets.opener_id")
                && message.contains("tickets.type");
    }

    public void initializeSchema() {
        String resource = sqlite ? "/schema-sqlite.sql" : "/schema.sql";
        String sql;
        try (InputStream input = Database.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("No se encontro " + resource + " dentro del JAR");
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer " + resource, exception);
        }

        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String block : sql.split(";")) {
                    if (!block.isBlank()) statement.execute(block);
                }
                connection.commit();
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    exception.addSuppressed(rollbackError);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo inicializar el almacenamiento", exception);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
