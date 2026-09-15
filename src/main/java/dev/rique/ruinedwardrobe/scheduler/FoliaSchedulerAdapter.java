package dev.rique.ruinedwardrobe.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public final class FoliaSchedulerAdapter implements SchedulerAdapter {

    private final JavaPlugin plugin;

    public FoliaSchedulerAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public TaskHandle runAsync(Runnable runnable) {
        ScheduledTask task = Bukkit.getAsyncScheduler().runNow(plugin, ignored -> runnable.run());
        return task::cancel;
    }

    @Override
    public TaskHandle runGlobal(Runnable runnable) {
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> runnable.run());
        return task::cancel;
    }

    @Override
    public TaskHandle runPlayer(Player player, Runnable runnable) {
        return runPlayer(player, runnable, () -> {
        });
    }

    @Override
    public TaskHandle runPlayer(Player player, Runnable runnable, Runnable retired) {
        ScheduledTask task = player.getScheduler().run(
                plugin,
                ignored -> runnable.run(),
                retired);
        if (task == null) {
            retired.run();
            return () -> {
            };
        }
        return task::cancel;
    }

    @Override
    public TaskHandle runLocation(Location location, Runnable runnable) {
        ScheduledTask task = Bukkit.getRegionScheduler().run(plugin, location, ignored -> runnable.run());
        return task::cancel;
    }

    @Override
    public TaskHandle runTimerAsync(Runnable runnable, long delayTicks, long periodTicks) {
        long delayMs = delayTicks * 50L;
        long periodMs = periodTicks * 50L;
        ScheduledTask task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, ignored -> runnable.run(), delayMs, periodMs, TimeUnit.MILLISECONDS);
        return task::cancel;
    }

    @Override
    public void shutdown() {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }
}

