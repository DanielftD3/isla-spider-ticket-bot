package com.islaspider.bot.model;

import java.time.OffsetDateTime;

public record TicketMessage(
        long messageId,
        long authorId,
        String authorName,
        String content,
        OffsetDateTime createdAt
) {}

