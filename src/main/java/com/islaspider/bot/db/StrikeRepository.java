package com.islaspider.bot.db;

import com.islaspider.bot.model.Strike;
import com.islaspider.bot.model.SanctionedUser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public final class StrikeRepository {
    private final Database database;

    public StrikeRepository(Database database) {
        this.database = database;
    }

    public long add(long guildId, long userId, int points, String reason, String evidence,
                    Long sourceTicketId, long moderatorId) {
        String sql = """
                INSERT INTO strikes(guild_id, user_id, points, reason, evidence_url, source_ticket_id, moderator_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, guildId);
            statement.setLong(2, userId);
            statement.setInt(3, points);
            statement.setString(4, reason);
            statement.setString(5, blankToNull(evidence));
            if (sourceTicketId == null) statement.setNull(6, java.sql.Types.BIGINT); else statement.setLong(6, sourceTicketId);
            statement.setLong(7, moderatorId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            throw new SQLException("PostgreSQL no devolvio el ID del strike");
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo registrar el strike", exception);
        }
    }

    public List<Strike> listActive(long guildId, long userId) {
        String sql = "SELECT * FROM strikes WHERE guild_id=? AND user_id=? AND active=true ORDER BY created_at";
        List<Strike> strikes = new ArrayList<>();
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, guildId);
            statement.setLong(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    strikes.add(new Strike(
                            result.getLong("id"), result.getLong("user_id"), result.getInt("points"),
                            result.getString("reason"), result.getString("evidence_url"),
                            result.getObject("source_ticket_id", Long.class), result.getLong("moderator_id"),
                            result.getObject("created_at", OffsetDateTime.class)));
                }
            }
            return strikes;
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudieron consultar los strikes", exception);
        }
    }

    public int activePoints(long guildId, long userId) {
        String sql = "SELECT COALESCE(SUM(points), 0) FROM strikes WHERE guild_id=? AND user_id=? AND active=true";
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, guildId);
            statement.setLong(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo calcular el total de strikes", exception);
        }
    }

    public boolean revoke(long guildId, long strikeId, long moderatorId, String reason) {
        String sql = """
                UPDATE strikes SET active=false, revoked_at=NOW(), revoked_by=?, revoke_reason=?
                 WHERE id=? AND guild_id=? AND active=true
                """;
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, moderatorId);
            statement.setString(2, reason);
            statement.setLong(3, strikeId);
            statement.setLong(4, guildId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo retirar el strike", exception);
        }
    }

    public long countActive(long guildId) {
        return count("SELECT COUNT(*) FROM strikes WHERE guild_id=? AND active=true", guildId);
    }

    public long countSanctionedUsers(long guildId) {
        return count("SELECT COUNT(DISTINCT user_id) FROM strikes WHERE guild_id=? AND active=true", guildId);
    }

    public List<SanctionedUser> listSanctionedUsers(long guildId, int limit) {
        String sql = """
                SELECT user_id, COUNT(*) AS warn_count, COALESCE(SUM(points), 0) AS points
                  FROM strikes
                 WHERE guild_id=? AND active=true
                 GROUP BY user_id
                 ORDER BY points DESC, warn_count DESC
                 LIMIT ?
                """;
        List<SanctionedUser> users = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, guildId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    users.add(new SanctionedUser(result.getLong("user_id"),
                            result.getInt("warn_count"), result.getInt("points")));
                }
            }
            return users;
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudieron consultar los usuarios sancionados", exception);
        }
    }

    private long count(String sql, long guildId) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudieron calcular las estadísticas de warns", exception);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
