package com.islaspider.bot.listener;

import com.islaspider.bot.config.BotConfig;
import com.islaspider.bot.db.ModerationRepository;
import com.islaspider.bot.db.StrikeRepository;
import com.islaspider.bot.db.TicketRepository;
import com.islaspider.bot.model.Ticket;
import com.islaspider.bot.model.TicketType;
import com.islaspider.bot.service.PanelFactory;
import com.islaspider.bot.service.PermissionService;
import com.islaspider.bot.service.TranscriptService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;

import java.awt.Color;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TicketListener extends ListenerAdapter {
    private static final String TICKET_MODAL_PREFIX = "ticket:form:";
    private static final String STRIKE_MODAL_PREFIX = "ticket:strike-form:";
    private static final String CLOSE_MODAL_PREFIX = "ticket:close-form:";
    private static final String APPEAL_DECISION_PREFIX = "appeal:decision-form:";
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    private final BotConfig config;
    private final TicketRepository tickets;
    private final StrikeRepository strikes;
    private final ModerationRepository moderation;
    private final PermissionService permissions;
    private final PanelFactory panels;
    private final TranscriptService transcripts;

    public TicketListener(BotConfig config, TicketRepository tickets, StrikeRepository strikes,
                          ModerationRepository moderation, PermissionService permissions,
                          PanelFactory panels, TranscriptService transcripts) {
        this.config = config;
        this.tickets = tickets;
        this.strikes = strikes;
        this.moderation = moderation;
        this.permissions = permissions;
        this.panels = panels;
        this.transcripts = transcripts;
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!PanelFactory.CREATE_MENU_ID.equals(event.getComponentId()) || event.getValues().isEmpty()) return;
        TicketType type = TicketType.fromCode(event.getValues().getFirst());
        event.replyModal(buildTicketModal(type)).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        try {
            if (id.startsWith(TICKET_MODAL_PREFIX)) {
                createTicket(event, TicketType.fromCode(id.substring(TICKET_MODAL_PREFIX.length())));
            } else if (id.startsWith(STRIKE_MODAL_PREFIX)) {
                addStrikeFromTicket(event, Long.parseLong(id.substring(STRIKE_MODAL_PREFIX.length())));
            } else if (id.startsWith(CLOSE_MODAL_PREFIX)) {
                closeTicket(event, Long.parseLong(id.substring(CLOSE_MODAL_PREFIX.length())));
            } else if (id.startsWith(APPEAL_DECISION_PREFIX)) {
                decideAppeal(event, id.substring(APPEAL_DECISION_PREFIX.length()));
            }
        } catch (RuntimeException exception) {
            replyError(event, exception);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        try {
            String id = event.getComponentId();
            if (PanelFactory.CLAIM_BUTTON_ID.equals(id)) {
                claimTicket(event);
            } else if (PanelFactory.STRIKE_BUTTON_ID.equals(id)) {
                openStrikeModal(event);
            } else if (PanelFactory.CLOSE_BUTTON_ID.equals(id)) {
                openCloseModal(event);
            } else if (id.startsWith("appeal:approve:") || id.startsWith("appeal:reject:")) {
                openAppealDecisionModal(event);
            }
        } catch (RuntimeException exception) {
            if (!event.isAcknowledged()) event.reply("❌ " + safeMessage(exception)).setEphemeral(true).queue();
        }
    }

    private Modal buildTicketModal(TicketType type) {
        Modal.Builder modal = Modal.create(TICKET_MODAL_PREFIX + type.code(), type.label())
                .addComponents(
                        label("Nombre dentro del juego", "game-name", TextInputStyle.SHORT,
                                "Tu nombre exacto en el juego", 2, 100, true),
                        label("Asunto", "subject", TextInputStyle.SHORT,
                                type == TicketType.APPEAL ? "¿Qué sanción deseas apelar?" : "Resumen del caso", 5, 150, true),
                        label("Explicación detallada", "description", TextInputStyle.PARAGRAPH,
                                "Describe qué pasó, cuándo y quiénes participaron", 20, 3000, true)
                );
        if (type == TicketType.REPORT) {
            modal.addComponents(label("ID de Discord del jugador reportado", "accused-id", TextInputStyle.SHORT,
                    "Ejemplo: 123456789012345678", 15, 25, true));
        }
        modal.addComponents(label("Pruebas o enlaces", "evidence", TextInputStyle.PARAGRAPH,
                "Opcional. También puedes subir archivos en el ticket", 0, 1500, false));
        return modal.build();
    }

    private Label label(String title, String id, TextInputStyle style, String placeholder,
                        int min, int max, boolean required) {
        TextInput.Builder input = TextInput.create(id, style)
                .setPlaceholder(placeholder)
                .setMaxLength(max)
                .setRequired(required);
        if (min > 0) input.setMinLength(min);
        return Label.of(title, input.build());
    }

    private void createTicket(ModalInteractionEvent event, TicketType type) {
        Guild guild = requireGuild(event.getGuild());
        String gameName = value(event, "game-name");
        String subject = value(event, "subject");
        String description = value(event, "description");
        String evidence = optionalValue(event, "evidence");
        Long accusedId = type == TicketType.REPORT ? parseDiscordId(value(event, "accused-id")) : null;

        if (tickets.hasOpenTicket(guild.getIdLong(), event.getUser().getIdLong(), type)) {
            event.reply("⚠️ Ya tienes un ticket abierto de esta categoría.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        if (type == TicketType.APPEAL) {
            createAppeal(event, guild, gameName, subject, description, evidence);
            return;
        }

        Category category = guild.getCategoryById(config.ticketCategoryId());
        Role staffRole = guild.getRoleById(config.staffRoleId());
        Role modRole = guild.getRoleById(config.moderatorRoleId());
        Member opener = event.getMember();
        if (category == null || staffRole == null || modRole == null || opener == null) {
            event.getHook().editOriginal("❌ La categoría o los roles del bot no están bien configurados.").queue();
            return;
        }

        EnumSet<Permission> userAllowed = EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
                Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS);
        EnumSet<Permission> staffAllowed = EnumSet.copyOf(userAllowed);
        staffAllowed.add(Permission.MESSAGE_MANAGE);

        var action = guild.createTextChannel(channelName(type, event.getUser()), category)
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(opener, userAllowed, null)
                .addPermissionOverride(staffRole, staffAllowed, null);
        if (modRole.getIdLong() != staffRole.getIdLong()) {
            action.addPermissionOverride(modRole, staffAllowed, null);
        }

        Long finalAccusedId = accusedId;
        action.queue(channel -> {
            try {
                long ticketId = tickets.create(guild.getIdLong(), channel.getIdLong(), event.getUser().getIdLong(),
                        type, gameName, subject, description, finalAccusedId, evidence);
                saveInitialLinks(ticketId, event.getUser().getIdLong(), evidence);
                channel.sendMessage(event.getUser().getAsMention() + " <@&" + config.staffRoleId() + ">")
                        .setEmbeds(ticketCreatedEmbed(ticketId, type, gameName, subject, description, finalAccusedId, evidence))
                        .setComponents(panels.ticketControls())
                        .queue();
                event.getHook().editOriginal("✅ Ticket creado: " + channel.getAsMention()).queue();
            } catch (RuntimeException exception) {
                channel.delete().reason("Error al guardar el ticket").queue();
                event.getHook().editOriginal("❌ No se pudo guardar el ticket: " + safeMessage(exception)).queue();
            }
        }, error -> event.getHook().editOriginal("❌ Discord no permitió crear el canal: " + error.getMessage()).queue());
    }

    private void createAppeal(ModalInteractionEvent event, Guild guild, String gameName, String subject,
                              String description, String evidence) {
        long ticketId = tickets.create(guild.getIdLong(), null, event.getUser().getIdLong(), TicketType.APPEAL,
                gameName, subject, description, null, evidence);
        saveInitialLinks(ticketId, event.getUser().getIdLong(), evidence);
        TextChannel log = guild.getTextChannelById(config.appealLogChannelId());
        if (log == null) {
            event.getHook().editOriginal("❌ No existe el canal configurado para apelaciones.").queue();
            return;
        }
        log.sendMessageEmbeds(new EmbedBuilder()
                        .setColor(new Color(245, 166, 35))
                        .setTitle("⚖️ Nueva apelación #" + ticketId)
                        .addField("Usuario", event.getUser().getAsMention() + " (`" + event.getUser().getId() + "`)", false)
                        .addField("Nombre en juego", gameName, true)
                        .addField("Sanción", subject, true)
                        .addField("Explicación", truncate(description, 1000), false)
                        .addField("Pruebas", evidence.isBlank() ? "Sin enlaces" : truncate(evidence, 1000), false)
                        .setTimestamp(OffsetDateTime.now())
                        .build())
                .setActionRow(
                        net.dv8tion.jda.api.components.buttons.Button.success("appeal:approve:" + ticketId, "Aprobar"),
                        net.dv8tion.jda.api.components.buttons.Button.danger("appeal:reject:" + ticketId, "Rechazar"))
                .queue();
        event.getUser().openPrivateChannel()
                .flatMap(channel -> channel.sendMessage("⚖️ Recibimos tu apelación **#" + ticketId + "**. Te responderemos por este medio."))
                .queue(null, ignored -> {});
        event.getHook().editOriginal("✅ Apelación #" + ticketId + " enviada. La respuesta llegará por mensaje privado.").queue();
    }

    private void claimTicket(ButtonInteractionEvent event) {
        if (!permissions.isStaff(event.getMember())) {
            event.reply("❌ Solo el equipo puede reclamar tickets.").setEphemeral(true).queue();
            return;
        }
        Ticket ticket = requireChannelTicket(event.getChannel().getIdLong());
        boolean claimed = tickets.claim(ticket.id(), event.getUser().getIdLong());
        if (claimed) {
            event.reply("✋ Ticket reclamado por " + event.getUser().getAsMention() + ".").queue();
        } else {
            event.reply("⚠️ Este ticket ya fue reclamado.").setEphemeral(true).queue();
        }
    }

    private void openStrikeModal(ButtonInteractionEvent event) {
        if (!permissions.isModerator(event.getMember())) {
            event.reply("❌ Solo Moderación puede agregar strikes.").setEphemeral(true).queue();
            return;
        }
        Ticket ticket = requireChannelTicket(event.getChannel().getIdLong());
        TextInput.Builder target = TextInput.create("target-id", TextInputStyle.SHORT)
                .setPlaceholder("ID de Discord del sancionado").setRequiredRange(15, 25);
        if (ticket.accusedUserId() != null) target.setValue(String.valueOf(ticket.accusedUserId()));
        Modal modal = Modal.create(STRIKE_MODAL_PREFIX + ticket.id(), "Agregar strike")
                .addComponents(
                        Label.of("Usuario sancionado", target.build()),
                        label("Cantidad de puntos", "points", TextInputStyle.SHORT, "Normalmente 1", 1, 2, true),
                        label("Motivo", "reason", TextInputStyle.PARAGRAPH, "Norma incumplida y contexto", 5, 1000, true),
                        label("Enlace de evidencia", "strike-evidence", TextInputStyle.PARAGRAPH, "Opcional", 0, 1000, false))
                .build();
        event.replyModal(modal).queue();
    }

    private void addStrikeFromTicket(ModalInteractionEvent event, long ticketId) {
        if (!permissions.isModerator(event.getMember())) {
            event.reply("❌ No tienes permisos de Moderación.").setEphemeral(true).queue();
            return;
        }
        Ticket ticket = tickets.findById(ticketId).orElseThrow(() -> new IllegalStateException("El ticket no existe"));
        long targetId = parseDiscordId(value(event, "target-id"));
        int points = parsePoints(value(event, "points"));
        String reason = value(event, "reason");
        String evidence = optionalValue(event, "strike-evidence");
        long strikeId = strikes.add(ticket.guildId(), targetId, points, reason, evidence, ticket.id(), event.getUser().getIdLong());
        int total = strikes.activePoints(ticket.guildId(), targetId);
        moderation.log(ticket.guildId(), targetId, event.getUser().getIdLong(), "STRIKE", reason, null, ticket.id());
        notifyStrike(event, targetId, strikeId, points, total, reason);
        applyConfiguredThreshold(event.getGuild(), targetId, total, reason, ticket.id(), event.getUser().getIdLong());
        event.reply("⚠️ Strike **#" + strikeId + "** agregado a <@" + targetId + ">. Total activo: **" + total + " puntos**.").queue();
    }

    private void openCloseModal(ButtonInteractionEvent event) {
        Ticket ticket = requireChannelTicket(event.getChannel().getIdLong());
        boolean allowed = permissions.isStaff(event.getMember()) || ticket.openerId() == event.getUser().getIdLong();
        if (!allowed) {
            event.reply("❌ No puedes cerrar este ticket.").setEphemeral(true).queue();
            return;
        }
        Modal modal = Modal.create(CLOSE_MODAL_PREFIX + ticket.id(), "Cerrar ticket")
                .addComponents(label("Motivo del cierre", "close-reason", TextInputStyle.PARAGRAPH,
                        "Describe brevemente cómo terminó el caso", 3, 1000, true))
                .build();
        event.replyModal(modal).queue();
    }

    private void closeTicket(ModalInteractionEvent event, long ticketId) {
        Ticket ticket = tickets.findById(ticketId).orElseThrow(() -> new IllegalStateException("El ticket no existe"));
        boolean allowed = permissions.isStaff(event.getMember()) || ticket.openerId() == event.getUser().getIdLong();
        if (!allowed) {
            event.reply("❌ No puedes cerrar este ticket.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        Path transcript = transcripts.generate(ticket);
        String reason = value(event, "close-reason");
        tickets.close(ticket.id(), event.getUser().getIdLong(), reason, transcript.toString());

        TextChannel log = event.getGuild().getTextChannelById(config.ticketLogChannelId());
        if (log != null) {
            log.sendMessageEmbeds(new EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle("🔒 Ticket cerrado #" + ticket.id())
                            .addField("Tipo", ticket.type().label(), true)
                            .addField("Creado por", "<@" + ticket.openerId() + ">", true)
                            .addField("Cerrado por", event.getUser().getAsMention(), true)
                            .addField("Motivo", truncate(reason, 1000), false)
                            .setTimestamp(OffsetDateTime.now()).build())
                    .addFiles(FileUpload.fromData(transcript.toFile()))
                    .queue();
        }
        event.getHook().editOriginal("✅ Ticket cerrado. La transcripción fue generada.").queue();
        if (event.getChannel() instanceof TextChannel channel) {
            channel.sendMessage("🔒 Ticket cerrado por " + event.getUser().getAsMention() + ". Motivo: " + reason).queue();
            if (config.deleteTicketChannelOnClose()) channel.delete().queueAfter(5, TimeUnit.SECONDS);
        }
    }

    private void openAppealDecisionModal(ButtonInteractionEvent event) {
        if (!permissions.isModerator(event.getMember())) {
            event.reply("❌ Solo Moderación puede resolver apelaciones.").setEphemeral(true).queue();
            return;
        }
        String[] parts = event.getComponentId().split(":");
        String decision = parts[1];
        long ticketId = Long.parseLong(parts[2]);
        Modal modal = Modal.create(APPEAL_DECISION_PREFIX + decision + ":" + ticketId,
                        "Resolver apelación")
                .addComponents(label("Motivo de la decisión", "decision-reason", TextInputStyle.PARAGRAPH,
                        "Explica la decisión que recibirá el usuario", 5, 1500, true))
                .build();
        event.replyModal(modal).queue();
    }

    private void decideAppeal(ModalInteractionEvent event, String decisionAndId) {
        if (!permissions.isModerator(event.getMember())) {
            event.reply("❌ No tienes permisos de Moderación.").setEphemeral(true).queue();
            return;
        }
        String[] parts = decisionAndId.split(":");
        boolean approved = "approve".equals(parts[0]);
        long ticketId = Long.parseLong(parts[1]);
        String reason = value(event, "decision-reason");
        Ticket ticket = tickets.findById(ticketId).orElseThrow(() -> new IllegalStateException("La apelación no existe"));
        if (!"OPEN".equals(ticket.status())) {
            event.reply("⚠️ Esta apelación ya fue resuelta.").setEphemeral(true).queue();
            return;
        }
        tickets.resolveAppeal(ticketId, event.getUser().getIdLong(), approved, reason);
        String result = approved ? "APROBADA ✅" : "RECHAZADA ❌";
        event.getJDA().retrieveUserById(ticket.openerId()).queue(user -> user.openPrivateChannel()
                .flatMap(channel -> channel.sendMessage("⚖️ Tu apelación **#" + ticketId + "** fue **" + result + "**.\n**Motivo:** " + reason))
                .queue(null, ignored -> {}));
        event.reply("Apelación #" + ticketId + " " + result + ". El usuario fue notificado por privado.").queue();
    }

    private void notifyStrike(ModalInteractionEvent event, long targetId, long strikeId, int points, int total, String reason) {
        event.getJDA().retrieveUserById(targetId).queue(user -> user.openPrivateChannel()
                .flatMap(channel -> channel.sendMessage("⚠️ Has recibido un strike en **" + event.getGuild().getName() + "**.\n"
                        + "**Strike:** #" + strikeId + " (" + points + " punto(s))\n"
                        + "**Total activo:** " + total + "\n**Motivo:** " + reason
                        + "\nSi consideras que es un error, usa el panel de apelaciones."))
                .queue(null, ignored -> {}), ignored -> {});
    }

    private void applyConfiguredThreshold(Guild guild, long targetId, int total, String reason,
                                          long ticketId, long moderatorId) {
        if (!config.autoApplyStrikeSanctions() || guild == null) return;
        if (total >= config.strikeBanThreshold()) {
            guild.ban(UserSnowflake.fromId(targetId), 0, TimeUnit.SECONDS)
                    .reason("Umbral de strikes: " + reason)
                    .queue(success -> moderation.log(guild.getIdLong(), targetId, moderatorId, "AUTO_BAN", reason, null, ticketId),
                            ignored -> {});
        } else if (total >= config.strikeMuteThreshold()) {
            guild.retrieveMemberById(targetId).queue(member -> member.timeoutFor(Duration.ofHours(config.strikeMuteHours()))
                    .reason("Umbral de strikes: " + reason)
                    .queue(success -> moderation.log(guild.getIdLong(), targetId, moderatorId, "AUTO_MUTE", reason,
                            Duration.ofHours(config.strikeMuteHours()).toSeconds(), ticketId), ignored -> {}), ignored -> {});
        }
    }

    private Ticket requireChannelTicket(long channelId) {
        return tickets.findByChannel(channelId).orElseThrow(() -> new IllegalStateException("Este canal no pertenece a un ticket activo"));
    }

    private Guild requireGuild(Guild guild) {
        if (guild == null || guild.getIdLong() != config.guildId()) throw new IllegalStateException("Servidor no autorizado");
        return guild;
    }

    private void saveInitialLinks(long ticketId, long authorId, String evidence) {
        Matcher matcher = URL_PATTERN.matcher(evidence == null ? "" : evidence);
        Set<String> links = new LinkedHashSet<>();
        while (matcher.find()) links.add(trimUrl(matcher.group()));
        for (String link : links) {
            tickets.saveAttachment(ticketId, null, authorId, "LINK", null, link, null, null, OffsetDateTime.now());
        }
    }

    private static String value(ModalInteractionEvent event, String id) {
        var value = event.getValue(id);
        if (value == null || value.getAsString().isBlank()) throw new IllegalArgumentException("Falta completar el campo " + id);
        return value.getAsString().trim();
    }

    private static String optionalValue(ModalInteractionEvent event, String id) {
        var value = event.getValue(id);
        return value == null ? "" : value.getAsString().trim();
    }

    private static long parseDiscordId(String input) {
        String digits = input.replaceAll("\\D", "");
        if (digits.length() < 15 || digits.length() > 20) throw new IllegalArgumentException("El ID de Discord no es válido");
        return Long.parseUnsignedLong(digits);
    }

    private static int parsePoints(String input) {
        try {
            int points = Integer.parseInt(input.trim());
            if (points < 1 || points > 10) throw new NumberFormatException();
            return points;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Los puntos deben estar entre 1 y 10");
        }
    }

    private static String channelName(TicketType type, User user) {
        String safe = user.getName().toLowerCase().replaceAll("[^a-z0-9-]", "");
        if (safe.isBlank()) safe = "usuario";
        if (safe.length() > 30) safe = safe.substring(0, 30);
        return type.channelPrefix() + "-" + safe + "-" + user.getId().substring(Math.max(0, user.getId().length() - 4));
    }

    private net.dv8tion.jda.api.entities.MessageEmbed ticketCreatedEmbed(long id, TicketType type, String gameName,
                                                                          String subject, String description,
                                                                          Long accusedId, String evidence) {
        EmbedBuilder embed = new EmbedBuilder().setColor(new Color(230, 30, 40))
                .setTitle("🎟️ Ticket #" + id + " · " + type.label())
                .addField("Nombre en juego", gameName, true)
                .addField("Asunto", subject, true)
                .addField("Descripción", truncate(description, 1000), false);
        if (accusedId != null) embed.addField("Jugador reportado", "<@" + accusedId + "> (`" + accusedId + "`)", false);
        if (!evidence.isBlank()) embed.addField("Pruebas iniciales", truncate(evidence, 1000), false);
        return embed.setFooter("Sube las capturas o videos directamente en este canal").setTimestamp(OffsetDateTime.now()).build();
    }

    private void replyError(ModalInteractionEvent event, RuntimeException exception) {
        String message = "❌ " + safeMessage(exception);
        if (event.isAcknowledged()) event.getHook().editOriginal(message).queue();
        else event.reply(message).setEphemeral(true).queue();
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "Ocurrió un error inesperado" : message;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) return "Sin información";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static String trimUrl(String url) {
        return url.replaceAll("[),.;]+$", "");
    }
}

