package com.islaspider.bot.listener;

import com.islaspider.bot.config.BotConfig;
import com.islaspider.bot.db.TicketRepository;
import com.islaspider.bot.model.Ticket;
import com.islaspider.bot.model.TicketType;
import com.islaspider.bot.service.PanelFactory;
import com.islaspider.bot.service.PermissionService;
import com.islaspider.bot.service.TranscriptService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;

import java.awt.Color;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TicketListener extends ListenerAdapter {
    private static final String TICKET_MODAL_PREFIX = "ticket:form:";
    private static final String APPEAL_DECISION_PREFIX = "appeal:decision-form:";
    private static final String FEEDBACK_MODAL_PREFIX = "ticket:feedback-form:";
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    private final BotConfig config;
    private final TicketRepository tickets;
    private final PermissionService permissions;
    private final PanelFactory panels;
    private final TranscriptService transcripts;

    public TicketListener(BotConfig config, TicketRepository tickets, PermissionService permissions,
                          PanelFactory panels, TranscriptService transcripts) {
        this.config = config;
        this.tickets = tickets;
        this.permissions = permissions;
        this.panels = panels;
        this.transcripts = transcripts;
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (event.getValues().isEmpty()) return;
        try {
            if (PanelFactory.CREATE_MENU_ID.equals(event.getComponentId())) {
                TicketType type = TicketType.fromCode(event.getValues().getFirst());
                event.replyModal(buildTicketModal(type)).queue();
            } else if (event.getComponentId().startsWith(PanelFactory.SURVEY_STAFF_PREFIX)) {
                selectSurveyStaff(event);
            }
        } catch (RuntimeException exception) {
            if (!event.isAcknowledged()) event.reply("❌ " + safeMessage(exception)).setEphemeral(true).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        try {
            if (id.startsWith(TICKET_MODAL_PREFIX)) {
                createTicket(event, TicketType.fromCode(id.substring(TICKET_MODAL_PREFIX.length())));
            } else if (id.startsWith(FEEDBACK_MODAL_PREFIX)) {
                submitFeedback(event, id.substring(FEEDBACK_MODAL_PREFIX.length()));
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
            if (PanelFactory.SURVEY_BUTTON_ID.equals(id)) {
                sendSurvey(event);
            } else if (PanelFactory.DELETE_BUTTON_ID.equals(id)) {
                requestTicketDeletion(event);
            } else if (PanelFactory.DELETE_CONFIRM_BUTTON_ID.equals(id)) {
                deleteTicket(event);
            } else if (PanelFactory.DELETE_CANCEL_BUTTON_ID.equals(id)) {
                cancelTicketDeletion(event);
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
                                "Escribe tu nombre dentro del juego", 2, 100, true),
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

        event.deferReply(true).queue();
        if (tickets.hasOpenTicket(guild.getIdLong(), event.getUser().getIdLong(), type)) {
            event.getHook().editOriginal("⚠️ Ya tienes un ticket abierto de esta categoría.").queue();
            return;
        }
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
        Role responsibleRole = type == TicketType.REPORT ? modRole : staffRole;

        var action = guild.createTextChannel(channelName(type, event.getUser()), category)
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(opener, userAllowed, null)
                .addPermissionOverride(responsibleRole, staffAllowed, null);

        Long finalAccusedId = accusedId;
        action.queue(channel -> {
            try {
                long ticketId = tickets.create(guild.getIdLong(), channel.getIdLong(), event.getUser().getIdLong(),
                        type, gameName, subject, description, finalAccusedId, evidence, extractLinks(evidence));
                channel.sendMessage(event.getUser().getAsMention() + " " + responsibleRole.getAsMention())
                        .setEmbeds(ticketCreatedEmbed(ticketId, type, event.getUser(), gameName, subject,
                                description, finalAccusedId, evidence))
                        .setComponents(panels.ticketControls())
                        .queue(message -> event.getHook().editOriginal("✅ Ticket creado: " + channel.getAsMention()).queue(),
                                error -> {
                                    markFailedQuietly(ticketId, "Discord no permitió publicar el mensaje inicial");
                                    channel.delete().reason("No se pudo publicar el mensaje inicial").queue(null, ignored -> {});
                                    event.getHook().editOriginal("❌ No se pudo publicar el ticket: "
                                            + safeMessage(error)).queue();
                                });
            } catch (RuntimeException exception) {
                channel.delete().reason("Error al guardar el ticket").queue(null, ignored -> {});
                event.getHook().editOriginal("❌ No se pudo guardar el ticket: " + safeMessage(exception)).queue();
            }
        }, error -> event.getHook().editOriginal("❌ Discord no permitió crear el canal: " + error.getMessage()).queue());
    }

    private void createAppeal(ModalInteractionEvent event, Guild guild, String gameName, String subject,
                              String description, String evidence) {
        long ticketId = tickets.create(guild.getIdLong(), null, event.getUser().getIdLong(), TicketType.APPEAL,
                gameName, subject, description, null, evidence, extractLinks(evidence));
        TextChannel log = guild.getTextChannelById(config.appealLogChannelId());
        if (log == null) {
            markFailedQuietly(ticketId, "El canal de apelaciones no existe");
            event.getHook().editOriginal("❌ No existe el canal configurado para apelaciones.").queue();
            return;
        }
        log.sendMessageEmbeds(new EmbedBuilder()
                        .setColor(new Color(245, 166, 35))
                        .setTitle("⚖️ Nueva apelación #" + ticketId)
                        .addField("Usuario", event.getUser().getAsMention() + " (`" + event.getUser().getId() + "`)", false)
                        .addField("Nombre en el juego", gameName, true)
                        .addField("Sanción", subject, true)
                        .addField("Explicación", truncate(description, 1000), false)
                        .addField("Pruebas", evidence.isBlank() ? "Sin enlaces" : truncate(evidence, 1000), false)
                        .setTimestamp(OffsetDateTime.now())
                        .build())
                .setComponents(ActionRow.of(
                        net.dv8tion.jda.api.components.buttons.Button.success("appeal:approve:" + ticketId, "Aprobar"),
                        net.dv8tion.jda.api.components.buttons.Button.danger("appeal:reject:" + ticketId, "Rechazar")))
                .queue(message -> {
                    event.getUser().openPrivateChannel()
                            .flatMap(channel -> channel.sendMessage("⚖️ Recibimos tu apelación **#" + ticketId
                                    + "**. Te responderemos por este medio."))
                            .queue(null, ignored -> {});
                    event.getHook().editOriginal("✅ Apelación #" + ticketId
                            + " enviada. La respuesta llegará por mensaje privado.").queue();
                }, error -> {
                    markFailedQuietly(ticketId, "Discord no permitió publicar la apelación");
                    event.getHook().editOriginal("❌ No se pudo publicar la apelación: "
                            + safeMessage(error) + ". Puedes volver a intentarlo.").queue();
                });
    }

    private void requestTicketDeletion(ButtonInteractionEvent event) {
        if (!permissions.isStaff(event.getMember())) {
            event.reply("❌ Solo el equipo puede eliminar el ticket.").setEphemeral(true).queue();
            return;
        }
        Ticket ticket = requireChannelTicket(event.getChannel().getIdLong());
        if (!"OPEN".equals(ticket.status())) {
            event.reply("⚠️ Este ticket ya está cerrado.").setEphemeral(true).queue();
            return;
        }
        event.reply("⚠️ ¿Confirmas que deseas archivar y eliminar este ticket? La encuesta es opcional.")
                .setEphemeral(true)
                .setComponents(ActionRow.of(
                        net.dv8tion.jda.api.components.buttons.Button.danger(
                                PanelFactory.DELETE_CONFIRM_BUTTON_ID, "Sí, eliminar"),
                        net.dv8tion.jda.api.components.buttons.Button.secondary(
                                PanelFactory.DELETE_CANCEL_BUTTON_ID, "Cancelar")))
                .queue();
    }

    private void cancelTicketDeletion(ButtonInteractionEvent event) {
        if (!permissions.isStaff(event.getMember())) {
            event.reply("❌ Solo el equipo puede gestionar el ticket.").setEphemeral(true).queue();
            return;
        }
        event.editMessage("✅ Eliminación cancelada.").setComponents().queue();
    }

    private void deleteTicket(ButtonInteractionEvent event) {
        if (!permissions.isStaff(event.getMember())) {
            event.reply("❌ Solo el equipo puede eliminar el ticket.").setEphemeral(true).queue();
            return;
        }
        Ticket ticket = requireChannelTicket(event.getChannel().getIdLong());
        if (!"OPEN".equals(ticket.status())) {
            event.reply("⚠️ Este ticket ya está cerrado.").setEphemeral(true).queue();
            return;
        }
        Guild guild = requireGuild(event.getGuild());
        event.deferReply(true).queue(hook -> {
            String reason = "Cierre manual por el equipo";
            final Path transcript;
            final List<CompletableFuture<Message>> uploads;
            try {
                transcript = transcripts.generate(ticket);
                uploads = uploadManualClosureArchive(
                        guild, ticket, event.getUser().getIdLong(), reason, transcript);
            } catch (RuntimeException exception) {
                hook.editOriginal("❌ No se eliminó el ticket: " + safeMessage(exception)
                        + ". El canal se conservará para reintentarlo.").queue();
                return;
            }

            CompletableFuture.allOf(uploads.toArray(CompletableFuture[]::new))
                    .whenComplete((unused, uploadError) -> {
                        List<Message> archivedMessages = successfulMessages(uploads);
                        if (uploadError != null) {
                            deleteMessages(archivedMessages);
                            hook.editOriginal("❌ No se eliminó el ticket porque no fue posible guardar todos los logs: "
                                    + safeMessage(rootCause(uploadError))
                                    + ". El canal se conservará para reintentarlo.").queue();
                            return;
                        }
                        try {
                            boolean closed = tickets.close(ticket.id(), event.getUser().getIdLong(),
                                    reason, transcript.toString());
                            if (!closed) {
                                deleteMessages(archivedMessages);
                                hook.editOriginal("⚠️ Este ticket ya fue cerrado.").queue();
                                return;
                            }
                        } catch (RuntimeException exception) {
                            deleteMessages(archivedMessages);
                            hook.editOriginal("❌ PostgreSQL no permitió cerrar el ticket: "
                                    + safeMessage(exception)
                                    + ". El canal se conservará para reintentarlo.").queue();
                            return;
                        }

                        hook.editOriginal("✅ Ticket archivado y cerrado correctamente.").queue();
                        if (event.getChannel() instanceof TextChannel channel) {
                            channel.sendMessage("🗑️ Ticket cerrado manualmente por "
                                            + event.getUser().getAsMention() + ".")
                                    .queue(null, ignored -> {});
                            if (config.deleteTicketChannelOnClose()) {
                                channel.delete().reason("Ticket cerrado manualmente")
                                        .queueAfter(5, TimeUnit.SECONDS, ignored -> {},
                                                error -> hook.editOriginal(
                                                        "✅ El ticket fue archivado, pero Discord no permitió eliminar el canal: "
                                                                + safeMessage(error)).queue());
                            }
                        }
                    });
        });
    }

    private void sendSurvey(ButtonInteractionEvent event) {
        if (!permissions.isStaff(event.getMember())) {
            event.reply("❌ Solo el equipo puede enviar la encuesta.").setEphemeral(true).queue();
            return;
        }
        Ticket ticket = requireChannelTicket(event.getChannel().getIdLong());
        if (!"OPEN".equals(ticket.status())) {
            event.reply("⚠️ Este ticket ya está cerrado.").setEphemeral(true).queue();
            return;
        }
        Guild guild = requireGuild(event.getGuild());
        event.deferReply().queue(hook -> guild.findMembers(member -> canAttend(ticket, member))
                .onSuccess(members -> {
                    List<Member> staffMembers = members.stream()
                            .filter(member -> !member.getUser().isBot())
                            .sorted(Comparator.comparing(Member::getEffectiveName, String.CASE_INSENSITIVE_ORDER))
                            .limit(125)
                            .toList();
                    if (staffMembers.isEmpty()) {
                        hook.editOriginal("❌ No hay administradores disponibles para esta categoría.").queue();
                        return;
                    }
                    String suffix = members.size() > 125
                            ? " Se muestran los primeros 125 miembros del equipo."
                            : "";
                    hook.editOriginal("⭐ <@" + ticket.openerId()
                                    + ">, selecciona el administrador que te atendió. "
                                    + "Al enviar la valoración, el ticket se cerrará automáticamente." + suffix)
                            .setComponents(panels.surveyStaffMenus(ticket.id(), staffMembers))
                            .queue();
                })
                .onError(error -> hook.editOriginal("❌ No se pudo cargar la lista del equipo: "
                        + safeMessage(error)).queue()));
    }

    private void selectSurveyStaff(StringSelectInteractionEvent event) {
        String selector = event.getComponentId().substring(PanelFactory.SURVEY_STAFF_PREFIX.length());
        long ticketId = Long.parseLong(selector.split(":", 2)[0]);
        long staffId = Long.parseUnsignedLong(event.getValues().getFirst());
        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new IllegalStateException("El ticket no existe"));
        if (ticket.openerId() != event.getUser().getIdLong()) {
            event.reply("❌ Solo quien abrió el ticket puede seleccionar al administrador.")
                    .setEphemeral(true).queue();
            return;
        }
        if (!"OPEN".equals(ticket.status())) {
            event.reply("⚠️ Este ticket ya está cerrado.").setEphemeral(true).queue();
            return;
        }
        Guild guild = requireGuild(event.getGuild());
        Member selectedStaff = guild.getMemberById(staffId);
        if (selectedStaff != null && !canAttend(ticket, selectedStaff)) {
            event.reply("❌ El administrador seleccionado ya no pertenece al equipo.")
                    .setEphemeral(true).queue();
            return;
        }
        Modal modal = Modal.create(FEEDBACK_MODAL_PREFIX + ticket.id() + ":" + staffId,
                        "Valorar atención del ticket")
                .addComponents(
                        label("Atención (1 a 10)", "attention", TextInputStyle.SHORT,
                                "Ejemplo: 10", 1, 2, true),
                        label("Solución (1 a 10)", "solution", TextInputStyle.SHORT,
                                "Ejemplo: 10", 1, 2, true),
                        label("Valoración", "comment", TextInputStyle.PARAGRAPH,
                                "Cuéntanos cómo fue la atención recibida", 3, 1000, true))
                .build();
        event.replyModal(modal).queue();
    }

    private void submitFeedback(ModalInteractionEvent event, String ticketAndStaff) {
        String[] parts = ticketAndStaff.split(":");
        if (parts.length != 2) throw new IllegalArgumentException("Encuesta no válida");
        long ticketId = Long.parseLong(parts[0]);
        long staffId = Long.parseUnsignedLong(parts[1]);
        int attention = parseRating(value(event, "attention"));
        int solution = parseRating(value(event, "solution"));
        String comment = value(event, "comment");
        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new IllegalStateException("El ticket no existe"));
        if (ticket.openerId() != event.getUser().getIdLong()) {
            event.reply("❌ Solo quien abrió el ticket puede responder la encuesta.").setEphemeral(true).queue();
            return;
        }
        if (!"OPEN".equals(ticket.status())) {
            event.reply("⚠️ Este ticket ya está cerrado.").setEphemeral(true).queue();
            return;
        }
        Guild guild = requireGuild(event.getGuild());
        Member selectedStaff = guild.getMemberById(staffId);
        if (selectedStaff != null && !canAttend(ticket, selectedStaff)) {
            event.reply("❌ El administrador seleccionado ya no pertenece al equipo.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue(hook -> {
            String reason = "Cierre automático después de completar la valoración";
            final Path transcript;
            final List<CompletableFuture<Message>> uploads;
            try {
                transcript = transcripts.generate(ticket);
                uploads = uploadClosureArchive(guild, ticket, staffId, attention, solution, comment,
                        reason, transcript);
            } catch (RuntimeException exception) {
                hook.editOriginal("❌ No se cerró el ticket: " + safeMessage(exception)
                        + ". Puedes volver a enviar la encuesta.").queue();
                return;
            }

            CompletableFuture.allOf(uploads.toArray(CompletableFuture[]::new))
                    .whenComplete((unused, uploadError) -> {
                        List<Message> archivedMessages = successfulMessages(uploads);
                        if (uploadError != null) {
                            deleteMessages(archivedMessages);
                            hook.editOriginal("❌ No se cerró el ticket porque no fue posible guardar todos los logs: "
                                    + safeMessage(rootCause(uploadError)) + ". El canal se conservará para reintentarlo.").queue();
                            return;
                        }
                        try {
                            boolean completed = tickets.completeFeedbackAndClose(ticket.id(), event.getUser().getIdLong(),
                                    staffId, attention, solution, comment, reason, transcript.toString());
                            if (!completed) {
                                deleteMessages(archivedMessages);
                                hook.editOriginal("⚠️ Este ticket ya fue cerrado o la encuesta ya fue procesada.").queue();
                                return;
                            }
                        } catch (RuntimeException exception) {
                            deleteMessages(archivedMessages);
                            hook.editOriginal("❌ PostgreSQL no permitió cerrar el ticket: "
                                    + safeMessage(exception) + ". El canal se conservará para reintentarlo.").queue();
                            return;
                        }

                        hook.editOriginal("✅ Gracias por tu valoración. El ticket fue archivado correctamente.").queue();
                        if (event.getChannel() instanceof TextChannel channel) {
                            channel.sendMessage("⭐ Encuesta completada por <@" + ticket.openerId()
                                    + ">. El ticket quedó archivado.").queue(null, ignored -> {});
                            if (config.deleteTicketChannelOnClose()) {
                                channel.delete().reason("Ticket archivado correctamente")
                                        .queueAfter(5, TimeUnit.SECONDS, ignored -> { },
                                                error -> hook.editOriginal("✅ El ticket fue archivado, pero Discord no permitió eliminar el canal: "
                                                        + safeMessage(error)).queue());
                            }
                        }
                    });
        });
    }

    private List<CompletableFuture<Message>> uploadClosureArchive(Guild guild, Ticket ticket, long attendedBy,
                                                                   int attention, int solution, String comment,
                                                                   String reason, Path transcript) {
        TextChannel ratings = requireTextChannel(guild, config.ticketRatingChannelId(), "valoraciones");
        List<CompletableFuture<Message>> uploads = new ArrayList<>();
        uploads.add(ratings.sendMessageEmbeds(new EmbedBuilder()
                        .setColor(new Color(255, 193, 7))
                        .setTitle("⭐ Nueva valoración")
                        .addField("Usuario", "<@" + ticket.openerId() + ">", true)
                        .addField("Atención", attention + "/10", true)
                        .addField("Solución", solution + "/10", true)
                        .addField("Valoración", truncate(comment, 1000), false)
                        .addField("Atendido por", "<@" + attendedBy + ">", false)
                        .setFooter("Ticket #" + ticket.id())
                        .setTimestamp(OffsetDateTime.now())
                        .build())
                .submit());
        uploads.addAll(uploadManualClosureArchive(guild, ticket, attendedBy, reason, transcript));
        return uploads;
    }

    private List<CompletableFuture<Message>> uploadManualClosureArchive(
            Guild guild, Ticket ticket, long closedBy, String reason, Path transcript) {
        TextChannel log = requireTextChannel(guild, config.ticketLogChannelId(), "logs de tickets");
        TextChannel transcriptLog = requireTextChannel(
                guild, config.ticketTranscriptChannelId(), "transcripciones");
        String openedAt = "<t:" + ticket.createdAt().toEpochSecond() + ":F>";
        String claimedBy = ticket.claimedBy() == null
                ? "No reclamado"
                : "<@" + ticket.claimedBy() + ">";

        List<CompletableFuture<Message>> uploads = new ArrayList<>();
        uploads.add(log.sendMessageEmbeds(new EmbedBuilder()
                        .setColor(new Color(87, 242, 135))
                        .setTitle("Ticket cerrado")
                        .addField("🎟️ Ticket ID", String.valueOf(ticket.id()), true)
                        .addField("✅ Abierto por", "<@" + ticket.openerId() + ">", true)
                        .addField("⛔ Cerrado por", "<@" + closedBy + ">", true)
                        .addField("🕘 Fecha de apertura", openedAt, true)
                        .addField("🟣 Reclamado por", claimedBy, true)
                        .addField("❓ Motivo", truncate(reason, 1000), false)
                        .setTimestamp(OffsetDateTime.now())
                        .build())
                .submit());

        CompletableFuture<Message> transcriptUpload = transcriptLog.sendMessageEmbeds(new EmbedBuilder()
                        .setColor(new Color(87, 242, 135))
                        .setTitle("Copia archivada de transcripción")
                        .addField("🎟️ Ticket ID", String.valueOf(ticket.id()), true)
                        .addField("✅ Abierto por", "<@" + ticket.openerId() + ">", true)
                        .addField("⛔ Generada por", "<@" + closedBy + ">", true)
                        .addField("🕘 Fecha de apertura", openedAt, true)
                        .addField("🟣 Reclamado por", claimedBy, true)
                        .addField("❓ Motivo", truncate(reason, 1000), false)
                        .setTimestamp(OffsetDateTime.now())
                        .build())
                .addFiles(FileUpload.fromData(transcript.toFile()))
                .submit()
                .thenCompose(message -> {
                    if (message.getAttachments().isEmpty()) {
                        message.delete().queue(null, ignored -> {});
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("Discord no devolvió el archivo de transcripción"));
                    }
                    String transcriptUrl = message.getAttachments().getFirst().getUrl();
                    return message.editMessageComponents(ActionRow.of(
                                    net.dv8tion.jda.api.components.buttons.Button.link(
                                            transcriptUrl, "Ver transcripción")))
                            .submit()
                            .exceptionally(ignored -> message);
                });
        uploads.add(transcriptUpload);
        return uploads;
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
        event.deferReply(true).queue(hook -> {
            try {
                Ticket ticket = tickets.findById(ticketId)
                        .orElseThrow(() -> new IllegalStateException("La apelación no existe"));
                if (!"OPEN".equals(ticket.status())
                        || !tickets.resolveAppeal(ticketId, event.getUser().getIdLong(), approved, reason)) {
                    hook.editOriginal("⚠️ Esta apelación ya fue resuelta.").queue();
                    return;
                }
                String result = approved ? "APROBADA ✅" : "RECHAZADA ❌";
                event.getJDA().retrieveUserById(ticket.openerId()).submit()
                        .thenCompose(user -> user.openPrivateChannel().submit())
                        .thenCompose(channel -> channel.sendMessage("⚖️ Tu apelación **#" + ticketId
                                + "** fue **" + result + "**.\n**Motivo:** " + reason).submit())
                        .whenComplete((message, dmError) -> {
                            if (dmError == null) {
                                hook.editOriginal("✅ Apelación #" + ticketId + " " + result
                                        + ". El usuario fue notificado por privado.").queue();
                            } else {
                                hook.editOriginal("✅ Apelación #" + ticketId + " " + result
                                        + ", pero el usuario tiene los mensajes privados cerrados.").queue();
                            }
                        });
            } catch (RuntimeException exception) {
                hook.editOriginal("❌ " + safeMessage(exception)).queue();
            }
        });
    }

    private Ticket requireChannelTicket(long channelId) {
        return tickets.findByChannel(channelId).orElseThrow(() -> new IllegalStateException("Este canal no pertenece a un ticket activo"));
    }

    private Guild requireGuild(Guild guild) {
        if (guild == null || guild.getIdLong() != config.guildId()) throw new IllegalStateException("Servidor no autorizado");
        return guild;
    }

    private static List<String> extractLinks(String evidence) {
        Matcher matcher = URL_PATTERN.matcher(evidence == null ? "" : evidence);
        Set<String> links = new LinkedHashSet<>();
        while (matcher.find()) links.add(trimUrl(matcher.group()));
        return List.copyOf(links);
    }

    private boolean canAttend(Ticket ticket, Member member) {
        if (member == null || member.getUser().isBot()) return false;
        if (member.hasPermission(Permission.ADMINISTRATOR)) return true;
        long roleId = ticket.type() == TicketType.REPORT
                ? config.moderatorRoleId()
                : config.staffRoleId();
        return member.getRoles().stream().anyMatch(role -> role.getIdLong() == roleId);
    }

    private static TextChannel requireTextChannel(Guild guild, long channelId, String purpose) {
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) throw new IllegalStateException("No existe el canal configurado para " + purpose);
        return channel;
    }

    private static List<Message> successfulMessages(List<CompletableFuture<Message>> uploads) {
        List<Message> messages = new ArrayList<>();
        for (CompletableFuture<Message> upload : uploads) {
            if (upload.isDone() && !upload.isCompletedExceptionally() && !upload.isCancelled()) {
                Message message = upload.getNow(null);
                if (message != null) messages.add(message);
            }
        }
        return messages;
    }

    private static void deleteMessages(List<Message> messages) {
        for (Message message : messages) message.delete().queue(null, ignored -> {});
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current.getClass().getSimpleName().equals("ExecutionException"))
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void markFailedQuietly(long ticketId, String reason) {
        try {
            tickets.failOpen(ticketId, reason);
        } catch (RuntimeException ignored) {
            // Conservamos el error original que inició la limpieza.
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

    private static int parseRating(String input) {
        try {
            int rating = Integer.parseInt(input.trim());
            if (rating < 1 || rating > 10) throw new NumberFormatException();
            return rating;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Las valoraciones deben estar entre 1 y 10");
        }
    }

    private static String channelName(TicketType type, User user) {
        String safe = user.getName().toLowerCase().replaceAll("[^a-z0-9-]", "");
        if (safe.isBlank()) safe = "usuario";
        if (safe.length() > 30) safe = safe.substring(0, 30);
        return type.channelPrefix() + "-" + safe + "-" + user.getId().substring(Math.max(0, user.getId().length() - 4));
    }

    private net.dv8tion.jda.api.entities.MessageEmbed ticketCreatedEmbed(long id, TicketType type, User creator, String gameName,
                                                                          String subject, String description,
                                                                          Long accusedId, String evidence) {
        String emoji = switch (type) {
            case BUG -> "🐛";
            case HELP -> "❓";
            case REPORT -> "🚨";
            case APPEAL -> "⚖️";
        };
        long responsibleRole = type == TicketType.REPORT ? config.moderatorRoleId() : config.staffRoleId();
        EmbedBuilder embed = new EmbedBuilder().setColor(new Color(30, 190, 120))
                .setTitle(emoji + " Ticket: " + type.label())
                .setDescription("Tu solicitud ha sido recibida. Describe el caso con el mayor detalle posible para que podamos ayudarte más rápido.")
                .addField("👤 Creador", creator.getAsMention(), true)
                .addField("🎮 Nombre en el juego", gameName, true)
                .addField("🛡️ Equipo responsable", "<@&" + responsibleRole + ">", true)
                .addField("📝 Qué ha sucedido", "**" + truncate(subject, 140) + "**\n" + truncate(description, 850), false);
        if (accusedId != null) embed.addField("Jugador reportado", "<@" + accusedId + "> (`" + accusedId + "`)", false);
        if (!evidence.isBlank()) embed.addField("Pruebas iniciales", truncate(evidence, 1000), false);
        return embed
                .addField("📌 Qué incluir", "**Explicación detallada, pruebas, capturas y cualquier dato relevante.** "
                        + "Las pruebas deben ser grabadas con Medal o cualquier otra aplicación de grabación.", false)
                .addField("⭐ Al finalizar", "El equipo puede enviarte una encuesta. Si la completas, el ticket se cerrará automáticamente.", false)
                .setFooter("Ticket #" + id + " · No menciones al equipo repetidamente; te atenderán lo antes posible.")
                .setTimestamp(OffsetDateTime.now()).build();
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
