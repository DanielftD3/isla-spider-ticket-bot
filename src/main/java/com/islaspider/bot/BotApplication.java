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
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
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
                    .setActivity(Activity.watching("tickets y moderación"))
                    .addEventListeners(
                            new TicketListener(config, tickets, strikes, moderation, permissions, panels, transcripts),
                            new MessageArchiveListener(config, tickets),
                            new ControlPanelListener(config, strikes, moderation, permissions, panels))
                    .build()
                    .awaitReady();
        } catch (RuntimeException | InterruptedException exception) {
            database.close();
            throw exception;
        }

        Guild guild = jda.getGuildById(config.guildId());
        if (guild == null) {
            jda.shutdownNow();
            database.close();
            throw new IllegalStateException("El bot no se encuentra en el servidor configurado en GUILD_ID");
        }
        validateDiscordObjects(config, guild);
        CommandRegistry.register(guild);

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
}
