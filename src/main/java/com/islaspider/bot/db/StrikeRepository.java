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
    public record AddedStrike(long id, int activePoints) {}

    private final Database database;

    public StrikeRepository(Database database) {
        this.database = database;
    }

    public AddedStrike addWithAudit(long guildId, long userId, int points, String reason, String evidence,
                                    Long sourceTicketId, long moderatorId) {
        String strikeSql = """
                INSERT INTO strikes(guild_id, user_id, points, reason, evidence_url, source_ticket_id, moderator_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        String auditSql = """
                INSERT INTO moderation_actions
                    (guild_id, target_user_id, moderator_id, action, reason, duration_seconds, source_ticket_id)
                VALUES (?, ?, ?, 'STRIKE', ?, NULL, ?)
                """;
        String totalSql = "SELECT COALESCE(SUM(points), 0) FROM strikes WHERE guild_id=? AND user_id=? AND active=true";
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement strike = connection.prepareStatement(strikeSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement audit = connection.prepareStatement(auditSql);
                 PreparedStatement total = connection.prepareStatement(totalSql)) {
                strike.setLong(1, guildId);
                strike.setLong(2, userId);
                strike.setInt(3, points);
                strike.setString(4, reason);
                strike.setString(5, blankToNull(evidence));
                if (sourceTicketId == null) strike.setNull(6, java.sql.Types.BIGINT); else strike.setLong(6, sourceTicketId);
                strike.setLong(7, moderatorId);
                strike.executeUpdate();
                long strikeId;
                try (ResultSet keys = strike.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("El almacenamiento no devolvio el ID del strike");
                    strikeId = keys.getLong(1);
                }

                audit.setLong(1, guildId);
                audit.setLong(2, userId);
                audit.setLong(3, moderatorId);
                audit.setString(4, reason);
                if (sourceTicketId == null) audit.setNull(5, java.sql.Types.BIGINT); else audit.setLong(5, sourceTicketId);
                audit.executeUpdate();

                total.setLong(1, guildId);
                total.setLong(2, userId);
                int activePoints;
                try (ResultSet result = total.executeQuery()) {
                    result.next();
                    activePoints = result.getInt(1);
                }
                connection.commit();
                return new AddedStrike(strikeId, activePoints);
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
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
                            Database.nullableLong(result, "source_ticket_id"), result.getLong("moderator_id"),
                            database.readTimestamp(result, "created_at")));
                }
            }
            return strikes;
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudieron consultar los strikes", exception);
        }
    }

    public boolean revoke(long guildId, long strikeId, long moderatorId, String reason) {
        String sql = """
                UPDATE strikes SET active=false, revoked_at=CURRENT_TIMESTAMP, revoked_by=?, revoke_reason=?
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

    private static void rollback(Connection connection, SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }
}
