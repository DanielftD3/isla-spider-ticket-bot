package com.islaspider.bot.db;

import com.islaspider.bot.model.Ticket;
import com.islaspider.bot.model.TicketAttachment;
import com.islaspider.bot.model.TicketMessage;
import com.islaspider.bot.model.TicketType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TicketRepository {
    private final Database database;

    public TicketRepository(Database database) {
        this.database = database;
    }

    public long create(long guildId, Long channelId, long openerId, TicketType type, String gameName,
                       String subject, String description, Long accusedUserId, String evidence) {
        String sql = """
                INSERT INTO tickets
                    (guild_id, channel_id, opener_id, type, game_name, subject, description, accused_user_id, evidence)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, guildId);
            if (channelId == null) statement.setNull(2, java.sql.Types.BIGINT); else statement.setLong(2, channelId);
            statement.setLong(3, openerId);
            statement.setString(4, type.name());
            statement.setString(5, gameName);
            statement.setString(6, subject);
            statement.setString(7, description);
            if (accusedUserId == null) statement.setNull(8, java.sql.Types.BIGINT); else statement.setLong(8, accusedUserId);
            statement.setString(9, blankToNull(evidence));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            throw new SQLException("PostgreSQL no devolvio el ID del ticket");
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo guardar el ticket", exception);
        }
    }

    public boolean hasOpenTicket(long guildId, long openerId, TicketType type) {
        String sql = "SELECT 1 FROM tickets WHERE guild_id=? AND opener_id=? AND type=? AND status='OPEN' LIMIT 1";
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, guildId);
            statement.setLong(2, openerId);
            statement.setString(3, type.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo validar si el usuario tiene tickets abiertos", exception);
        }
    }

    public Optional<Ticket> findByChannel(long channelId) {
        return findOne("SELECT * FROM tickets WHERE channel_id=?", channelId);
    }

    public Optional<Ticket> findById(long ticketId) {
        return findOne("SELECT * FROM tickets WHERE id=?", ticketId);
    }

    private Optional<Ticket> findOne(String sql, long value) {
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo consultar el ticket", exception);
        }
    }

    public boolean claim(long ticketId, long moderatorId) {
        String sql = "UPDATE tickets SET claimed_by=? WHERE id=? AND status='OPEN' AND claimed_by IS NULL";
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, moderatorId);
            statement.setLong(2, ticketId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo reclamar el ticket", exception);
        }
    }

    public void close(long ticketId, long moderatorId, String reason, String transcriptPath) {
        String sql = """
                UPDATE tickets
                   SET status='CLOSED', closed_at=NOW(), closed_by=?, close_reason=?, transcript_path=?
                 WHERE id=? AND status='OPEN'
                """;
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, moderatorId);
            statement.setString(2, reason);
            statement.setString(3, transcriptPath);
            statement.setLong(4, ticketId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo cerrar el ticket", exception);
        }
    }

    public void resolveAppeal(long ticketId, long moderatorId, boolean approved, String reason) {
        String status = approved ? "APPROVED" : "REJECTED";
        String sql = """
                UPDATE tickets
                   SET status=?, closed_at=NOW(), closed_by=?, close_reason=?
                 WHERE id=? AND type='APPEAL' AND status='OPEN'
                """;
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setLong(2, moderatorId);
            statement.setString(3, reason);
            statement.setLong(4, ticketId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo resolver la apelacion", exception);
        }
    }

    public boolean saveFeedback(long ticketId, long userId, int rating) {
        String sql = """
                INSERT INTO ticket_feedback(ticket_id, user_id, rating)
                VALUES (?, ?, ?)
                ON CONFLICT (ticket_id) DO NOTHING
                """;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ticketId);
            statement.setLong(2, userId);
            statement.setInt(3, rating);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo guardar la valoración", exception);
        }
    }

    public void saveMessage(long ticketId, long messageId, long authorId, String authorName,
                            String content, OffsetDateTime createdAt) {
        String sql = """
                INSERT INTO ticket_messages(ticket_id, message_id, author_id, author_name, content, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (message_id) DO NOTHING
                """;
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ticketId);
            statement.setLong(2, messageId);
            statement.setLong(3, authorId);
            statement.setString(4, authorName);
            statement.setString(5, content == null ? "" : content);
            statement.setObject(6, createdAt);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo archivar un mensaje del ticket", exception);
        }
    }

    public void saveAttachment(long ticketId, Long messageId, long authorId, String kind, String fileName,
                               String url, String contentType, Long sizeBytes, OffsetDateTime createdAt) {
        String sql = """
                INSERT INTO ticket_attachments
                    (ticket_id, message_id, author_id, kind, file_name, url, content_type, size_bytes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ticketId);
            if (messageId == null) statement.setNull(2, java.sql.Types.BIGINT); else statement.setLong(2, messageId);
            statement.setLong(3, authorId);
            statement.setString(4, kind);
            statement.setString(5, blankToNull(fileName));
            statement.setString(6, url);
            statement.setString(7, blankToNull(contentType));
            if (sizeBytes == null) statement.setNull(8, java.sql.Types.BIGINT); else statement.setLong(8, sizeBytes);
            statement.setObject(9, createdAt);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo registrar un archivo o enlace", exception);
        }
    }

    public List<TicketMessage> listMessages(long ticketId) {
        String sql = "SELECT * FROM ticket_messages WHERE ticket_id=? ORDER BY created_at, id";
        List<TicketMessage> messages = new ArrayList<>();
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ticketId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    messages.add(new TicketMessage(
                            result.getLong("message_id"), result.getLong("author_id"),
                            result.getString("author_name"), result.getString("content"),
                            result.getObject("created_at", OffsetDateTime.class)));
                }
            }
            return messages;
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudieron consultar los mensajes", exception);
        }
    }

    public List<TicketAttachment> listAttachments(long ticketId) {
        String sql = "SELECT * FROM ticket_attachments WHERE ticket_id=? ORDER BY created_at, id";
        List<TicketAttachment> attachments = new ArrayList<>();
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ticketId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    attachments.add(new TicketAttachment(
                            result.getObject("message_id", Long.class), result.getLong("author_id"),
                            result.getString("kind"), result.getString("file_name"), result.getString("url"),
                            result.getString("content_type"), result.getObject("size_bytes", Long.class),
                            result.getObject("created_at", OffsetDateTime.class)));
                }
            }
            return attachments;
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudieron consultar los archivos", exception);
        }
    }

    private Ticket map(ResultSet result) throws SQLException {
        return new Ticket(
                result.getLong("id"), result.getLong("guild_id"), result.getObject("channel_id", Long.class),
                result.getLong("opener_id"), TicketType.valueOf(result.getString("type")),
                result.getString("game_name"), result.getString("subject"), result.getString("description"),
                result.getObject("accused_user_id", Long.class), result.getString("evidence"),
                result.getString("status"), result.getObject("claimed_by", Long.class),
                result.getObject("created_at", OffsetDateTime.class));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
