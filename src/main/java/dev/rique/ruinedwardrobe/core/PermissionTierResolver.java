package dev.rique.ruinedwardrobe.core;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

public final class PermissionTierResolver {

    private static final String SLOT_PREFIX = PermissionNodes.node("slots.");

    public int resolve(Player player, int defaultSlots) {
        int best = defaultSlots;
        for (PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
            if (!attachment.getValue()) {
                continue;
            }
            String permission = attachment.getPermission().toLowerCase();
            if (!permission.startsWith(SLOT_PREFIX)) {
                continue;
            }
            String suffix = permission.substring(SLOT_PREFIX.length());
            try {
                int value = Integer.parseInt(suffix);
                best = Math.max(best, value);
            } catch (NumberFormatException ignored) {
            }
        }
        return best;
    }
}
