package dev.rique.fluxwardrobe.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class SchedulerFactory {

    private SchedulerFactory() {
    }

    public static SchedulerAdapter create(JavaPlugin plugin) {
        boolean foliaDetected = isFolia();
        if (foliaDetected) {
            try {
                plugin.getLogger().info("Scheduler mode: Folia");
                return new FoliaSchedulerAdapter(plugin);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Folia scheduler initialization failed, falling back to Paper scheduler: " + throwable.getMessage());
            }
        }
        plugin.getLogger().info("Scheduler mode: Paper");
        return new PaperSchedulerAdapter(plugin);
    }

    private static boolean isFolia() {
        String serverName = Bukkit.getName();
        if ("Folia".equalsIgnoreCase(serverName)) {
            return true;
        }
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
