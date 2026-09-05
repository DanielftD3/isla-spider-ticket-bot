package com.islaspider.bot.model;

import java.time.OffsetDateTime;

public record Ticket(
        long id,
        long guildId,
        Long channelId,
        long openerId,
        TicketType type,
        String steamId,
        String subject,
        String description,
        Long accusedUserId,
        String evidence,
        String status,
        Long claimedBy,
        OffsetDateTime createdAt
) {}
