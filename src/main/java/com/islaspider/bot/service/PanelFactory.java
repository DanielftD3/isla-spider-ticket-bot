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
    public static final String CLOSE_BUTTON_ID = "ticket:close";

    private final BotConfig config;

    public PanelFactory(BotConfig config) {
        this.config = config;
    }

    public MessageEmbed ticketPanel() {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(230, 30, 40))
                .setTitle("🎟️ Centro de tickets")
                .setDescription("""
                        ¿Necesitas ayuda? Selecciona una categoría en el menú de abajo.
                        Se abrirá un canal privado al que solo tendrás acceso tú y el equipo responsable.

                        **Antes de abrir un ticket:** se te pedirá tu nombre dentro del juego y una explicación de lo sucedido. Adjunta capturas, videos o enlaces dentro del canal.

                        🦕 **Bug en el juego · Staff**
                        Reporta errores, fallos visuales o mecánicas que no funcionan.

                        ❓ **Ayuda general · Staff**
                        Dudas sobre el servidor, sistemas del juego o ayuda general.

                        🚨 **Reportar a otro jugador · Moderación**
                        Denuncia conductas que incumplan las normas. Aporta pruebas y contexto.

                        ⚖️ **Apelar strike o ban**
                        La administración revisará tu solicitud y responderá por mensaje privado.

                        📌 **Importante**
                        No hagas spam ni compartas información personal. Cuando el caso quede resuelto, podrás valorar la atención.
                        """)
                .setFooter("Selecciona una categoría para comenzar")
                .setTimestamp(Instant.now());
        if (!config.panelImageUrl().isBlank()) {
            embed.setImage(config.panelImageUrl());
        }
        return embed.build();
    }

    public ActionRow ticketMenu() {
        return ActionRow.of(StringSelectMenu.create(CREATE_MENU_ID)
                .setPlaceholder("Selecciona el tipo de ticket")
                .addOption("Bug en el juego", "bug", "Informa un error o fallo del juego.", Emoji.fromUnicode("🦕"))
                .addOption("Ayuda general", "help", "Solicita ayuda al equipo de soporte.", Emoji.fromUnicode("❓"))
                .addOption("Reportar a otro jugador", "report", "Denuncia el comportamiento de otro jugador.", Emoji.fromUnicode("🚨"))
                .addOption("Apelar strike o ban", "appeal", "Solicita la revisión de una sanción.", Emoji.fromUnicode("⚖️"))
                .build());
    }

    public ActionRow ticketControls() {
        return ActionRow.of(
                Button.primary(CLAIM_BUTTON_ID, "Reclamar ticket").withEmoji(Emoji.fromUnicode("✋")),
                Button.danger(STRIKE_BUTTON_ID, "Agregar strike").withEmoji(Emoji.fromUnicode("⚠️")),
                Button.secondary(CLOSE_BUTTON_ID, "Cerrar").withEmoji(Emoji.fromUnicode("🔒"))
        );
    }
}

