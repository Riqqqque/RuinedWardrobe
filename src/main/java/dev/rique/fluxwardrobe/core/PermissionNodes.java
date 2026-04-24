package dev.rique.fluxwardrobe.core;

import org.bukkit.command.CommandSender;

public final class PermissionNodes {

    public static final String NAMESPACE = "fluxwardrobe";

    private PermissionNodes() {
    }

    public static boolean has(CommandSender sender, String suffix) {
        return sender.hasPermission(node(suffix));
    }

    public static String node(String suffix) {
        return NAMESPACE + "." + suffix;
    }
}
