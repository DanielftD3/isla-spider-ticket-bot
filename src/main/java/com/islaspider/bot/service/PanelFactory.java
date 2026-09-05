package com.islaspider.bot.service;

import com.islaspider.bot.config.BotConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.awt.Color;
import java.time.Instant;

public final class PanelFactory {
    public static final String CREATE_MENU_ID = "ticket:create";
    public static final String CLAIM_BUTTON_ID = "ticket:claim";
    public static final String STRIKE_BUTTON_ID = "ticket:strike";
    public static final String SURVEY_BUTTON_ID = "ticket:survey";
    public static final String TRANSCRIPT_BUTTON_ID = "ticket:transcript";
    public static final String CLOSE_BUTTON_ID = "ticket:close";

    public static final String SETUP_TICKETS_BUTTON_ID = "setup:publish:tickets";
    public static final String SETUP_MODERATION_BUTTON_ID = "setup:publish:moderation";
    public static final String MODERATION_MENU_ID = "moderation:action";
    public static final String MODERATION_REFRESH_BUTTON_ID = "moderation:refresh";
    public static final String MODERATION_USERS_BUTTON_ID = "moderation:users";

    private final BotConfig config;

    public PanelFactory(BotConfig config) {
        this.config = config;
    }

    public MessageEmbed setupPanel() {
        return new EmbedBuilder()
                .setColor(new Color(88, 101, 242))
                .setTitle("⚙️ ISLA SPIDER · CONFIGURACIÓN")
                .setDescription("Publica desde aquí los paneles permanentes del bot. "
                        + "Este asistente solo es visible para ti.")
                .addField("🎫 Panel de tickets", "Solicitudes, reportes y apelaciones.", false)
                .addField("🛡️ Panel de moderación", "Warns, strikes, mute, kick y ban.", false)
                .setFooter("/setup es el único comando del bot")
                .build();
    }

    public ActionRow setupControls() {
        return ActionRow.of(
                Button.primary(SETUP_TICKETS_BUTTON_ID, "Publicar tickets").withEmoji(Emoji.fromUnicode("🎫")),
                Button.secondary(SETUP_MODERATION_BUTTON_ID, "Publicar moderación").withEmoji(Emoji.fromUnicode("🛡️"))
        );
    }

