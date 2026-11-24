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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
    private final Set<Long> resolvingAppeals = ConcurrentHashMap.newKeySet();

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
            } else if (id.startsWith("appeal:archive-confirm:")) {
                archiveResolvedAppeal(event, true);
            } else if (id.startsWith("appeal:archive:")) {
                archiveResolvedAppeal(event, false);
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
        Category category = guild.getCategoryById(config.categoryIdFor(type));
        Role responsibleRole = guild.getRoleById(config.responsibleRoleIdFor(type));
        Member opener = event.getMember();
        if (category == null || responsibleRole == null || opener == null) {
            event.getHook().editOriginal("❌ La categoría o los roles del bot no están bien configurados.").queue();
            return;
        }

        EnumSet<Permission> userAllowed = EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
                Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS);
        EnumSet<Permission> staffAllowed = EnumSet.copyOf(userAllowed);
        staffAllowed.add(Permission.MESSAGE_MANAGE);
        EnumSet<Permission> botAllowed = EnumSet.copyOf(staffAllowed);
        botAllowed.add(Permission.MANAGE_CHANNEL);
        long ticketId = tickets.create(guild.getIdLong(), null, event.getUser().getIdLong(),
                type, gameName, subject, description, accusedId, evidence, extractLinks(evidence));

        try {
            var action = guild.createTextChannel(type.channelName(ticketId), category)
                    .clearPermissionOverrides()
                    .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                    .addPermissionOverride(opener, userAllowed, null)
                    .addPermissionOverride(responsibleRole, staffAllowed, null)
                    .addPermissionOverride(guild.getSelfMember(), botAllowed, null)
                    .setTopic("Caso #" + ticketId + " · " + type.label() + " · Usuario " + opener.getId());

            Long finalAccusedId = accusedId;
            action.queue(channel -> {
                try {
                    if (!tickets.attachChannel(ticketId, channel.getIdLong())) {
                        throw new IllegalStateException("El caso ya no está disponible para vincular su canal");
                    }
                    channel.sendMessage(event.getUser().getAsMention() + " " + responsibleRole.getAsMention())
                            .setEmbeds(ticketCreatedEmbed(ticketId, type, event.getUser(), gameName, subject,
                                    description, finalAccusedId, evidence))
                            .setComponents(type == TicketType.APPEAL ? panels.appealControls(ticketId) : panels.ticketControls())
                            .queue(message -> {
                                event.getHook().editOriginal("✅ Caso **#" + ticketId + "** creado: " + channel.getAsMention()).queue();
                                try {
                                    logCaseOpened(guild, tickets.findById(ticketId).orElseThrow(), channel);
                                } catch (RuntimeException exception) {
                                    System.err.println("No se pudo registrar la apertura del caso #" + ticketId
                                            + ": " + safeMessage(exception));
                                }
                            },
                                    error -> {
                                        markFailedQuietly(ticketId, "Discord no permitió publicar el mensaje inicial");
                                        channel.delete().reason("No se pudo publicar el mensaje inicial").queue(null, ignored -> {});
                                        event.getHook().editOriginal("❌ No se pudo publicar el ticket: "
                                                + safeMessage(error)).queue();
                                    });
                } catch (RuntimeException exception) {
                    markFailedQuietly(ticketId, "No se pudo completar la creación del caso");
                    channel.delete().reason("Error al guardar el ticket").queue(null, ignored -> {});
                    event.getHook().editOriginal("❌ No se pudo guardar el ticket: " + safeMessage(exception)).queue();
                }
            }, error -> {
                markFailedQuietly(ticketId, "Discord no permitió crear el canal");
                event.getHook().editOriginal("❌ Discord no permitió crear el canal: " + safeMessage(error)
                        + ". Puedes volver a intentarlo.").queue();
            });
        } catch (RuntimeException exception) {
            markFailedQuietly(ticketId, "Discord rechazó la configuración del canal");
            throw exception;
        }
    }

    private void logCaseOpened(Guild guild, Ticket ticket, TextChannel channel) {
        TextChannel log = guild.getTextChannelById(ticket.type() == TicketType.APPEAL
                ? config.appealLogChannelId() : config.ticketLogChannelId());
        if (log == null) {
            System.err.println("No existe el canal de registro para el caso #" + ticket.id());
            return;
        }
        log.sendMessageEmbeds(new EmbedBuilder()
                        .setColor(new Color(88, 101, 242))
                        .setTitle("📂 Apertura de caso #" + ticket.id())
                        .addField("Tipo", ticket.type().label(), true)
                        .addField("Usuario", "<@" + ticket.openerId() + ">", true)
                        .addField("Canal privado", channel.getAsMention(), true)
                        .addField("Asunto", truncate(ticket.subject(), 150), false)
                        .addField("Equipo responsable", "<@&" + config.responsibleRoleIdFor(ticket.type()) + ">", true)
                        .setTimestamp(OffsetDateTime.now())
                        .build())
                .setComponents(ActionRow.of(
                        net.dv8tion.jda.api.components.buttons.Button.link(
                                "https://discord.com/channels/" + guild.getId() + "/" + channel.getId(), "Abrir caso")))
                .queue(null, error -> System.err.println("No se pudo publicar la apertura del caso #"
                        + ticket.id() + ": " + safeMessage(error)));
    }

    private void requestTicketDeletion(ButtonInteractionEvent event) {
        if (!permissions.isStaff(event.getMember())) {
            event.reply("❌ Solo el equipo puede eliminar el ticket.").setEphemeral(true).queue();
            return;
        }
        Ticket ticket = requireChannelTicket(event.getChannel().getIdLong());
        requireOrdinaryTicket(ticket);
        requireTicketManager(event.getMember(), ticket);
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
        requireOrdinaryTicket(ticket);
        requireTicketManager(event.getMember(), ticket);
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
                            hook.editOriginal("❌ No se pudo guardar el cierre del ticket: "
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
        requireOrdinaryTicket(ticket);
        requireTicketManager(event.getMember(), ticket);
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
        requireOrdinaryTicket(ticket);
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
        requireOrdinaryTicket(ticket);
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
                            hook.editOriginal("❌ No se pudo guardar el cierre del ticket: "
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
        uploads.add(submitArchive(() -> log.sendMessageEmbeds(new EmbedBuilder()
                        .setColor(new Color(87, 242, 135))
                        .setTitle(ticket.isResolvedAppeal()
                                ? "⚖️ Caso #" + ticket.id() + " · " + appealResult(ticket)
                                : "Ticket cerrado")
                        .addField("Tipo de caso", ticket.type().label(), true)
                        .addField("🎟️ Ticket ID", String.valueOf(ticket.id()), true)
                        .addField("✅ Abierto por", "<@" + ticket.openerId() + ">", true)
                        .addField("⛔ Cerrado por", "<@" + closedBy + ">", true)
                        .addField("🕘 Fecha de apertura", openedAt, true)
                        .addField("🟣 Reclamado por", claimedBy, true)
                        .addField("❓ Motivo", truncate(reason, 1000), false)
                        .setTimestamp(OffsetDateTime.now())
                        .build())
                .submit()));

        CompletableFuture<Message> transcriptUpload = submitArchive(() -> transcriptLog.sendMessageEmbeds(new EmbedBuilder()
                        .setColor(new Color(87, 242, 135))
                        .setTitle("Copia archivada de transcripción · Caso #" + ticket.id())
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
                }));
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
        Ticket ticket = requireAppeal(event.getGuild(), ticketId);
        requireIndependentReviewer(ticket, event.getUser().getIdLong());
        if (!"OPEN".equals(ticket.status())) {
            event.reply("⚠️ Este caso ya fue resuelto.").setEphemeral(true).queue();
            return;
        }
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
        if (parts.length != 2 || !("approve".equals(parts[0]) || "reject".equals(parts[0]))) {
            throw new IllegalArgumentException("Decisión de apelación no válida");
        }
        boolean approved = "approve".equals(parts[0]);
        long ticketId = Long.parseLong(parts[1]);
        String reason = value(event, "decision-reason");
        Ticket ticket = requireAppeal(event.getGuild(), ticketId);
        requireIndependentReviewer(ticket, event.getUser().getIdLong());
        if (!"OPEN".equals(ticket.status())) {
            event.reply("⚠️ Esta apelación ya fue resuelta.").setEphemeral(true).queue();
            return;
        }
        if (!resolvingAppeals.add(ticketId)) {
            event.reply("⏳ Moderación ya está guardando una resolución para este caso.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue(hook -> {
            final Ticket resolution;
            final Path transcript;
            final List<CompletableFuture<Message>> uploads;
            try {
                Guild guild = requireGuild(event.getGuild());
                if (ticket.channelId() != null) requireTextChannel(guild, ticket.channelId(), "el caso de apelación");
                resolution = ticket.resolvedAppeal(event.getUser().getIdLong(), approved, reason);
                transcript = transcripts.generate(resolution);
                // Guardamos las copias antes de confirmar el estado final en la base de datos.
                requireTextChannel(guild, config.appealLogChannelId(), "resoluciones de apelaciones");
                uploads = uploadManualClosureArchive(guild, resolution, event.getUser().getIdLong(), reason, transcript);
                if (config.appealLogChannelId() != config.ticketLogChannelId()) {
                    uploads.add(submitArchive(() -> guild.getTextChannelById(config.appealLogChannelId())
                            .sendMessageEmbeds(appealResolutionEmbed(resolution)).submit()));
                }
            } catch (RuntimeException exception) {
                resolvingAppeals.remove(ticketId);
                hook.editOriginal("❌ No se resolvió el caso: " + safeMessage(exception)
                        + ". Se conserva abierto para reintentarlo.").queue();
                return;
            }

            CompletableFuture.allOf(uploads.toArray(CompletableFuture[]::new))
                    .whenComplete((unused, uploadError) -> {
                        try {
                            if (uploadError != null) {
                                deleteMessages(successfulMessages(uploads));
                                hook.editOriginal("❌ No se resolvió el caso porque no se pudieron guardar todos los registros: "
                                        + safeMessage(rootCause(uploadError)) + ". Se conserva abierto.").queue();
                                return;
                            }
                            if (!tickets.resolveAppeal(ticketId, event.getUser().getIdLong(), approved, reason,
                                    transcript.toString())) {
                                deleteMessages(successfulMessages(uploads));
                                hook.editOriginal("⚠️ Este caso ya fue resuelto. No se guardó una segunda decisión.").queue();
                                return;
                            }
                        } catch (RuntimeException exception) {
                            deleteMessages(successfulMessages(uploads));
                            hook.editOriginal("❌ No se pudo guardar la resolución: " + safeMessage(exception)
                                    + ". El canal se conserva para reintentarlo.").queue();
                            return;
                        } finally {
                            resolvingAppeals.remove(ticketId);
                        }

                        if (event.getMessage() != null) {
                            event.getMessage().editMessageComponents().queue(null, ignored -> {});
                        }
                        TextChannel channel = ticket.channelId() == null ? null
                                : event.getGuild().getTextChannelById(ticket.channelId());
                        if (channel != null) {
                            submitArchive(() -> channel.sendMessageEmbeds(appealResolutionEmbed(resolution))
                                    .setComponents(panels.resolvedAppealControls(ticketId)).submit())
                                    .whenComplete((message, error) -> {
                                        if (error == null) hook.editOriginal("✅ Caso **#" + ticketId + "** "
                                                    + appealResult(resolution) + ". Resolución publicada y transcripción guardada. "
                                                    + "El canal queda disponible hasta que Moderación lo archive.").queue();
                                        else hook.editOriginal("⚠️ La resolución y la transcripción se guardaron, "
                                                    + "pero no se pudo publicar la respuesta en el canal: " + safeMessage(error)
                                                    + ". Revisa los registros antes de eliminarlo.").queue();
                                    });
                            channel.getManager().putMemberPermissionOverride(ticket.openerId(),
                                            EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY),
                                            EnumSet.of(Permission.MESSAGE_SEND))
                                    .setName((approved ? "aprobada-" : "rechazada-")
                                            + TicketType.APPEAL.channelName(ticketId))
                                    .setTopic("Caso #" + ticketId + " · " + appealResult(resolution))
                                    .queue(null, ignored -> {});
                        } else {
                            hook.editOriginal("✅ Caso **#" + ticketId + "** " + appealResult(resolution)
                                    + ". Resolución y transcripción guardadas.").queue();
                        }
                        event.getJDA().retrieveUserById(ticket.openerId()).submit()
                                .thenCompose(user -> user.openPrivateChannel().submit())
                                .thenCompose(dm -> dm.sendMessage("⚖️ Tu apelación **#" + ticketId + "** fue **"
                                        + appealResult(resolution) + "**.\n**Motivo:** " + reason).submit())
                                .exceptionally(ignored -> null);
                    });
        }, error -> resolvingAppeals.remove(ticketId));
    }

    private void archiveResolvedAppeal(ButtonInteractionEvent event, boolean confirmed) {
        if (!permissions.isModerator(event.getMember())) {
            event.reply("❌ Solo Moderación puede archivar un caso de apelación.").setEphemeral(true).queue();
            return;
        }
        long caseId = Long.parseLong(event.getComponentId().split(":")[2]);
        Ticket ticket = requireAppeal(event.getGuild(), caseId);
        if (!ticket.isResolvedAppeal() || ticket.transcriptPath() == null || ticket.transcriptPath().isBlank()) {
            event.reply("❌ Primero resuelve y guarda la transcripción del caso.").setEphemeral(true).queue();
            return;
        }
        if (ticket.channelId() == null || ticket.channelId() != event.getChannel().getIdLong()) {
            throw new IllegalStateException("Archiva el caso desde su propio canal");
        }
        if (!confirmed) {
            event.reply("¿Eliminar el canal de este caso resuelto? La resolución y su transcripción se conservarán en los registros.")
                    .setEphemeral(true).setComponents(ActionRow.of(
                            net.dv8tion.jda.api.components.buttons.Button.danger(
                                    "appeal:archive-confirm:" + caseId, "Confirmar archivo del canal"))).queue();
            return;
        }
        event.deferEdit().queue(hook -> {
            hook.editOriginal("✅ El canal se eliminará en 5 segundos. El expediente queda guardado.")
                    .setComponents().queue();
            event.getChannel().asTextChannel().delete().reason("Caso de apelación #" + caseId + " resuelto y archivado")
                    .queueAfter(5, TimeUnit.SECONDS, ignored -> {}, error -> hook.editOriginal(
                            "❌ Discord no permitió eliminar el canal: " + safeMessage(error)).queue());
        });
    }

    private Ticket requireAppeal(Guild guild, long caseId) {
        requireGuild(guild);
        Ticket ticket = tickets.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("La apelación no existe"));
        if (ticket.guildId() != guild.getIdLong() || ticket.type() != TicketType.APPEAL) {
            throw new IllegalStateException("El caso no es una apelación de este servidor");
        }
        return ticket;
    }

    private static void requireIndependentReviewer(Ticket ticket, long moderatorId) {
        if (ticket.openerId() == moderatorId) {
            throw new IllegalStateException("Otro moderador debe revisar tu propia apelación");
        }
    }

    private static String appealResult(Ticket ticket) {
        return "APPROVED".equals(ticket.status()) ? "APROBADA ✅" : "RECHAZADA ❌";
    }

    private net.dv8tion.jda.api.entities.MessageEmbed appealResolutionEmbed(Ticket ticket) {
        return new EmbedBuilder()
                .setColor("APPROVED".equals(ticket.status()) ? new Color(87, 242, 135) : new Color(237, 66, 69))
                .setTitle("⚖️ Resolución del caso #" + ticket.id() + " · " + appealResult(ticket))
                .addField("Solicitante", "<@" + ticket.openerId() + ">", true)
                .addField("Resuelto por", "<@" + ticket.closedBy() + ">", true)
                .addField("Sanción o referencia", truncate(ticket.subject(), 150), false)
                .setDescription("**Motivo:**\n" + ticket.closeReason())
                .setFooter("La decisión queda registrada. Moderación debe aplicar cualquier cambio a la sanción correspondiente.")
                .setTimestamp(ticket.closedAt()).build();
    }

    private static void requireOrdinaryTicket(Ticket ticket) {
        if (ticket.type() == TicketType.APPEAL) {
            throw new IllegalStateException("Las apelaciones deben resolverse con Aprobar o Rechazar y un motivo");
        }
    }

    private void requireTicketManager(Member member, Ticket ticket) {
        if (!permissions.canManageTicket(member, ticket)) {
            throw new IllegalStateException("Solo el equipo responsable puede gestionar este caso");
        }
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
        long roleId = config.responsibleRoleIdFor(ticket.type());
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

    private static CompletableFuture<Message> submitArchive(Supplier<CompletableFuture<Message>> upload) {
        try {
            return upload.get();
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
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

    private net.dv8tion.jda.api.entities.MessageEmbed ticketCreatedEmbed(long id, TicketType type, User creator, String gameName,
                                                                          String subject, String description,
                                                                          Long accusedId, String evidence) {
        String emoji = switch (type) {
            case BUG -> "🐛";
            case HELP -> "❓";
            case REPORT -> "🚨";
            case APPEAL -> "⚖️";
        };
        long responsibleRole = config.responsibleRoleIdFor(type);
        EmbedBuilder embed = new EmbedBuilder().setColor(new Color(30, 190, 120))
                .setTitle(emoji + " Caso #" + id + " · " + type.label())
                .setDescription("**" + subject + "**\n\n" + description)
                .addField("👤 Creador", creator.getAsMention(), true)
                .addField("🎮 Nombre en el juego", gameName, true)
                .addField("🛡️ Equipo responsable", "<@&" + responsibleRole + ">", true)
                .addField("📌 Estado", "Abierto · En revisión", true);
        if (accusedId != null) embed.addField("Jugador reportado", "<@" + accusedId + "> (`" + accusedId + "`)", false);
        if (!evidence.isBlank()) embed.addField("Pruebas iniciales", truncate(evidence, 1000), false);
        return embed
                .addField("📌 Qué incluir", "**Explicación detallada, pruebas, capturas y cualquier dato relevante.** "
                        + "Las pruebas deben ser grabadas con Medal o cualquier otra aplicación de grabación.", false)
                .addField(type == TicketType.APPEAL ? "⚖️ Resolución" : "⭐ Al finalizar",
                        type == TicketType.APPEAL
                                ? "Moderación revisará el caso y publicará una decisión con su motivo. Puedes añadir pruebas mientras está abierto."
                                : "El equipo puede enviarte una encuesta. Si la completas, el ticket se cerrará automáticamente.", false)
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
