package dev.rique.ruinedwardrobe.core;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import dev.rique.ruinedwardrobe.cache.WardrobeCache;
import dev.rique.ruinedwardrobe.scheduler.SchedulerAdapter;
import dev.rique.ruinedwardrobe.storage.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class HealthMonitor {

    private final JavaPlugin plugin;
    private final SchedulerAdapter schedulerAdapter;
    private final WardrobeCache cache;
    private final DatabaseManager databaseManager;
    private final VersionSyncService versionSyncService;
    private final long intervalSeconds;
    private SchedulerAdapter.TaskHandle taskHandle;

    public HealthMonitor(
            JavaPlugin plugin,
            SchedulerAdapter schedulerAdapter,
            WardrobeCache cache,
            DatabaseManager databaseManager,
            VersionSyncService versionSyncService,
            long intervalSeconds
    ) {
        this.plugin = plugin;
        this.schedulerAdapter = schedulerAdapter;
        this.cache = cache;
        this.databaseManager = databaseManager;
        this.versionSyncService = versionSyncService;
        this.intervalSeconds = intervalSeconds;
    }

    public void start() {
        long ticks = Math.max(20L, intervalSeconds * 20L);
        taskHandle = schedulerAdapter.runTimerAsync(this::logHealth, ticks, ticks);
    }

    public void stop() {
        if (taskHandle != null) {
            taskHandle.cancel();
        }
    }

    private void logHealth() {
        CacheStats stats = cache.stats();
        plugin.getLogger().info(String.format(
                "Health | cacheSize=%d hitRate=%.2f dbQueue=%d dbLatencyMs=%.2f activeConnections=%d syncPollMs=%d",
                cache.size(),
                stats.hitRate(),
                databaseManager.queueDepth(),
                databaseManager.averageLatencyMs(),
                databaseManager.poolActiveConnections(),
                versionSyncService.lastPollMs()
        ));
    }
}

