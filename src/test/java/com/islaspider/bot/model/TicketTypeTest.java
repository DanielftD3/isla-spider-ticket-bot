package com.islaspider.bot.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketTypeTest {
    @Test
    void resolvesEveryPublicCode() {
        assertEquals(TicketType.BUG, TicketType.fromCode("bug"));
        assertEquals(TicketType.HELP, TicketType.fromCode("HELP"));
        assertEquals(TicketType.REPORT, TicketType.fromCode("report"));
        assertEquals(TicketType.APPEAL, TicketType.fromCode("appeal"));
    }

    @Test
    void rejectsUnknownCode() {
        assertThrows(IllegalArgumentException.class, () -> TicketType.fromCode("otro"));
    }

    @Test
    void numbersChannelsWithThePersistentCaseId() {
        assertEquals("apelacion-0007", TicketType.APPEAL.channelName(7));
        assertEquals("reporte-10000", TicketType.REPORT.channelName(10000));
        assertThrows(IllegalArgumentException.class, () -> TicketType.HELP.channelName(0));
    }
}

