package com.islaspider.bot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;

public final class CommandRegistry {
    private CommandRegistry() {}

    public static void register(Guild guild) {
        DefaultMemberPermissions moderator = DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS);

        CommandData panel = Commands.slash("ticket-panel", "Publica el panel para crear tickets")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));

        CommandData strike = Commands.slash("strike", "Administra strikes de moderación")
                .setDefaultPermissions(moderator)
                .addSubcommands(
                        new SubcommandData("add", "Agrega puntos de strike a un usuario")
                                .addOptions(
                                        required(OptionType.USER, "usuario", "Usuario sancionado"),
                                        required(OptionType.INTEGER, "puntos", "Cantidad de puntos, entre 1 y 10"),
                                        required(OptionType.STRING, "motivo", "Motivo del strike"),
                                        optional(OptionType.STRING, "evidencia", "Enlace de la evidencia"),
                                        optional(OptionType.INTEGER, "ticket", "ID interno del ticket relacionado")),
                        new SubcommandData("list", "Consulta los strikes activos de un usuario")
                                .addOptions(required(OptionType.USER, "usuario", "Usuario que deseas consultar")),
                        new SubcommandData("remove", "Retira un strike aplicado por error")
                                .addOptions(
                                        required(OptionType.INTEGER, "id", "ID del strike"),
                                        required(OptionType.STRING, "motivo", "Razón para retirar el strike"))
                );

        CommandData warn = Commands.slash("warn", "Envía una advertencia sin sumar puntos de strike")
                .setDefaultPermissions(moderator)
                .addOptions(required(OptionType.USER, "usuario", "Usuario advertido"),
                        required(OptionType.STRING, "motivo", "Motivo de la advertencia"));

        CommandData mute = Commands.slash("mute", "Silencia temporalmente a un miembro")
                .setDefaultPermissions(moderator)
                .addOptions(required(OptionType.USER, "usuario", "Miembro que será silenciado"),
                        required(OptionType.INTEGER, "minutos", "Duración entre 1 y 40320 minutos"),
                        required(OptionType.STRING, "motivo", "Motivo del mute"));

        CommandData kick = Commands.slash("kick", "Expulsa a un miembro del servidor")
                .setDefaultPermissions(moderator)
                .addOptions(required(OptionType.USER, "usuario", "Miembro que será expulsado"),
                        required(OptionType.STRING, "motivo", "Motivo de la expulsión"));

        CommandData ban = Commands.slash("ban", "Banea a un usuario del servidor")
                .setDefaultPermissions(moderator)
                .addOptions(required(OptionType.USER, "usuario", "Usuario que será baneado"),
                        required(OptionType.STRING, "motivo", "Motivo del ban"));

        guild.updateCommands().addCommands(List.of(panel, strike, warn, mute, kick, ban)).queue();
    }

    private static OptionData required(OptionType type, String name, String description) {
        return new OptionData(type, name, description, true);
    }

    private static OptionData optional(OptionType type, String name, String description) {
        return new OptionData(type, name, description, false);
    }
}

