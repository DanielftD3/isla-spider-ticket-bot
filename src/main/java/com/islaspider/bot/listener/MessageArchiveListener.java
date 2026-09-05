package com.islaspider.bot.listener;

import com.islaspider.bot.config.BotConfig;
import com.islaspider.bot.db.TicketRepository;
import com.islaspider.bot.model.Ticket;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageArchiveListener extends ListenerAdapter {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private final BotConfig config;
    private final TicketRepository tickets;

    public MessageArchiveListener(BotConfig config, TicketRepository tickets) {
        this.config = config;
        this.tickets = tickets;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getGuild().getIdLong() != config.guildId() || event.getAuthor().isBot()) return;
        if (!(event.getChannel() instanceof TextChannel textChannel)
                || textChannel.getParentCategoryIdLong() != config.ticketCategoryId()) return;
        Ticket ticket = tickets.findByChannel(textChannel.getIdLong()).orElse(null);
        if (ticket == null || !"OPEN".equals(ticket.status())) return;

        Message message = event.getMessage();
        OffsetDateTime createdAt = message.getTimeCreated();
        tickets.saveMessage(ticket.id(), message.getIdLong(), event.getAuthor().getIdLong(),
                event.getAuthor().getEffectiveName(), message.getContentRaw(), createdAt);

        for (Message.Attachment attachment : message.getAttachments()) {
            tickets.saveAttachment(ticket.id(), message.getIdLong(), event.getAuthor().getIdLong(), "FILE",
                    attachment.getFileName(), attachment.getUrl(), attachment.getContentType(), (long) attachment.getSize(), createdAt);
        }

        Set<String> links = extractLinks(message.getContentRaw());
        for (String link : links) {
            tickets.saveAttachment(ticket.id(), message.getIdLong(), event.getAuthor().getIdLong(), "LINK",
                    null, link, null, null, createdAt);
        }
        if (!message.getAttachments().isEmpty() || !links.isEmpty()) {
            sendEvidenceLog(event, ticket, message.getAttachments().size(), links);
        }
    }

    private void sendEvidenceLog(MessageReceivedEvent event, Ticket ticket, int fileCount,
                                 Set<String> links) {
        TextChannel log = event.getGuild().getTextChannelById(config.ticketImageChannelId());
        if (log == null) return;
        StringBuilder linkText = new StringBuilder();
        links.stream().limit(10).forEach(url -> linkText.append(url).append('\n'));
        String title = fileCount > 0
                ? (links.isEmpty() ? "📎 Archivo enviado en ticket" : "🎬 Archivo o enlace enviado en ticket")
                : "🎬 Enlace enviado en ticket";
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(52, 152, 219))
                .setTitle(title)
                .addField("Ticket", event.getChannel().getAsMention(), true)
                .addField("Enviado por", event.getAuthor().getAsMention(), true)
                .addField("Archivos", String.valueOf(fileCount), true)
                .setTimestamp(event.getMessage().getTimeCreated());
        if (!links.isEmpty()) embed.addField("Enlace(s)", truncate(linkText.toString(), 1000), false);
        List<FileUpload> mirrorFiles = event.getMessage().getAttachments().stream()
                .filter(attachment -> attachment.getSize() <= event.getGuild().getMaxFileSize())
                .limit(10)
                .map(attachment -> attachment.getProxy().downloadAsFileUpload(attachment.getFileName()))
                .toList();
        var action = log.sendMessageEmbeds(embed.build());
        if (!mirrorFiles.isEmpty()) action.addFiles(mirrorFiles);
        action.queue(logMessage -> {
            for (Message.Attachment mirror : logMessage.getAttachments()) {
                tickets.saveAttachment(ticket.id(), event.getMessageIdLong(), event.getAuthor().getIdLong(), "MIRROR",
                        mirror.getFileName(), mirror.getUrl(), mirror.getContentType(), (long) mirror.getSize(), logMessage.getTimeCreated());
            }
        }, ignored -> {});
    }

    private static Set<String> extractLinks(String content) {
        Set<String> links = new LinkedHashSet<>();
        Matcher matcher = URL_PATTERN.matcher(content == null ? "" : content);
        while (matcher.find()) links.add(matcher.group().replaceAll("[),.;]+$", ""));
        return links;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
