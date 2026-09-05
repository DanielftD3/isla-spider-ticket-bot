package com.islaspider.bot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SteamIdTest {
    @Test
    void acceptsRawSteamId64() {
        assertEquals("76561198012345678", SteamId.normalize("76561198012345678"));
    }

    @Test
    void extractsSteamId64FromProfileUrl() {
        assertEquals("76561198012345678",
                SteamId.normalize("https://steamcommunity.com/profiles/76561198012345678/"));
    }

    @Test
    void rejectsNamesAndVanityUrls() {
        assertThrows(IllegalArgumentException.class, () -> SteamId.normalize("JugadorSpider"));
        assertThrows(IllegalArgumentException.class,
                () -> SteamId.normalize("https://steamcommunity.com/id/JugadorSpider"));
    }
}
