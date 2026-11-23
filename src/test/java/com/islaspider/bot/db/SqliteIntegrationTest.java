package com.islaspider.bot.db;

import com.islaspider.bot.config.BotConfig;
import com.islaspider.bot.model.TicketType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class SqliteIntegrationTest {
    @TempDir Path directory;
    private Database database;
    private TicketRepository tickets;
    private StrikeRepository strikes;

    @BeforeEach
    void connect() {
        database = open();
        tickets = new TicketRepository(database);
        strikes = new StrikeRepository(database);
    }

    private Database open() {
        BotConfig config = new BotConfig("test-token", 1, 2, 3, 4, 5, 6, 7, 8, 9,
                "jdbc:sqlite:" + directory.resolve("nested/bot.db"), "", "", 8,
                directory.resolve("transcripts"), true, 3, 5, 24, false);
        Database storage = new Database(config);
        storage.initializeSchema();
        return storage;
    }

    @AfterEach
    void close() {
        if (database != null) database.close();
    }

    private long create(long opener) {
        return tickets.create(1, null, opener, TicketType.HELP, "Jugador",
                "Ayuda", "Descripción del problema", null, "", List.of());
    }

    @Test
    void persistsTicketsMessagesAttachmentsAndExactInstantsAcrossRestart() {
        long id = create(100);
        long messageId = 9007199254740997L;
        OffsetDateTime time = OffsetDateTime.parse("2026-11-23T15:10:20.123456789-05:00");
        tickets.saveMessage(id, messageId, 100, "Jugador", "Necesito ayuda", time);
        tickets.saveMessage(id, messageId, 100, "Jugador", "Duplicado ignorado", time);
        tickets.saveAttachment(id, messageId, 100, "FILE", "prueba.png",
                "https://example.com/prueba.png", "image/png", 9876543210L, time);
        database.close();
        database = open();
        tickets = new TicketRepository(database);

        var ticket = tickets.findById(id).orElseThrow();
        assertNull(ticket.channelId());
        assertNull(ticket.accusedUserId());
        assertNull(ticket.claimedBy());
        assertNotNull(ticket.createdAt());
        assertEquals("Jugador", ticket.gameName());
        var messages = tickets.listMessages(id);
        assertEquals(1, messages.size());
        assertEquals(messageId, messages.getFirst().messageId());
        assertEquals(time.toInstant(), messages.getFirst().createdAt().toInstant());
        var attachments = tickets.listAttachments(id);
        assertEquals(1, attachments.size());
        assertEquals(messageId, attachments.getFirst().messageId());
        assertEquals(9876543210L, attachments.getFirst().sizeBytes());
        assertEquals(time.toInstant(), attachments.getFirst().createdAt().toInstant());
    }

    @Test
    void enforcesOneOpenTicketPerCategoryIncludingConcurrentRequests() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> attempt = () -> {
                try {
                    create(200);
                    return true;
                } catch (IllegalStateException exception) {
                    assertTrue(exception.getMessage().contains("Ya tienes un ticket"));
                    return false;
                }
            };
            var results = executor.invokeAll(List.of(attempt, attempt));
            int successes = 0;
            for (var result : results) if (result.get()) successes++;
            assertEquals(1, successes);
        }
        assertTrue(tickets.hasOpenTicket(1, 200, TicketType.HELP));
    }

    @Test
    void rollsBackFeedbackFailureAndClosesOnlyOnce() throws Exception {
        long id = create(300);
        assertThrows(IllegalStateException.class, () ->
                tickets.completeFeedbackAndClose(id, 300, 900, 11, 8, "Comentario", "Resuelto", "ticket.html"));
        assertEquals("OPEN", tickets.findById(id).orElseThrow().status());
        assertEquals(0, count("ticket_feedback"));
        assertTrue(tickets.completeFeedbackAndClose(id, 300, 900, 9, 8,
                "Comentario", "Resuelto", "ticket.html"));
        assertFalse(tickets.completeFeedbackAndClose(id, 300, 901, 10, 10,
                "Segundo", "Repetido", "otro.html"));
        assertEquals("CLOSED", tickets.findById(id).orElseThrow().status());
        assertEquals(1, count("ticket_feedback"));
        assertTrue(create(300) > id);
    }

    @Test
    void resolvesAppealsAndManualClosuresWithoutFeedback() {
        long appeal = tickets.create(1, null, 400, TicketType.APPEAL, "Jugador",
                "Apelación", "Motivo de apelación", null, "", List.of());
        assertTrue(tickets.resolveAppeal(appeal, 900, true, "Aceptada"));
        assertFalse(tickets.resolveAppeal(appeal, 901, false, "Tardía"));
        long manual = create(401);
        assertTrue(tickets.close(manual, 900, "Cierre manual", "ticket.html"));
        assertFalse(tickets.close(manual, 900, "Repetido", "ticket.html"));
        assertTrue(tickets.failOpen(create(402), "No se pudo crear el canal"));
    }

    @Test
    void savesStrikeAndAuditAtomicallyAndSupportsRevocation() throws Exception {
        var added = strikes.addWithAudit(1, 500, 2, "Incumplimiento", "", null, 900);
        assertEquals(2, added.activePoints());
        assertEquals(1, count("moderation_actions"));
        assertEquals(1, strikes.listActive(1, 500).size());
        assertNull(strikes.listActive(1, 500).getFirst().sourceTicketId());
        assertEquals(1, strikes.countActive(1));
        assertEquals(1, strikes.countSanctionedUsers(1));
        assertEquals(500, strikes.listSanctionedUsers(1, 10).getFirst().userId());
        assertThrows(IllegalStateException.class,
                () -> strikes.addWithAudit(1, 500, 3, null, "", null, 900));
        assertEquals(1, count("strikes"));
        assertEquals(1, count("moderation_actions"));
        assertTrue(strikes.revoke(1, added.id(), 900, "Retirado"));
        assertFalse(strikes.revoke(1, added.id(), 900, "Repetido"));
        assertEquals(0, strikes.countActive(1));
        new ModerationRepository(database).log(1, 500, 900, "WARN", "Aviso", null, null);
        assertEquals(2, count("moderation_actions"));
    }

    @Test
    void enablesForeignKeysAndInitializesSchemaIdempotently() throws Exception {
        assertThrows(IllegalStateException.class, () -> tickets.saveMessage(
                9999, 700, 100, "Jugador", "Sin ticket", OffsetDateTime.now()));
        long id = create(600);
        tickets.saveMessage(id, 701, 600, "Jugador", "Mensaje", OffsetDateTime.now());
        database.initializeSchema();
        assertTrue(tickets.findById(id).isPresent());
        try (var connection = database.connection(); var statement = connection.prepareStatement("DELETE FROM tickets WHERE id=?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
        assertEquals(0, count("ticket_messages"));
    }

    private long count(String table) throws Exception {
        try (var connection = database.connection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }
}
