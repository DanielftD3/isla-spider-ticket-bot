package com.islaspider.bot.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public record BotConfig(
        String discordToken,
        long guildId,
        long ticketCategoryId,
        long ticketLogChannelId,
        long ticketImageChannelId,
        long ticketTranscriptChannelId,
        long ticketRatingChannelId,
        long appealLogChannelId,
        long staffRoleId,
        long moderatorRoleId,
        String databaseUrl,
        String databaseUser,
        String databasePassword,
        int databasePoolSize,
        Path transcriptDirectory,
        boolean deleteTicketChannelOnClose,
        int strikeMuteThreshold,
        int strikeBanThreshold,
        int strikeMuteHours,
        boolean autoApplyStrikeSanctions
) {
    public BotConfig {
        if (databasePoolSize < 1 || databasePoolSize > 50) {
            throw new IllegalArgumentException("DB_POOL_SIZE debe estar entre 1 y 50");
        }
        if (strikeMuteThreshold < 1 || strikeBanThreshold < 1 || strikeMuteHours < 1) {
            throw new IllegalArgumentException("Los umbrales y la duración de strikes deben ser mayores que cero");
        }
        if (strikeMuteThreshold >= strikeBanThreshold) {
            throw new IllegalArgumentException("STRIKE_MUTE_THRESHOLD debe ser menor que STRIKE_BAN_THRESHOLD");
        }
    }

    public static BotConfig load() {
        Map<String, String> values = new HashMap<>();
        loadDotEnv(Path.of(".env"), values);
        values.putAll(System.getenv());

        long ticketLogChannelId = requiredId(values, "TICKET_LOG_CHANNEL_ID");
        return new BotConfig(
                required(values, "DISCORD_TOKEN"),
                requiredId(values, "GUILD_ID"),
                requiredId(values, "TICKET_CATEGORY_ID"),
                ticketLogChannelId,
                optionalId(values, "TICKET_IMAGE_CHANNEL_ID", ticketLogChannelId),
                optionalId(values, "TICKET_TRANSCRIPT_CHANNEL_ID", ticketLogChannelId),
                optionalId(values, "TICKET_RATING_CHANNEL_ID", ticketLogChannelId),
                requiredId(values, "APPEAL_LOG_CHANNEL_ID"),
                requiredId(values, "STAFF_ROLE_ID"),
                requiredId(values, "MOD_ROLE_ID"),
                required(values, "DB_URL"),
                required(values, "DB_USER"),
                required(values, "DB_PASSWORD"),
                integer(values, "DB_POOL_SIZE", 8),
                Path.of(values.getOrDefault("TRANSCRIPT_DIR", "transcripts")),
                bool(values, "DELETE_TICKET_CHANNEL_ON_CLOSE", true),
                integer(values, "STRIKE_MUTE_THRESHOLD", 3),
                integer(values, "STRIKE_BAN_THRESHOLD", 5),
                integer(values, "STRIKE_MUTE_HOURS", 24),
                bool(values, "AUTO_APPLY_STRIKE_SANCTIONS", false)
        );
    }

    private static void loadDotEnv(Path path, Map<String, String> destination) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            for (String rawLine : Files.readAllLines(path)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator < 1) {
                    continue;
                }
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                destination.put(key, value);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer el archivo .env", exception);
        }
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank() || value.startsWith("PEGA_") || value.startsWith("CAMBIA_")) {
            throw new IllegalStateException("Falta configurar " + key + " en el archivo .env");
        }
        return value.trim();
    }

    private static long requiredId(Map<String, String> values, String key) {
        String value = required(values, key);
        try {
            long id = Long.parseUnsignedLong(value);
            if (id == 0) {
                throw new NumberFormatException("cero");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(key + " debe ser un ID numerico valido de Discord", exception);
        }
    }

    private static long optionalId(Map<String, String> values, String key, long defaultValue) {
        String value = values.get(key);
        if (value == null || value.isBlank() || value.equals("000000000000000000")) return defaultValue;
        try {
            long id = Long.parseUnsignedLong(value.trim());
            if (id == 0) throw new NumberFormatException("cero");
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(key + " debe ser un ID numerico valido de Discord", exception);
        }
    }

    private static int integer(Map<String, String> values, String key, int defaultValue) {
        try {
            return Integer.parseInt(values.getOrDefault(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(key + " debe ser un numero entero", exception);
        }
    }

    private static boolean bool(Map<String, String> values, String key, boolean defaultValue) {
        return Boolean.parseBoolean(values.getOrDefault(key, String.valueOf(defaultValue)));
    }
}
