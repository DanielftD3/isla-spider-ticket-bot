package com.islaspider.bot.model;

import java.util.Arrays;
import java.util.Locale;

public enum TicketType {
    BUG("bug", "Bug en el juego", "Informa un error o fallo del juego.", "bug"),
    HELP("help", "Ayuda general", "Solicita ayuda al equipo de soporte.", "ayuda"),
    REPORT("report", "Reportar a otro jugador", "Denuncia el comportamiento de otro jugador.", "reporte"),
    APPEAL("appeal", "Apelar strike o ban", "Solicita la revision de una sancion.", "apelacion");

    private final String code;
    private final String label;
    private final String description;
    private final String channelPrefix;

    TicketType(String code, String label, String description, String channelPrefix) {
        this.code = code;
        this.label = label;
        this.description = description;
        this.channelPrefix = channelPrefix;
    }

    public String code() { return code; }
    public String label() { return label; }
    public String description() { return description; }
    public String channelPrefix() { return channelPrefix; }

    public boolean requiresModeration() {
        return this == REPORT || this == APPEAL;
    }

    public String channelName(long caseId) {
        if (caseId < 1) throw new IllegalArgumentException("El número de caso debe ser positivo");
        return String.format(Locale.ROOT, "%s-%04d", channelPrefix, caseId);
    }

    public static TicketType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de ticket desconocido: " + code));
    }
}

