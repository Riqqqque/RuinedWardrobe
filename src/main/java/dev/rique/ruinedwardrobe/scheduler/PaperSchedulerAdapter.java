package dev.rique.ruinedwardrobe.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PaperSchedulerAdapter implements SchedulerAdapter {

    private final JavaPlugin plugin;

    public PaperSchedulerAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public TaskHandle runAsync(Runnable runnable) {
        BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        return task::cancel;
    }

    @Override
    public TaskHandle runGlobal(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return () -> {
            };
        }
        BukkitTask task = Bukkit.getScheduler().runTask(plugin, runnable);
        return task::cancel;
    }

    @Override
    public TaskHandle runPlayer(Player player, Runnable runnable) {
        return runPlayer(player, runnable, () -> {
        });
    }

    @Override
    public TaskHandle runPlayer(Player player, Runnable runnable, Runnable retired) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return () -> {
            };
        }
        BukkitTask task = Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                runnable.run();
            } else {
                retired.run();
            }
        });
        return task::cancel;
    }

    @Override
    public TaskHandle runLocation(Location location, Runnable runnable) {
        return runGlobal(runnable);
    }

    @Override
    public TaskHandle runTimerAsync(Runnable runnable, long delayTicks, long periodTicks) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks);
        return task::cancel;
    }

    @Override
    public void shutdown() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}