    public MessageEmbed ticketPanel() {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(230, 30, 40))
                .setTitle("ISLA SPIDER · SOPORTE")
                .setDescription("""
                        [**🎫**](https://discord.com/assets/0668ca7859b5ab1a.svg) **Centro de tickets**

                        ¿Necesitas ayuda? Selecciona una categoría en el menú de abajo. Se abrirá un canal privado al que solo tendrás acceso tú y el equipo responsable.

                        **Antes de abrir un ticket:** se te pedirá tu nombre dentro del juego y una explicación de lo sucedido. Adjunta capturas o vídeos si los tienes y evita abrir más de un ticket para el mismo asunto.

                        [**🐛**](https://discord.com/assets/cf1083ab7430f27e.svg) **Bug en el juego · Staff**

                        Reporta errores, fallos, problemas visuales o mecánicas que no funcionen. El equipo de **Staff** revisará la información y te orientará.

                        [**❓**](https://discord.com/assets/881ed827548f38c6.svg) **Ayuda general · Staff**

                        Usa esta opción para dudas sobre el servidor, sistemas del juego o ayuda general. El equipo de **Staff** se encargará de atenderte.

                        [**🚨**](https://discord.com/assets/8d433206d7a0fe81.svg) **Reportar a otro jugador · Moderación**

                        Denuncia conductas que incumplan las normas. Aporta pruebas y contexto; el equipo de **Moderación** evaluará el caso de forma privada.

                        [**⚖️**](https://discord.com/assets/b5138bdc32633d55.svg) **Apelar strike o ban**

                        Selecciona la sanción que quieres apelar y explica tu caso. No se abrirá un ticket: la administración revisará tu solicitud y te responderá por mensaje directo.

                        [**📌**](https://discord.com/assets/c7ba45651998dd2c.svg) **Importante**

                        No hagas spam, no menciones repetidamente al equipo y no compartas información personal. Cuando el caso quede resuelto, podrás valorar la atención recibida.
                        """)
                .setFooter("Selecciona una categoría para comenzar")
                .setTimestamp(Instant.now());
        if (!config.panelImageUrl().isBlank()) embed.setImage(config.panelImageUrl());
        return embed.build();
    }

    public ActionRow ticketMenu() {
        return ActionRow.of(StringSelectMenu.create(CREATE_MENU_ID)
                .setPlaceholder("Selecciona el tipo de ticket")
                .addOption("Bug en el juego", "bug", "Informa un error o fallo del juego.", Emoji.fromUnicode("🐛"))
                .addOption("Ayuda general", "help", "Solicita ayuda al equipo de soporte.", Emoji.fromUnicode("❓"))
                .addOption("Reportar a otro jugador", "report", "Denuncia el comportamiento de otro jugador.", Emoji.fromUnicode("🚨"))
                .addOption("Apelar strike o ban", "appeal", "Solicita la revisión de una sanción.", Emoji.fromUnicode("⚖️"))
                .build());
    }

    public ActionRow ticketControls() {
        return ActionRow.of(
                Button.primary(CLAIM_BUTTON_ID, "Reclamar").withEmoji(Emoji.fromUnicode("🙋")),
                Button.danger(STRIKE_BUTTON_ID, "Añadir warn").withEmoji(Emoji.fromUnicode("⚠️")),
                Button.success(SURVEY_BUTTON_ID, "Encuesta").withEmoji(Emoji.fromUnicode("⭐")),
                Button.secondary(TRANSCRIPT_BUTTON_ID, "Transcribir").withEmoji(Emoji.fromUnicode("📄")),
                Button.danger(CLOSE_BUTTON_ID, "Cerrar").withEmoji(Emoji.fromUnicode("🔒"))
        );
    }

    public ActionRow feedbackButtons(long ticketId) {
        return ActionRow.of(
                rating(ticketId, 1), rating(ticketId, 2), rating(ticketId, 3),
                rating(ticketId, 4), rating(ticketId, 5)
        );
    }

    private Button rating(long ticketId, int score) {
        return Button.secondary("ticket:rating:" + ticketId + ":" + score, score + " ⭐");
    }

    public MessageEmbed moderationPanel(long activeWarns, long sanctionedUsers) {
        return new EmbedBuilder()
                .setColor(new Color(88, 101, 242))
                .setTitle("🛡️ Centro de Moderación · Warns")
                .setDescription("""
                        Gestiona las sanciones del servidor desde un único menú.

                        **📖 Guía para moderadores**
                        • **Añadir Warn** — registra puntos, motivo y evidencia.
                        • **Quitar Warn** — retira un warn sin borrar el historial.
                        • **Revisar Warns** — consulta el historial activo de un usuario.
                        • **Advertir sin puntos** — envía un aviso informativo.
                        • **Mute, Kick y Ban** — aplica una sanción desde un formulario.

                        **🔐 Acceso:** Staff de Moderación
                        """)
                .addField("⚠️ Warns activos", String.valueOf(activeWarns), true)
                .addField("👥 Usuarios sancionados", String.valueOf(sanctionedUsers), true)
                .addField("💾 Almacenamiento", "PostgreSQL", true)
                .setFooter("Los cambios se guardan automáticamente")
                .setTimestamp(Instant.now())
                .build();
    }

    public ActionRow moderationMenu() {
        return ActionRow.of(StringSelectMenu.create(MODERATION_MENU_ID)
                .setPlaceholder("Selecciona una acción de moderación")
                .addOption("Añadir Warn", "add", "Suma puntos a un usuario.", Emoji.fromUnicode("➕"))
                .addOption("Quitar Warn", "remove", "Retira un warn por su ID.", Emoji.fromUnicode("➖"))
                .addOption("Revisar Warns", "review", "Consulta el historial de un usuario.", Emoji.fromUnicode("🔎"))
                .addOption("Advertir sin puntos", "warn", "Envía una advertencia informativa.", Emoji.fromUnicode("📢"))
                .addOption("Aplicar Mute", "mute", "Silencia temporalmente a un miembro.", Emoji.fromUnicode("🔇"))
                .addOption("Expulsar usuario", "kick", "Expulsa a un miembro del servidor.", Emoji.fromUnicode("👢"))
                .addOption("Banear usuario", "ban", "Banea a un usuario del servidor.", Emoji.fromUnicode("🔨"))
                .addOption("Usuarios de Moderación", "role-users", "Muestra el equipo con acceso.", Emoji.fromUnicode("🛡️"))
                .build());
    }

    public ActionRow moderationControls() {
        return ActionRow.of(
                Button.primary(MODERATION_USERS_BUTTON_ID, "Usuarios sancionados").withEmoji(Emoji.fromUnicode("👥")),
                Button.secondary(MODERATION_REFRESH_BUTTON_ID, "Actualizar").withEmoji(Emoji.fromUnicode("🔄"))
        );
    }
}
