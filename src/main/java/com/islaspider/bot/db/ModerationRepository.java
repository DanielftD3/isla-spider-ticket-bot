package com.islaspider.bot.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class ModerationRepository {
    private final Database database;

    public ModerationRepository(Database database) {
        this.database = database;
    }

    public void log(long guildId, long targetUserId, long moderatorId, String action, String reason,
                    Long durationSeconds, Long sourceTicketId) {
        String sql = """
                INSERT INTO moderation_actions
                    (guild_id, target_user_id, moderator_id, action, reason, duration_seconds, source_ticket_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, guildId);
            statement.setLong(2, targetUserId);
            statement.setLong(3, moderatorId);
            statement.setString(4, action);
            statement.setString(5, reason);
            if (durationSeconds == null) statement.setNull(6, java.sql.Types.BIGINT); else statement.setLong(6, durationSeconds);
            if (sourceTicketId == null) statement.setNull(7, java.sql.Types.BIGINT); else statement.setLong(7, sourceTicketId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo registrar la accion de moderacion", exception);
        }
    }
}

