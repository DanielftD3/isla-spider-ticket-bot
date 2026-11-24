package com.islaspider.bot.service;

import com.islaspider.bot.config.BotConfig;
import com.islaspider.bot.db.TicketRepository;
import com.islaspider.bot.model.Ticket;
import com.islaspider.bot.model.TicketAttachment;
import com.islaspider.bot.model.TicketMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TranscriptService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'");
    private final BotConfig config;
    private final TicketRepository tickets;

    public TranscriptService(BotConfig config, TicketRepository tickets) {
        this.config = config;
        this.tickets = tickets;
    }

    public Path generate(Ticket ticket) {
        List<TicketMessage> messages = tickets.listMessages(ticket.id());
        List<TicketAttachment> attachments = tickets.listAttachments(ticket.id());
        String html = render(ticket, messages, attachments);

        try {
            Files.createDirectories(config.transcriptDirectory());
            String suffix = ticket.isResolvedAppeal() ? "-" + UUID.randomUUID() : "";
            Path output = config.transcriptDirectory().resolve("ticket-" + ticket.id() + suffix + ".html").toAbsolutePath();
            Files.writeString(output, html, StandardCharsets.UTF_8);
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar la transcripcion", exception);
        }
    }

    static String render(Ticket ticket, List<TicketMessage> messages, List<TicketAttachment> attachments) {
        Map<Long, List<TicketAttachment>> rawAttachments = attachments.stream()
                .filter(item -> item.messageId() != null)
                .collect(Collectors.groupingBy(TicketAttachment::messageId));
        Map<Long, List<TicketAttachment>> attachmentsByMessage = rawAttachments.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> preferMirrors(entry.getValue())));

        StringBuilder body = new StringBuilder();
        for (TicketMessage message : messages) {
            body.append("<article class=\"message\"><header><strong>")
                    .append(escape(message.authorName())).append("</strong><span>")
                    .append(message.createdAt().withOffsetSameInstant(ZoneOffset.UTC).format(DATE_FORMAT))
                    .append("</span></header><p>").append(withBreaks(message.content())).append("</p>");
            for (TicketAttachment attachment : attachmentsByMessage.getOrDefault(message.messageId(), List.of())) {
                body.append("<a class=\"attachment\" href=\"").append(attribute(attachment.url())).append("\">📎 ")
                        .append(escape(attachment.fileName() == null ? attachment.url() : attachment.fileName()))
                        .append("</a>");
            }
            body.append("</article>");
        }

        String resolution = ticket.isResolvedAppeal() ? """
                <section class="summary"><h2>Resolución del caso #%d</h2>
                <p><b>Decisión:</b> %s</p><p><b>Resuelto por:</b> %d</p>
                <p><b>Fecha:</b> %s</p><p><b>Motivo:</b> %s</p></section>
                """.formatted(ticket.id(), "APPROVED".equals(ticket.status()) ? "APROBADA" : "RECHAZADA",
                ticket.closedBy(), ticket.closedAt().withOffsetSameInstant(ZoneOffset.UTC).format(DATE_FORMAT),
                withBreaks(ticket.closeReason())) : "";

        String html = """
                <!doctype html><html lang="es"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Ticket #%d</title><style>
                body{margin:0;background:#1e1f22;color:#dbdee1;font:15px Arial,sans-serif}
                main{max-width:960px;margin:auto;padding:28px}.summary,.message{background:#2b2d31;border-radius:8px;padding:16px;margin:12px 0}
                h1{color:#fff}.summary b{color:#fff}.message header{display:flex;gap:12px;align-items:baseline}.message header span{color:#949ba4;font-size:12px}
                .message p{white-space:normal;line-height:1.45}.attachment{display:block;color:#00a8fc;margin:7px 0;word-break:break-all}
                </style></head><body><main><h1>Transcripción del ticket #%d</h1>
                <section class="summary"><p><b>Tipo:</b> %s</p><p><b>Nombre en el juego:</b> %s</p>
                <p><b>Usuario de Discord:</b> %d</p>
                <p><b>Asunto:</b> %s</p><p><b>Descripción:</b> %s</p>
                <p><b>Jugador reportado:</b> %s</p><p><b>Pruebas iniciales:</b> %s</p></section>%s%s</main></body></html>
                """.formatted(ticket.id(), ticket.id(), escape(ticket.type().label()), escape(ticket.gameName()),
                ticket.openerId(), escape(ticket.subject()), withBreaks(ticket.description()),
                ticket.accusedUserId() == null ? "No aplica" : escape(String.valueOf(ticket.accusedUserId())),
                ticket.evidence() == null || ticket.evidence().isBlank() ? "Sin pruebas iniciales" : withBreaks(ticket.evidence()),
                resolution, body);
        return html;
    }

    private static List<TicketAttachment> preferMirrors(List<TicketAttachment> attachments) {
        boolean hasMirror = attachments.stream().anyMatch(item -> "MIRROR".equals(item.kind()));
        if (!hasMirror) return attachments;
        return attachments.stream().filter(item -> !"FILE".equals(item.kind())).toList();
    }

    private static String withBreaks(String value) {
        return escape(value == null ? "" : value).replace("\n", "<br>");
    }

    private static String attribute(String value) {
        return escape(value).replace("\"", "&quot;");
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
