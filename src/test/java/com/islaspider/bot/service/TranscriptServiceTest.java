package com.islaspider.bot.service;

import com.islaspider.bot.model.Ticket;
import com.islaspider.bot.model.TicketAttachment;
import com.islaspider.bot.model.TicketMessage;
import com.islaspider.bot.model.TicketType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptServiceTest {
    @Test
    void includesInitialCaseDataEscapesHtmlAndPrefersMirroredFiles() {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-06T00:00:00Z");
        Ticket ticket = new Ticket(42, 1, 2L, 3, TicketType.REPORT, "Jugador <uno>",
                "Reporte", "Descripción <script>alert(1)</script>", 987654321012345678L,
                "Prueba: https://example.com/video?a=1&b=2", "OPEN", null, now, null, null, null, null);
        List<TicketMessage> messages = List.of(
                new TicketMessage(100, 3, "Usuario <admin>", "Mensaje <b>sin formato</b>", now));
        List<TicketAttachment> attachments = List.of(
                new TicketAttachment(100L, 3, "FILE", "captura.png",
                        "https://cdn.example/original.png", "image/png", 100L, now),
                new TicketAttachment(100L, 3, "MIRROR", "captura.png",
                        "https://cdn.example/mirror.png", "image/png", 100L, now),
                new TicketAttachment(100L, 3, "LINK", null,
                        "https://example.com/contexto", null, null, now));

        String html = TranscriptService.render(ticket, messages, attachments);

        assertTrue(html.contains("987654321012345678"));
        assertTrue(html.contains("https://example.com/video?a=1&amp;b=2"));
        assertTrue(html.contains("https://cdn.example/mirror.png"));
        assertTrue(html.contains("https://example.com/contexto"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html.contains("Mensaje &lt;b&gt;sin formato&lt;/b&gt;"));
        assertFalse(html.contains("https://cdn.example/original.png"));
        assertFalse(html.contains("<script>alert(1)</script>"));
    }

    @Test
    void preservesAppealDecisionReviewerReasonAndConversationInTheCaseFile() {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-24T12:00:00Z");
        Ticket open = new Ticket(7, 1, 2L, 3, TicketType.APPEAL, "Jugador", "Warn #17",
                "Solicito revisar las pruebas", null, "Video inicial", "OPEN", null, now,
                null, null, null, null);
        Ticket resolved = open.resolvedAppeal(900, false, "Falta contexto <script>\nSegunda línea");
        String html = TranscriptService.render(resolved, List.of(
                new TicketMessage(100, 3, "Jugador", "Prueba adicional", now)), List.of());
        assertTrue(html.contains("Resolución del caso #7"));
        assertTrue(html.contains("RECHAZADA"));
        assertTrue(html.contains("900"));
        assertTrue(html.contains("Falta contexto &lt;script&gt;<br>Segunda línea"));
        assertTrue(html.contains("Warn #17"));
        assertTrue(html.contains("Prueba adicional"));
        assertFalse(html.contains("<script>"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> resolved.resolvedAppeal(901, true, "Otra decisión"));
    }
}
