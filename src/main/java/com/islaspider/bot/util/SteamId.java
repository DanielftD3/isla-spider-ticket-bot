package com.islaspider.bot.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SteamId {
    private static final Pattern STEAM_ID_64 = Pattern.compile("(?<!\\d)(7656119\\d{10})(?!\\d)");

    private SteamId() {}

    public static String normalize(String input) {
        Matcher matcher = STEAM_ID_64.matcher(input == null ? "" : input.trim());
        if (!matcher.find()) {
            throw new IllegalArgumentException("Introduce un SteamID64 válido de 17 dígitos o el enlace de tu perfil");
        }
        return matcher.group(1);
    }
}
