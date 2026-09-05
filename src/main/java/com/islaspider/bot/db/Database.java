package com.islaspider.bot.db;

import com.islaspider.bot.config.BotConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database implements AutoCloseable {
    private final HikariDataSource dataSource;

    public Database(BotConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.databaseUrl());
        hikari.setUsername(config.databaseUser());
        hikari.setPassword(config.databasePassword());
        hikari.setMaximumPoolSize(config.databasePoolSize());
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(10_000);
        hikari.setValidationTimeout(5_000);
        hikari.setPoolName("isla-spider-postgres");
        hikari.addDataSourceProperty("ApplicationName", "isla-spider-ticket-bot");
        this.dataSource = new HikariDataSource(hikari);
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    public void initializeSchema() {
        String sql;
        try (InputStream input = Database.class.getResourceAsStream("/schema.sql")) {
            if (input == null) {
                throw new IllegalStateException("No se encontro schema.sql dentro del JAR");
            }
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer schema.sql", exception);
        }

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            for (String block : sql.split(";")) {
                if (!block.isBlank()) {
                    statement.execute(block);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo inicializar el esquema de PostgreSQL", exception);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}

