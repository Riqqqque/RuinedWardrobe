package dev.rique.prismwardrobe.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
        ScheduledTask task = player.getScheduler().run(plugin, ignored -> runnable.run(), null);
        return task::cancel;
    }

    @Override
    public TaskHandle runTimerAsync(Runnable runnable, long delayTicks, long periodTicks) {
        long delayMs = delayTicks * 50L;
        long periodMs = periodTicks * 50L;
        ScheduledTask task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, ignored -> runnable.run(), delayMs, periodMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        return task::cancel;
    }

    @Override
    public void shutdown() {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }
}

