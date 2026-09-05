package com.islaspider.bot.model;

import java.time.OffsetDateTime;

public record TicketAttachment(
        Long messageId,
        long authorId,
        String kind,
        String fileName,
        String url,
        String contentType,
        Long sizeBytes,
        OffsetDateTime createdAt
) {}

