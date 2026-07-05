package dev.pvprating.utils;

public class AdminCommandAccess {
    public static boolean canRunAdminCommand(
            boolean hasAdminPermission,
            boolean playerSource,
            boolean serverSource,
            boolean commandBlockAdminCommandsAllowed
    ) {
        if (!hasAdminPermission) return false;
        if (playerSource || serverSource) return true;
        return commandBlockAdminCommandsAllowed;
    }
}
