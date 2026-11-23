package com.islaspider.bot;

import com.islaspider.bot.config.BotConfig;
import com.islaspider.bot.db.Database;
import com.islaspider.bot.db.ModerationRepository;
import com.islaspider.bot.db.StrikeRepository;
import com.islaspider.bot.db.TicketRepository;
import com.islaspider.bot.listener.MessageArchiveListener;
import com.islaspider.bot.listener.ControlPanelListener;
import com.islaspider.bot.listener.TicketListener;
import com.islaspider.bot.service.PanelFactory;
import com.islaspider.bot.service.PermissionService;
import com.islaspider.bot.service.TranscriptService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.EnumSet;

public final class BotApplication {
    private BotApplication() {}

    public static void main(String[] args) throws InterruptedException {
        BotConfig config = BotConfig.load();
        Database database = new Database(config);
        database.initializeSchema();

        TicketRepository tickets = new TicketRepository(database);
        StrikeRepository strikes = new StrikeRepository(database);
        ModerationRepository moderation = new ModerationRepository(database);
        PermissionService permissions = new PermissionService(config);
        PanelFactory panels = new PanelFactory(config);
        TranscriptService transcripts = new TranscriptService(config, tickets);

        JDA jda;
        try {
            jda = JDABuilder.createDefault(config.discordToken(), EnumSet.of(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MEMBERS))
                    .setActivity(Activity.watching("ARK Dinosauria · tickets y moderación"))
                    .addEventListeners(
                            new TicketListener(config, tickets, permissions, panels, transcripts),
                            new MessageArchiveListener(config, tickets),
                            new ControlPanelListener(config, strikes, moderation, permissions, panels))
                    .build()
                    .awaitReady();
        } catch (RuntimeException | InterruptedException exception) {
            database.close();
            throw exception;
        }

        final Guild guild;
        try {
            guild = jda.getGuildById(config.guildId());
            if (guild == null) {
                throw new IllegalStateException("El bot no se encuentra en el servidor configurado en GUILD_ID");
            }
            validateDiscordObjects(config, guild);
            validateBotPermissions(config, guild);
            CommandRegistry.register(guild);
        } catch (RuntimeException exception) {
            jda.shutdownNow();
            database.close();
            throw exception;
        }

        if (!"ARK Dinosauria".equals(jda.getSelfUser().getName())) {
            jda.getSelfUser().getManager().setName("ARK Dinosauria").queue(
                    ignored -> System.out.println("Nombre del bot actualizado a ARK Dinosauria"),
                    error -> System.err.println("No se pudo actualizar el nombre del bot: " + error.getMessage()));
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            jda.shutdown();
            database.close();
        }, "isla-spider-shutdown"));

        System.out.println("Bot conectado como " + jda.getSelfUser().getName() + " en " + guild.getName());
    }

    private static void validateDiscordObjects(BotConfig config, Guild guild) {
        if (guild.getCategoryById(config.ticketCategoryId()) == null) {
            throw new IllegalStateException("TICKET_CATEGORY_ID no corresponde a una categoría del servidor");
        }
        if (guild.getTextChannelById(config.ticketLogChannelId()) == null) {
            throw new IllegalStateException("TICKET_LOG_CHANNEL_ID no corresponde a un canal de texto");
        }
        if (guild.getTextChannelById(config.ticketImageChannelId()) == null) {
            throw new IllegalStateException("TICKET_IMAGE_CHANNEL_ID no corresponde a un canal de texto");
        }
        if (guild.getTextChannelById(config.ticketTranscriptChannelId()) == null) {
            throw new IllegalStateException("TICKET_TRANSCRIPT_CHANNEL_ID no corresponde a un canal de texto");
        }
        if (guild.getTextChannelById(config.ticketRatingChannelId()) == null) {
            throw new IllegalStateException("TICKET_RATING_CHANNEL_ID no corresponde a un canal de texto");
        }
        if (guild.getTextChannelById(config.appealLogChannelId()) == null) {
            throw new IllegalStateException("APPEAL_LOG_CHANNEL_ID no corresponde a un canal de texto");
        }
        if (guild.getRoleById(config.staffRoleId()) == null) {
            throw new IllegalStateException("STAFF_ROLE_ID no corresponde a un rol del servidor");
        }
        if (guild.getRoleById(config.moderatorRoleId()) == null) {
            throw new IllegalStateException("MOD_ROLE_ID no corresponde a un rol del servidor");
        }
    }

    private static void validateBotPermissions(BotConfig config, Guild guild) {
        Member bot = guild.getSelfMember();
        Category category = guild.getCategoryById(config.ticketCategoryId());
        requirePermissions(bot, category, "la categoría de tickets",
                Permission.VIEW_CHANNEL, Permission.MANAGE_CHANNEL);

        requireLogPermissions(bot, guild.getTextChannelById(config.ticketLogChannelId()), "logs de tickets");
        requireLogPermissions(bot, guild.getTextChannelById(config.ticketImageChannelId()), "imágenes de tickets");
        requireLogPermissions(bot, guild.getTextChannelById(config.ticketTranscriptChannelId()), "transcripciones");
        requireLogPermissions(bot, guild.getTextChannelById(config.ticketRatingChannelId()), "valoraciones");
        requireLogPermissions(bot, guild.getTextChannelById(config.appealLogChannelId()), "apelaciones");

        if (!bot.hasPermission(Permission.MODERATE_MEMBERS, Permission.KICK_MEMBERS, Permission.BAN_MEMBERS)) {
            throw new IllegalStateException("Al bot le faltan permisos globales: Moderate Members, Kick Members o Ban Members");
        }
    }

    private static void requireLogPermissions(Member bot, TextChannel channel, String purpose) {
        requirePermissions(bot, channel, "el canal de " + purpose,
                Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
                Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ATTACH_FILES,
                Permission.MESSAGE_HISTORY);
    }

    private static void requirePermissions(Member bot, GuildChannel channel, String location,
                                           Permission... required) {
        if (channel == null || !bot.hasPermission(channel, required)) {
            throw new IllegalStateException("Al bot le faltan permisos necesarios en " + location);
        }
    }
}
