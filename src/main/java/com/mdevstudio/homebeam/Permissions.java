package com.mdevstudio.homebeam;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

public final class Permissions {

    public static final String COMMAND = "homebeam.command.";
    public static final String DEATH_MARK = "homebeam.death-mark";
    public static final String BYPASS_WARMUP = "homebeam.bypass.warmup";
    public static final String BYPASS_COOLDOWN = "homebeam.bypass.cooldown";
    public static final int UNLIMITED = Integer.MAX_VALUE;

    private static final String LIMIT = "homebeam.limit.";
    private static final String LIMIT_UNLIMITED = LIMIT + "unlimited";

    private Permissions() {
    }

    // The highest homebeam.limit.<n> wins, so a donor rank adds to the default rather than fighting it.
    public static int homeLimit(Player player, int defaultLimit) {
        if (player.hasPermission(LIMIT_UNLIMITED)) {
            return UNLIMITED;
        }
        int limit = defaultLimit;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String permission = info.getPermission();
            if (info.getValue() && permission.startsWith(LIMIT)) {
                try {
                    limit = Math.max(limit, Integer.parseInt(permission.substring(LIMIT.length())));
                } catch (NumberFormatException ignored) {
                    // homebeam.limit.unlimited and typos simply do not raise the limit
                }
            }
        }
        return limit;
    }
}
