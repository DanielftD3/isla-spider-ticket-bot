package com.islaspider.bot.listener;

import com.islaspider.bot.config.BotConfig;
import com.islaspider.bot.db.ModerationRepository;
import com.islaspider.bot.db.StrikeRepository;
import com.islaspider.bot.model.Strike;
import com.islaspider.bot.service.PanelFactory;
import com.islaspider.bot.service.PermissionService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.Color;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ModerationCommandListener extends ListenerAdapter {
    private final BotConfig config;
    private final StrikeRepository strikes;
    private final ModerationRepository moderation;
    private final PermissionService permissions;
    private final PanelFactory panels;

    public ModerationCommandListener(BotConfig config, StrikeRepository strikes, ModerationRepository moderation,
                                     PermissionService permissions, PanelFactory panels) {
        this.config = config;
        this.strikes = strikes;
        this.moderation = moderation;
        this.permissions = permissions;
        this.panels = panels;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild().getIdLong() != config.guildId()) return;
        try {
            switch (event.getName()) {
                case "ticket-panel" -> sendPanel(event);
                case "strike" -> handleStrike(event);
                case "warn" -> warn(event);
                case "mute" -> mute(event);
                case "kick" -> kick(event);
                case "ban" -> ban(event);
                default -> { }
            }
        } catch (RuntimeException exception) {
            if (!event.isAcknowledged()) event.reply("❌ " + safeMessage(exception)).setEphemeral(true).queue();
        }
    }

    private void sendPanel(SlashCommandInteractionEvent event) {
        if (event.getMember() == null || !event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply("❌ Solo un administrador puede publicar el panel.").setEphemeral(true).queue();
            return;
        }
        event.getChannel().sendMessageEmbeds(panels.ticketPanel()).setComponents(panels.ticketMenu()).queue();
        event.reply("✅ Panel de tickets publicado.").setEphemeral(true).queue();
    }

    private void handleStrike(SlashCommandInteractionEvent event) {
        requireModerator(event);
        String subcommand = event.getSubcommandName();
        if (subcommand == null) throw new IllegalArgumentException("Falta el subcomando");
        switch (subcommand) {
            case "add" -> addStrike(event);
            case "list" -> listStrikes(event);
            case "remove" -> removeStrike(event);
            default -> throw new IllegalArgumentException("Subcomando de strike desconocido");
        }
    }

    private void addStrike(SlashCommandInteractionEvent event) {
        User user = required(event, "usuario").getAsUser();
        int points = required(event, "puntos").getAsInt();
        String reason = required(event, "motivo").getAsString();
        String evidence = optional(event, "evidencia", OptionMapping::getAsString, "");
        Long ticketId = optional(event, "ticket", OptionMapping::getAsLong, null);
        if (points < 1 || points > 10) throw new IllegalArgumentException("Los puntos deben estar entre 1 y 10");

        long strikeId = strikes.add(event.getGuild().getIdLong(), user.getIdLong(), points, reason, evidence,
                ticketId, event.getUser().getIdLong());
        int total = strikes.activePoints(event.getGuild().getIdLong(), user.getIdLong());
        moderation.log(event.getGuild().getIdLong(), user.getIdLong(), event.getUser().getIdLong(),
                "STRIKE", reason, null, ticketId);
        notifyUser(user, "⚠️ Has recibido el strike **#" + strikeId + "** en **" + event.getGuild().getName()
                + "**.\n**Puntos:** " + points + "\n**Total activo:** " + total + "\n**Motivo:** " + reason);
        applyConfiguredThreshold(event.getGuild(), user.getIdLong(), total, reason, ticketId, event.getUser().getIdLong());
        event.reply("⚠️ Strike **#" + strikeId + "** agregado a " + user.getAsMention()
                + ". Total: **" + total + " punto(s)**.").queue();
    }

    private void listStrikes(SlashCommandInteractionEvent event) {
        User user = required(event, "usuario").getAsUser();
        List<Strike> active = strikes.listActive(event.getGuild().getIdLong(), user.getIdLong());
        int total = active.stream().mapToInt(Strike::points).sum();
        StringBuilder description = new StringBuilder();
        for (Strike strike : active) {
            description.append("**#").append(strike.id()).append(" · ").append(strike.points()).append(" punto(s)**\n")
                    .append(strike.reason()).append("\nModerador: <@").append(strike.moderatorId()).append(">")
                    .append(strike.sourceTicketId() == null ? "" : " · Ticket #" + strike.sourceTicketId()).append("\n\n");
        }
        if (description.isEmpty()) description.append("Este usuario no tiene strikes activos.");
        event.replyEmbeds(new EmbedBuilder().setColor(new Color(245, 166, 35))
                .setTitle("Historial activo de " + user.getEffectiveName())
                .setDescription(description.length() > 4000 ? description.substring(0, 3999) : description.toString())
                .setFooter("Total activo: " + total + " punto(s)").build()).setEphemeral(true).queue();
    }

    private void removeStrike(SlashCommandInteractionEvent event) {
        long strikeId = required(event, "id").getAsLong();
        String reason = required(event, "motivo").getAsString();
        boolean removed = strikes.revoke(event.getGuild().getIdLong(), strikeId, event.getUser().getIdLong(), reason);
        event.reply(removed ? "✅ Strike #" + strikeId + " retirado. Motivo: " + reason
                : "⚠️ El strike no existe o ya estaba retirado.").setEphemeral(true).queue();
    }

    private void warn(SlashCommandInteractionEvent event) {
        requireModerator(event);
        User user = required(event, "usuario").getAsUser();
        String reason = required(event, "motivo").getAsString();
        moderation.log(event.getGuild().getIdLong(), user.getIdLong(), event.getUser().getIdLong(),
                "WARN", reason, null, null);
        notifyUser(user, "📢 Has recibido una advertencia en **" + event.getGuild().getName() + "**.\n**Motivo:** " + reason
                + "\nEsta advertencia no suma puntos de strike.");
        event.reply("📢 Advertencia enviada a " + user.getAsMention() + ". **No suma strikes.**").queue();
    }

    private void mute(SlashCommandInteractionEvent event) {
        requireModerator(event);
        Member target = required(event, "usuario").getAsMember();
        if (target == null) throw new IllegalArgumentException("Ese usuario no pertenece al servidor");
        int minutes = required(event, "minutos").getAsInt();
        String reason = required(event, "motivo").getAsString();
        requireInteraction(event.getMember(), target);
        if (minutes < 1 || minutes > 40320) throw new IllegalArgumentException("El mute debe durar entre 1 y 40320 minutos");
        event.deferReply().queue();
        target.timeoutFor(Duration.ofMinutes(minutes)).reason(reason).queue(success -> {
            moderation.log(event.getGuild().getIdLong(), target.getIdLong(), event.getUser().getIdLong(),
                    "MUTE", reason, Duration.ofMinutes(minutes).toSeconds(), null);
            notifyUser(target.getUser(), "🔇 Has recibido un mute de **" + minutes + " minutos** en **"
                    + event.getGuild().getName() + "**.\n**Motivo:** " + reason);
            event.getHook().editOriginal("🔇 " + target.getAsMention() + " silenciado por " + minutes + " minutos.").queue();
        }, error -> event.getHook().editOriginal("❌ No se pudo aplicar el mute: " + error.getMessage()).queue());
    }

    private void kick(SlashCommandInteractionEvent event) {
        requireModerator(event);
        Member target = required(event, "usuario").getAsMember();
        if (target == null) throw new IllegalArgumentException("Ese usuario no pertenece al servidor");
        String reason = required(event, "motivo").getAsString();
        requireInteraction(event.getMember(), target);
        event.deferReply().queue();
        notifyUser(target.getUser(), "👢 Has sido expulsado de **" + event.getGuild().getName() + "**.\n**Motivo:** " + reason);
        target.kick().reason(reason).queue(success -> {
            moderation.log(event.getGuild().getIdLong(), target.getIdLong(), event.getUser().getIdLong(),
                    "KICK", reason, null, null);
            event.getHook().editOriginal("👢 " + target.getUser().getAsTag() + " fue expulsado.").queue();
        }, error -> event.getHook().editOriginal("❌ No se pudo expulsar: " + error.getMessage()).queue());
    }

    private void ban(SlashCommandInteractionEvent event) {
        requireModerator(event);
        User target = required(event, "usuario").getAsUser();
        String reason = required(event, "motivo").getAsString();
        Member member = event.getGuild().getMember(target);
        if (member != null) requireInteraction(event.getMember(), member);
        event.deferReply().queue();
        notifyUser(target, "🔨 Has sido baneado de **" + event.getGuild().getName() + "**.\n**Motivo:** " + reason);
        event.getGuild().ban(UserSnowflake.fromId(target.getIdLong()), 0, TimeUnit.SECONDS).reason(reason).queue(success -> {
            moderation.log(event.getGuild().getIdLong(), target.getIdLong(), event.getUser().getIdLong(),
                    "BAN", reason, null, null);
            event.getHook().editOriginal("🔨 " + target.getAsTag() + " fue baneado.").queue();
        }, error -> event.getHook().editOriginal("❌ No se pudo banear: " + error.getMessage()).queue());
    }

    private void applyConfiguredThreshold(Guild guild, long targetId, int total, String reason,
                                          Long ticketId, long moderatorId) {
        if (!config.autoApplyStrikeSanctions()) return;
        if (total >= config.strikeBanThreshold()) {
            guild.ban(UserSnowflake.fromId(targetId), 0, TimeUnit.SECONDS).reason("Umbral de strikes: " + reason)
                    .queue(success -> moderation.log(guild.getIdLong(), targetId, moderatorId, "AUTO_BAN", reason, null, ticketId),
                            ignored -> {});
        } else if (total >= config.strikeMuteThreshold()) {
            guild.retrieveMemberById(targetId).queue(member -> member.timeoutFor(Duration.ofHours(config.strikeMuteHours()))
                    .reason("Umbral de strikes: " + reason).queue(success -> moderation.log(guild.getIdLong(), targetId,
                            moderatorId, "AUTO_MUTE", reason, Duration.ofHours(config.strikeMuteHours()).toSeconds(), ticketId),
                            ignored -> {}), ignored -> {});
        }
    }

    private void requireModerator(SlashCommandInteractionEvent event) {
        if (!permissions.isModerator(event.getMember())) throw new IllegalArgumentException("No tienes permisos de Moderación");
    }

    private static void requireInteraction(Member moderator, Member target) {
        if (moderator == null || !moderator.canInteract(target)) {
            throw new IllegalArgumentException("No puedes moderar a ese usuario por la jerarquía de roles");
        }
    }

    private static OptionMapping required(SlashCommandInteractionEvent event, String name) {
        OptionMapping option = event.getOption(name);
        if (option == null) throw new IllegalArgumentException("Falta la opción " + name);
        return option;
    }

    private static <T> T optional(SlashCommandInteractionEvent event, String name,
                                  java.util.function.Function<OptionMapping, T> mapper, T fallback) {
        OptionMapping option = event.getOption(name);
        return option == null ? fallback : mapper.apply(option);
    }

    private static void notifyUser(User user, String message) {
        user.openPrivateChannel().flatMap(channel -> channel.sendMessage(message)).queue(null, ignored -> {});
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? "Ocurrió un error inesperado" : error.getMessage();
    }
}
