package com.islaspider.bot.service;

import com.islaspider.bot.config.BotConfig;
import com.islaspider.bot.model.Ticket;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

public final class PermissionService {
    private final BotConfig config;

    public PermissionService(BotConfig config) {
        this.config = config;
    }

    public boolean isStaff(Member member) {
        if (member == null) return false;
        return member.hasPermission(Permission.ADMINISTRATOR)
                || member.getRoles().stream().anyMatch(role ->
                role.getIdLong() == config.staffRoleId() || role.getIdLong() == config.moderatorRoleId());
    }

    public boolean isModerator(Member member) {
        if (member == null) return false;
        return member.hasPermission(Permission.ADMINISTRATOR, Permission.MODERATE_MEMBERS)
                || member.getRoles().stream().anyMatch(role -> role.getIdLong() == config.moderatorRoleId());
    }

    public boolean canManageTicket(Member member, Ticket ticket) {
        return ticket.type().requiresModeration() ? isModerator(member) : isStaff(member);
    }
}

