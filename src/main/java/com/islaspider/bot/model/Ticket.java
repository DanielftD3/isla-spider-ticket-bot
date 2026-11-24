package com.islaspider.bot.model;

import java.time.OffsetDateTime;

public record Ticket(
        long id,
        long guildId,
        Long channelId,
        long openerId,
        TicketType type,
        String gameName,
        String subject,
        String description,
        Long accusedUserId,
        String evidence,
        String status,
        Long claimedBy,
        OffsetDateTime createdAt,
        Long closedBy,
        String closeReason,
        String transcriptPath,
        OffsetDateTime closedAt
) {
    public boolean isResolvedAppeal() {
        return type == TicketType.APPEAL && ("APPROVED".equals(status) || "REJECTED".equals(status));
    }

    public Ticket resolvedAppeal(long moderatorId, boolean approved, String reason) {
        if (type != TicketType.APPEAL || !"OPEN".equals(status)) {
            throw new IllegalStateException("Solo se puede resolver una apelación abierta");
        }
        return new Ticket(id, guildId, channelId, openerId, type, gameName, subject, description,
                accusedUserId, evidence, approved ? "APPROVED" : "REJECTED", claimedBy, createdAt,
                moderatorId, reason, null, OffsetDateTime.now());
    }
}
