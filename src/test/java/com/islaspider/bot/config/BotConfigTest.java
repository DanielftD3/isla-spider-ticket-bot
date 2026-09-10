package com.islaspider.bot.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BotConfigTest {
    @Test
    void usesDedicatedRatingChannelAndReusesTicketLogForAppeals() {
        Map<String, String> values = requiredValues();
        values.put("TICKET_IMAGE_CHANNEL_ID", "5");
        values.put("TICKET_TRANSCRIPT_CHANNEL_ID", "6");

        BotConfig config = BotConfig.from(values);

        assertEquals(4L, config.ticketLogChannelId());
        assertEquals(5L, config.ticketImageChannelId());
        assertEquals(6L, config.ticketTranscriptChannelId());
        assertEquals(9L, config.ticketRatingChannelId());
        assertEquals(4L, config.appealLogChannelId());
    }

    @Test
    void requiresDedicatedRatingChannel() {
        Map<String, String> values = requiredValues();
        values.remove("TICKET_RATING_CHANNEL_ID");

        assertThrows(IllegalStateException.class, () -> BotConfig.from(values));
    }

    @Test
    void rejectsUnchangedTemplatePlaceholders() {
        Map<String, String> values = requiredValues();
        values.put("DISCORD_TOKEN", "YOUR_BOT_TOKEN");

        assertThrows(IllegalStateException.class, () -> BotConfig.from(values));
    }

    private static Map<String, String> requiredValues() {
        Map<String, String> values = new HashMap<>();
        values.put("DISCORD_TOKEN", "test-token");
        values.put("GUILD_ID", "1");
        values.put("TICKET_CATEGORY_ID", "2");
        values.put("TICKET_LOG_CHANNEL_ID", "4");
        values.put("TICKET_RATING_CHANNEL_ID", "9");
        values.put("STAFF_ROLE_ID", "7");
        values.put("MOD_ROLE_ID", "8");
        values.put("DB_URL", "jdbc:postgresql://localhost:5432/test");
        values.put("DB_USER", "test");
        values.put("DB_PASSWORD", "test-password");
        return values;
    }
}
