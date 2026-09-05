package com.islaspider.bot.model;

import java.time.OffsetDateTime;

public record Strike(
        long id,
        long userId,
        int points,
        String reason,
        String evidenceUrl,
        Long sourceTicketId,
        long moderatorId,
        OffsetDateTime createdAt
) {}

