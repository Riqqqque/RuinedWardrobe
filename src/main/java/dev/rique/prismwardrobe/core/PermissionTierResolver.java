package dev.rique.prismwardrobe.core;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

public final class PermissionTierResolver {

    private static final String PREFIX = "prismwardrobe.slots.";

    public int resolve(Player player, int defaultSlots) {
        int best = defaultSlots;
        for (PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
            if (!attachment.getValue()) {
                continue;
            }
            String permission = attachment.getPermission().toLowerCase();
            if (!permission.startsWith(PREFIX)) {
                continue;
            }
            String suffix = permission.substring(PREFIX.length());
            try {
                int value = Integer.parseInt(suffix);
                best = Math.max(best, value);
            } catch (NumberFormatException ignored) {
            }
        }
        return best;
    }
}

