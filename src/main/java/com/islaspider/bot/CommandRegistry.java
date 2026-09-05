package com.islaspider.bot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public final class CommandRegistry {
    private CommandRegistry() {}

    public static void register(Guild guild) {
        guild.updateCommands()
                .addCommands(Commands.slash("setup", "Abre el asistente de configuración del bot")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)))
                .queue();
    }
}
