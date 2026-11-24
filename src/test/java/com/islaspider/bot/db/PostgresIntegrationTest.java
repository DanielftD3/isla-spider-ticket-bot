package com.islaspider.bot.db;

import com.islaspider.bot.config.BotConfig;
import com.islaspider.bot.model.TicketType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PostgresIntegrationTest {
    private static Database database;
    private static TicketRepository tickets;
    private static StrikeRepository strikes;

    @BeforeAll
    static void connect() {
        String url = System.getenv("TEST_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "PostgreSQL de integración no configurado");
        String user = System.getenv().getOrDefault("TEST_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "postgres");
        BotConfig config = new BotConfig("test-token", 1, 2, 2, 3, 4, 5, 6, 7, 8, 9,
                url, user, password, 2, Path.of("target", "test-transcripts"),
                true, 3, 5, 24, false);
        database = new Database(config);
        database.initializeSchema();
        tickets = new TicketRepository(database);
        strikes = new StrikeRepository(database);
    }

    @BeforeEach
    void cleanDatabase() throws Exception {
        try (var connection = database.connection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE ticket_feedback, ticket_attachments, ticket_messages, "
                    + "moderation_actions, strikes, tickets RESTART IDENTITY CASCADE");
        }
    }

    @AfterAll
    static void disconnect() {
        if (database != null) database.close();
    }

    @Test
    void createsTicketAndInitialLinksAtomicallyAndPreventsDuplicates() {
        long ticketId = tickets.create(1, 100L, 200, TicketType.BUG, "Jugador",
                "Asunto", "Descripción suficientemente detallada", null,
                "https://example.com/prueba", List.of("https://example.com/prueba"));

        assertEquals(1, ticketId);
        assertEquals(1, tickets.listAttachments(ticketId).size());
        assertTrue(tickets.hasOpenTicket(1, 200, TicketType.BUG));
        assertThrows(IllegalStateException.class, () -> tickets.create(1, 101L, 200,
                TicketType.BUG, "Jugador", "Otro asunto", "Otra descripción", null,
                "", List.of()));
    }

    @Test
    void closesTicketAndSavesFeedbackInOneTransaction() throws Exception {
        long ticketId = tickets.create(1, 100L, 201, TicketType.HELP, "Jugador",
                "Necesito ayuda", "Descripción suficientemente detallada", null, "", List.of());

        assertTrue(tickets.completeFeedbackAndClose(ticketId, 201, 900, 10, 9,
                "Excelente atención", "Cierre por encuesta", "transcripts/ticket-1.html"));
        assertFalse(tickets.completeFeedbackAndClose(ticketId, 201, 900, 1, 1,
                "Duplicada", "Segundo cierre", "otro.html"));

        try (var connection = database.connection();
             var statement = connection.prepareStatement("""
                     SELECT t.status, t.closed_by, f.attention_rating, f.solution_rating, f.comment
                       FROM tickets t JOIN ticket_feedback f ON f.ticket_id=t.id
                      WHERE t.id=?
                     """)) {
            statement.setLong(1, ticketId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("CLOSED", result.getString("status"));
                assertEquals(900, result.getLong("closed_by"));
                assertEquals(10, result.getInt("attention_rating"));
                assertEquals(9, result.getInt("solution_rating"));
                assertEquals("Excelente atención", result.getString("comment"));
                assertFalse(result.next());
            }
        }
    }

    @Test
    void closesTicketManuallyWithoutCreatingFeedback() throws Exception {
        long ticketId = tickets.create(1, 100L, 203, TicketType.HELP, "Jugador",
                "Cierre manual", "Descripción suficientemente detallada", null, "", List.of());

        assertTrue(tickets.close(ticketId, 900, "Cierre manual por el equipo",
                "transcripts/ticket-manual.html"));
        assertFalse(tickets.close(ticketId, 901, "Segundo cierre",
                "transcripts/ticket-manual-2.html"));

        try (var connection = database.connection();
             var statement = connection.prepareStatement("""
                     SELECT status, closed_by, close_reason, transcript_path,
                            (SELECT COUNT(*) FROM ticket_feedback WHERE ticket_id=?) AS feedback_count
                       FROM tickets
                      WHERE id=?
                     """)) {
            statement.setLong(1, ticketId);
            statement.setLong(2, ticketId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("CLOSED", result.getString("status"));
                assertEquals(900, result.getLong("closed_by"));
                assertEquals("Cierre manual por el equipo", result.getString("close_reason"));
                assertEquals("transcripts/ticket-manual.html", result.getString("transcript_path"));
                assertEquals(0, result.getInt("feedback_count"));
                assertFalse(result.next());
            }
        }
    }

    @Test
    void onlyOneModeratorCanResolveAnAppeal() {
        long ticketId = tickets.create(1, null, 202, TicketType.APPEAL, "Jugador",
                "Apelar ban", "Descripción suficientemente detallada", null, "", List.of());

        assertTrue(tickets.attachChannel(ticketId, 8100));
        assertFalse(tickets.attachChannel(ticketId, 8101));
        assertFalse(tickets.close(ticketId, 900, "Sin revisión", "incorrecto.html"));
        assertTrue(tickets.resolveAppeal(ticketId, 900, true, "Apelación aceptada", "caso.html"));
        assertFalse(tickets.resolveAppeal(ticketId, 901, false, "Decisión tardía"));
        var resolution = tickets.findByChannel(8100).orElseThrow();
        assertEquals(900L, resolution.closedBy());
        assertEquals("Apelación aceptada", resolution.closeReason());
        assertEquals("caso.html", resolution.transcriptPath());
        assertNotNull(resolution.closedAt());
    }

    @Test
    void savesStrikeAndAuditTogether() throws Exception {
        StrikeRepository.AddedStrike added = strikes.addWithAudit(1, 300, 2,
                "Incumplimiento", "https://example.com/evidencia", null, 900);

        assertEquals(1, added.id());
        assertEquals(2, added.activePoints());
        try (var connection = database.connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM moderation_actions WHERE action='STRIKE'")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }
}
