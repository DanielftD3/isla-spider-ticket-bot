package com.islaspider.bot.listener;

import com.islaspider.bot.config.BotConfig;
import com.islaspider.bot.db.ModerationRepository;
import com.islaspider.bot.db.StrikeRepository;
import com.islaspider.bot.model.SanctionedUser;
import com.islaspider.bot.model.Strike;
import com.islaspider.bot.service.PanelFactory;
import com.islaspider.bot.service.PermissionService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ControlPanelListener extends ListenerAdapter {
    private static final String MODAL_PREFIX = "moderation:";

    private final BotConfig config;
    private final StrikeRepository strikes;
    private final ModerationRepository moderation;
    private final PermissionService permissions;
    private final PanelFactory panels;

    public ControlPanelListener(BotConfig config, StrikeRepository strikes, ModerationRepository moderation,
                                PermissionService permissions, PanelFactory panels) {
        this.config = config;
        this.strikes = strikes;
        this.moderation = moderation;
        this.permissions = permissions;
        this.panels = panels;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"setup".equals(event.getName()) || !isConfiguredGuild(event.getGuild())) return;
        if (!isAdministrator(event.getMember())) {
            event.reply("❌ Solo un administrador puede usar el asistente.").setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(panels.setupPanel())
                .setComponents(panels.setupControls())
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!isConfiguredGuild(event.getGuild())) return;
        try {
            switch (event.getComponentId()) {
                case PanelFactory.SETUP_TICKETS_BUTTON_ID -> publishTicketPanel(event);
                case PanelFactory.SETUP_MODERATION_BUTTON_ID -> publishModerationPanel(event);
                case PanelFactory.MODERATION_REFRESH_BUTTON_ID -> refreshModerationPanel(event);
                case PanelFactory.MODERATION_USERS_BUTTON_ID -> showSanctionedUsers(event);
                default -> { }
            }
        } catch (RuntimeException exception) {
            replyError(event, exception);
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!PanelFactory.MODERATION_MENU_ID.equals(event.getComponentId())
                || event.getValues().isEmpty() || !isConfiguredGuild(event.getGuild())) return;
        if (!permissions.isModerator(event.getMember())) {
            event.reply("❌ Solo Moderación puede usar este panel.").setEphemeral(true).queue();
            return;
        }
        String action = event.getValues().getFirst();
        if ("role-users".equals(action)) {
            showModeratorRoleUsers(event);
            return;
        }
        event.replyModal(buildModerationModal(action)).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith(MODAL_PREFIX) || !isConfiguredGuild(event.getGuild())) return;
        if (!permissions.isModerator(event.getMember())) {
            event.reply("❌ Solo Moderación puede realizar esta acción.").setEphemeral(true).queue();
            return;
        }
        try {
            switch (event.getModalId().substring(MODAL_PREFIX.length())) {
                case "add" -> addWarn(event);
                case "remove" -> removeWarn(event);
                case "review" -> reviewWarns(event);
                case "warn" -> sendInformativeWarn(event);
                case "mute" -> mute(event);
                case "kick" -> kick(event);
                case "ban" -> ban(event);
                default -> throw new IllegalArgumentException("Acción de moderación desconocida");
            }
        } catch (RuntimeException exception) {
            replyError(event, exception);
        }
    }

    private void publishTicketPanel(ButtonInteractionEvent event) {
        requireAdministrator(event.getMember());
        event.deferReply(true).queue();
        event.getChannel().sendMessageEmbeds(panels.ticketPanel())
                .setComponents(panels.ticketMenu())
                .addFiles(ticketPanelImage())
                .queue(message -> event.getHook().editOriginal("✅ Panel de tickets publicado.").queue(),
                        error -> event.getHook().editOriginal("❌ No se pudo publicar: " + error.getMessage()).queue());
    }

    private FileUpload ticketPanelImage() {
        try (InputStream input = ControlPanelListener.class
                .getResourceAsStream("/assets/isla-spider-panel.jpg.b64")) {
            if (input == null) throw new IllegalStateException("No se encontró la imagen del panel");
            String encoded = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
            byte[] image = Base64.getMimeDecoder().decode(encoded);
            return FileUpload.fromData(image, PanelFactory.PANEL_IMAGE_NAME);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo cargar la imagen del panel", exception);
        }
    }

    private void publishModerationPanel(ButtonInteractionEvent event) {
        requireAdministrator(event.getMember());
        event.deferReply(true).queue();
        event.getChannel().sendMessageEmbeds(currentModerationPanel())
                .setComponents(List.of(panels.moderationMenu(), panels.moderationControls()))
                .queue(message -> event.getHook().editOriginal("✅ Panel de moderación publicado.").queue(),
                        error -> event.getHook().editOriginal("❌ No se pudo publicar: " + error.getMessage()).queue());
    }

    private void refreshModerationPanel(ButtonInteractionEvent event) {
        requireModerator(event.getMember());
        event.editMessageEmbeds(currentModerationPanel())
                .setComponents(List.of(panels.moderationMenu(), panels.moderationControls()))
                .queue();
    }

    private net.dv8tion.jda.api.entities.MessageEmbed currentModerationPanel() {
        return panels.moderationPanel(strikes.countActive(config.guildId()),
                strikes.countSanctionedUsers(config.guildId()));
    }

    private Modal buildModerationModal(String action) {
        return switch (action) {
            case "add" -> Modal.create(MODAL_PREFIX + action, "Añadir Warn")
                    .addComponents(
                            field("ID del usuario", "target-id", TextInputStyle.SHORT,
                                    "Copia el ID de Discord", 15, 25, true),
                            field("Puntos", "points", TextInputStyle.SHORT,
                                    "Normalmente 1", 1, 2, true),
                            field("Motivo", "reason", TextInputStyle.PARAGRAPH,
                                    "Norma incumplida y contexto", 5, 1000, true),
                            field("Evidencia", "evidence", TextInputStyle.PARAGRAPH,
                                    "Enlace opcional", 0, 1000, false))
                    .build();
            case "remove" -> Modal.create(MODAL_PREFIX + action, "Quitar Warn")
                    .addComponents(
                            field("ID del warn", "strike-id", TextInputStyle.SHORT,
                                    "Ejemplo: 24", 1, 20, true),
                            field("Motivo del retiro", "reason", TextInputStyle.PARAGRAPH,
                                    "Explica por qué se retira", 5, 1000, true))
                    .build();
            case "review" -> Modal.create(MODAL_PREFIX + action, "Revisar Warns")
                    .addComponents(field("ID del usuario", "target-id", TextInputStyle.SHORT,
                            "Copia el ID de Discord", 15, 25, true))
                    .build();
            case "warn" -> Modal.create(MODAL_PREFIX + action, "Advertir sin puntos")
                    .addComponents(
                            field("ID del usuario", "target-id", TextInputStyle.SHORT,
                                    "Copia el ID de Discord", 15, 25, true),
                            field("Motivo", "reason", TextInputStyle.PARAGRAPH,
                                    "Contenido de la advertencia", 5, 1000, true))
                    .build();
            case "mute" -> Modal.create(MODAL_PREFIX + action, "Aplicar Mute")
                    .addComponents(
                            field("ID del usuario", "target-id", TextInputStyle.SHORT,
                                    "Copia el ID de Discord", 15, 25, true),
                            field("Duración en minutos", "minutes", TextInputStyle.SHORT,
                                    "Entre 1 y 40320", 1, 5, true),
                            field("Motivo", "reason", TextInputStyle.PARAGRAPH,
                                    "Motivo del mute", 5, 1000, true))
                    .build();
            case "kick" -> sanctionModal(action, "Expulsar usuario", "Motivo de la expulsión");
            case "ban" -> sanctionModal(action, "Banear usuario", "Motivo del ban");
            default -> throw new IllegalArgumentException("Acción de moderación desconocida");
        };
    }

    private Modal sanctionModal(String action, String title, String reasonPlaceholder) {
        return Modal.create(MODAL_PREFIX + action, title)
                .addComponents(
                        field("ID del usuario", "target-id", TextInputStyle.SHORT,
                                "Copia el ID de Discord", 15, 25, true),
                        field("Motivo", "reason", TextInputStyle.PARAGRAPH,
                                reasonPlaceholder, 5, 1000, true))
                .build();
    }

    private Label field(String title, String id, TextInputStyle style, String placeholder,
                        int min, int max, boolean required) {
        TextInput.Builder input = TextInput.create(id, style)
                .setPlaceholder(placeholder)
                .setMaxLength(max)
                .setRequired(required);
        if (min > 0) input.setMinLength(min);
        return Label.of(title, input.build());
    }

    private void addWarn(ModalInteractionEvent event) {
        Guild guild = event.getGuild();
        long targetId = parseDiscordId(value(event, "target-id"));
        int points = parseInteger(value(event, "points"), 1, 10, "Los puntos deben estar entre 1 y 10");
        String reason = value(event, "reason");
        String evidence = optionalValue(event, "evidence");

        long strikeId = strikes.add(guild.getIdLong(), targetId, points, reason, evidence,
                null, event.getUser().getIdLong());
        int total = strikes.activePoints(guild.getIdLong(), targetId);
        moderation.log(guild.getIdLong(), targetId, event.getUser().getIdLong(),
                "STRIKE", reason, null, null);
        notifyUser(event, targetId, "⚠️ Has recibido el warn **#" + strikeId + "** en **" + guild.getName()
                + "**.\n**Puntos:** " + points + "\n**Total activo:** " + total + "\n**Motivo:** " + reason
                + "\nPuedes apelar desde el panel de soporte.");
        applyConfiguredThreshold(guild, targetId, total, reason, event.getUser().getIdLong());
        event.reply("✅ Warn **#" + strikeId + "** añadido a <@" + targetId
                + ">. Total activo: **" + total + " punto(s)**.").setEphemeral(true).queue();
    }

    private void removeWarn(ModalInteractionEvent event) {
        long strikeId = parseLong(value(event, "strike-id"), "El ID del warn no es válido");
        String reason = value(event, "reason");
        boolean removed = strikes.revoke(event.getGuild().getIdLong(), strikeId,
                event.getUser().getIdLong(), reason);
        event.reply(removed ? "✅ Warn #" + strikeId + " retirado."
                : "⚠️ Ese warn no existe o ya estaba retirado.").setEphemeral(true).queue();
    }

    private void reviewWarns(ModalInteractionEvent event) {
        long targetId = parseDiscordId(value(event, "target-id"));
        List<Strike> active = strikes.listActive(event.getGuild().getIdLong(), targetId);
        int total = active.stream().mapToInt(Strike::points).sum();
        StringBuilder description = new StringBuilder();
        for (Strike strike : active) {
            description.append("**#").append(strike.id()).append(" · ")
                    .append(strike.points()).append(" punto(s)**\n")
                    .append(strike.reason()).append("\nModerador: <@")
                    .append(strike.moderatorId()).append(">");
            if (strike.sourceTicketId() != null) description.append(" · Ticket #").append(strike.sourceTicketId());
            if (strike.evidenceUrl() != null && !strike.evidenceUrl().isBlank()) {
                description.append("\n[Evidencia](").append(strike.evidenceUrl()).append(")");
            }
            description.append("\n\n");
        }
        if (description.isEmpty()) description.append("Este usuario no tiene warns activos.");
        event.replyEmbeds(new EmbedBuilder()
                        .setColor(new Color(245, 166, 35))
                        .setTitle("Warns activos de " + targetId)
                        .setDescription(truncate(description.toString(), 4000))
                        .setFooter("Total activo: " + total + " punto(s)")
                        .build())
                .setEphemeral(true).queue();
    }

    private void sendInformativeWarn(ModalInteractionEvent event) {
        long targetId = parseDiscordId(value(event, "target-id"));
        String reason = value(event, "reason");
        Guild guild = event.getGuild();
        moderation.log(guild.getIdLong(), targetId, event.getUser().getIdLong(),
                "WARN", reason, null, null);
        notifyUser(event, targetId, "📢 Has recibido una advertencia en **" + guild.getName()
                + "**.\n**Motivo:** " + reason + "\nEsta advertencia no suma puntos.");
        event.reply("📢 Advertencia enviada a <@" + targetId + ">. No suma puntos.")
                .setEphemeral(true).queue();
    }

    private void mute(ModalInteractionEvent event) {
        long targetId = parseDiscordId(value(event, "target-id"));
        int minutes = parseInteger(value(event, "minutes"), 1, 40320,
                "El mute debe durar entre 1 y 40320 minutos");
        String reason = value(event, "reason");
        Guild guild = event.getGuild();
        event.deferReply(true).queue();
        guild.retrieveMemberById(targetId).queue(target -> {
            try {
                requireInteraction(event.getMember(), target);
                target.timeoutFor(Duration.ofMinutes(minutes)).reason(reason).queue(success -> {
                    moderation.log(guild.getIdLong(), targetId, event.getUser().getIdLong(),
                            "MUTE", reason, Duration.ofMinutes(minutes).toSeconds(), null);
                    notifyUser(event, targetId, "🔇 Has recibido un mute de **" + minutes + " minutos** en **"
                            + guild.getName() + "**.\n**Motivo:** " + reason);
                    event.getHook().editOriginal("✅ <@" + targetId + "> silenciado por " + minutes + " minutos.").queue();
                }, error -> event.getHook().editOriginal("❌ No se pudo aplicar el mute: " + error.getMessage()).queue());
            } catch (RuntimeException exception) {
                event.getHook().editOriginal("❌ " + safeMessage(exception)).queue();
            }
        }, error -> event.getHook().editOriginal("❌ Ese usuario no pertenece al servidor.").queue());
    }

    private void kick(ModalInteractionEvent event) {
        long targetId = parseDiscordId(value(event, "target-id"));
        String reason = value(event, "reason");
        Guild guild = event.getGuild();
        event.deferReply(true).queue();
        guild.retrieveMemberById(targetId).queue(target -> {
            try {
                requireInteraction(event.getMember(), target);
                notifyUser(event, targetId, "👢 Has sido expulsado de **" + guild.getName()
                        + "**.\n**Motivo:** " + reason);
                target.kick().reason(reason).queue(success -> {
                    moderation.log(guild.getIdLong(), targetId, event.getUser().getIdLong(),
                            "KICK", reason, null, null);
                    event.getHook().editOriginal("✅ <@" + targetId + "> fue expulsado.").queue();
                }, error -> event.getHook().editOriginal("❌ No se pudo expulsar: " + error.getMessage()).queue());
            } catch (RuntimeException exception) {
                event.getHook().editOriginal("❌ " + safeMessage(exception)).queue();
            }
        }, error -> event.getHook().editOriginal("❌ Ese usuario no pertenece al servidor.").queue());
    }

    private void ban(ModalInteractionEvent event) {
        long targetId = parseDiscordId(value(event, "target-id"));
        String reason = value(event, "reason");
        Guild guild = event.getGuild();
        Member cached = guild.getMemberById(targetId);
        if (cached != null) requireInteraction(event.getMember(), cached);
        event.deferReply(true).queue();
        notifyUser(event, targetId, "🔨 Has sido baneado de **" + guild.getName()
                + "**.\n**Motivo:** " + reason);
        guild.ban(UserSnowflake.fromId(targetId), 0, TimeUnit.SECONDS).reason(reason).queue(success -> {
            moderation.log(guild.getIdLong(), targetId, event.getUser().getIdLong(),
                    "BAN", reason, null, null);
            event.getHook().editOriginal("✅ <@" + targetId + "> fue baneado.").queue();
        }, error -> event.getHook().editOriginal("❌ No se pudo banear: " + error.getMessage()).queue());
    }

    private void showSanctionedUsers(ButtonInteractionEvent event) {
        requireModerator(event.getMember());
        event.replyEmbeds(sanctionedUsersEmbed(event.getGuild())).setEphemeral(true).queue();
    }

    private net.dv8tion.jda.api.entities.MessageEmbed sanctionedUsersEmbed(Guild guild) {
        List<SanctionedUser> users = strikes.listSanctionedUsers(guild.getIdLong(), 25);
        StringBuilder description = new StringBuilder();
        for (SanctionedUser user : users) {
            description.append("<@").append(user.userId()).append("> (`")
                    .append(user.userId()).append("`) — **")
                    .append(user.warnCount()).append(" warn(s), ")
                    .append(user.points()).append(" punto(s)**\n");
        }
        if (description.isEmpty()) description.append("No hay usuarios con warns activos.");
        return new EmbedBuilder().setColor(new Color(88, 101, 242))
                .setTitle("👥 Usuarios sancionados")
                .setDescription(truncate(description.toString(), 4000))
                .setFooter("Se muestran hasta 25 usuarios")
                .build();
    }

    private void showModeratorRoleUsers(StringSelectInteractionEvent event) {
        Role role = event.getGuild().getRoleById(config.moderatorRoleId());
        if (role == null) {
            event.reply("❌ No se encontró el rol de Moderación configurado.").setEphemeral(true).queue();
            return;
        }
        String users = event.getGuild().getMembers().stream()
                .filter(member -> member.getRoles().contains(role))
                .limit(40)
                .map(Member::getAsMention)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No hay usuarios con este rol.");
        event.replyEmbeds(new EmbedBuilder().setColor(new Color(88, 101, 242))
                        .setTitle("🛡️ Usuarios de " + role.getName())
                        .setDescription(users)
                        .build())
                .setEphemeral(true).queue();
    }

    private void applyConfiguredThreshold(Guild guild, long targetId, int total, String reason, long moderatorId) {
        if (!config.autoApplyStrikeSanctions()) return;
        if (total >= config.strikeBanThreshold()) {
            guild.ban(UserSnowflake.fromId(targetId), 0, TimeUnit.SECONDS)
                    .reason("Umbral de warns: " + reason)
                    .queue(success -> moderation.log(guild.getIdLong(), targetId, moderatorId,
                            "AUTO_BAN", reason, null, null), ignored -> {});
        } else if (total >= config.strikeMuteThreshold()) {
            guild.retrieveMemberById(targetId).queue(member -> member
                    .timeoutFor(Duration.ofHours(config.strikeMuteHours()))
                    .reason("Umbral de warns: " + reason)
                    .queue(success -> moderation.log(guild.getIdLong(), targetId, moderatorId,
                            "AUTO_MUTE", reason, Duration.ofHours(config.strikeMuteHours()).toSeconds(), null),
                            ignored -> {}), ignored -> {});
        }
    }

    private void notifyUser(ModalInteractionEvent event, long userId, String message) {
        event.getJDA().retrieveUserById(userId).queue(user -> user.openPrivateChannel()
                .flatMap(channel -> channel.sendMessage(message)).queue(null, ignored -> {}), ignored -> {});
    }

    private void requireModerator(Member member) {
        if (!permissions.isModerator(member)) throw new IllegalArgumentException("No tienes permisos de Moderación");
    }

    private static void requireAdministrator(Member member) {
        if (!isAdministrator(member)) throw new IllegalArgumentException("Solo un administrador puede publicar paneles");
    }

    private static boolean isAdministrator(Member member) {
        return member != null && member.hasPermission(Permission.ADMINISTRATOR);
    }

    private static void requireInteraction(Member moderator, Member target) {
        if (moderator == null || moderator.getIdLong() == target.getIdLong() || !moderator.canInteract(target)) {
            throw new IllegalArgumentException("No puedes moderar a ese usuario por la jerarquía de roles");
        }
        if (!target.getGuild().getSelfMember().canInteract(target)) {
            throw new IllegalArgumentException("El rol del bot está por debajo del usuario objetivo");
        }
    }

    private boolean isConfiguredGuild(Guild guild) {
        return guild != null && guild.getIdLong() == config.guildId();
    }

    private static String value(ModalInteractionEvent event, String id) {
        var value = event.getValue(id);
        if (value == null || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("Falta completar el campo " + id);
        }
        return value.getAsString().trim();
    }

    private static String optionalValue(ModalInteractionEvent event, String id) {
        var value = event.getValue(id);
        return value == null ? "" : value.getAsString().trim();
    }

    private static long parseDiscordId(String input) {
        String digits = input.replaceAll("\\D", "");
        if (digits.length() < 15 || digits.length() > 20) {
            throw new IllegalArgumentException("El ID de Discord no es válido");
        }
        return Long.parseUnsignedLong(digits);
    }

    private static long parseLong(String input, String errorMessage) {
        try {
            long value = Long.parseLong(input.trim());
            if (value < 1) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private static int parseInteger(String input, int minimum, int maximum, String errorMessage) {
        try {
            int value = Integer.parseInt(input.trim());
            if (value < minimum || value > maximum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? "Ocurrió un error inesperado" : error.getMessage();
    }

    private static void replyError(ButtonInteractionEvent event, RuntimeException exception) {
        if (!event.isAcknowledged()) {
            event.reply("❌ " + safeMessage(exception)).setEphemeral(true).queue();
        }
    }

    private static void replyError(ModalInteractionEvent event, RuntimeException exception) {
        if (event.isAcknowledged()) event.getHook().editOriginal("❌ " + safeMessage(exception)).queue();
        else event.reply("❌ " + safeMessage(exception)).setEphemeral(true).queue();
    }
}
