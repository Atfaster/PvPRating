package dev.pvprating.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminCommandAccessTest {
    @Test
    void deniesAllSourcesWithoutAdminPermission() {
        assertFalse(AdminCommandAccess.canRunAdminCommand(false, true, false, true));
        assertFalse(AdminCommandAccess.canRunAdminCommand(false, false, true, true));
        assertFalse(AdminCommandAccess.canRunAdminCommand(false, false, false, true));
    }

    @Test
    void allowsOperatorPlayerAndServerSource() {
        assertTrue(AdminCommandAccess.canRunAdminCommand(true, true, false, false));
        assertTrue(AdminCommandAccess.canRunAdminCommand(true, false, true, false));
    }

    @Test
    void deniesCommandBlockLikeSourceByDefault() {
        assertFalse(AdminCommandAccess.canRunAdminCommand(true, false, false, false));
    }

    @Test
    void allowsCommandBlockLikeSourceWhenExplicitlyEnabled() {
        assertTrue(AdminCommandAccess.canRunAdminCommand(true, false, false, true));
    }
}
