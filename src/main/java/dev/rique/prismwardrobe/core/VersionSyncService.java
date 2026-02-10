package dev.rique.prismwardrobe.core;

import dev.rique.prismwardrobe.api.model.WardrobeProfile;
import dev.rique.prismwardrobe.config.PluginConfig;
import dev.rique.prismwardrobe.scheduler.SchedulerAdapter;
import dev.rique.prismwardrobe.storage.WardrobeRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VersionSyncService {

    private final JavaPlugin plugin;
    private final SchedulerAdapter schedulerAdapter;
    private final WardrobeRepository repository;
    private final WardrobeServiceImpl wardrobeService;
    private final PluginConfig.SyncSettings syncSettings;
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private SchedulerAdapter.TaskHandle taskHandle;
    private volatile long lastPollMs;

    public VersionSyncService(
            JavaPlugin plugin,
            SchedulerAdapter schedulerAdapter,
            WardrobeRepository repository,
            WardrobeServiceImpl wardrobeService,
            PluginConfig.SyncSettings syncSettings
    ) {
        this.plugin = plugin;
        this.schedulerAdapter = schedulerAdapter;
        this.repository = repository;
        this.wardrobeService = wardrobeService;
        this.syncSettings = syncSettings;
    }

    public void start() {
        long periodTicks = Math.max(20L, syncSettings.pollSeconds() * 20L);
        taskHandle = schedulerAdapter.runTimerAsync(this::poll, periodTicks, periodTicks);
    }

    public void stop() {
        if (taskHandle != null) {
            taskHandle.cancel();
        }
    }

    public long lastPollMs() {
        return lastPollMs;
    }

    private void poll() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        long start = System.currentTimeMillis();
        schedulerAdapter.runGlobal(() -> {
            try {
                List<UUID> onlineIds = new ArrayList<>();
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlineIds.add(onlinePlayer.getUniqueId());
                }
                if (onlineIds.isEmpty()) {
                    finishPoll(start);
                    return;
                }
                pollChunks(onlineIds, start);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Version sync poll failed before DB phase: " + throwable.getMessage());
                finishPoll(start);
            }
        });
    }

    private void pollChunks(List<UUID> onlineIds, long start) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < onlineIds.size(); i += syncSettings.batchSize()) {
            int end = Math.min(onlineIds.size(), i + syncSettings.batchSize());
            List<UUID> chunk = onlineIds.subList(i, end);
            CompletableFuture<Void> future = repository.fetchVersions(chunk).thenCompose(versionMap -> {
                List<CompletableFuture<WardrobeProfile>> refreshTasks = new ArrayList<>();
                for (UUID playerId : chunk) {
                    Long remoteVersion = versionMap.get(playerId);
                    if (remoteVersion == null) {
                        continue;
                    }
                    long localVersion = wardrobeService.cachedProfile(playerId).map(WardrobeProfile::version).orElse(-1L);
                    if (WardrobeSafetyDecisions.shouldRefreshFromRemote(remoteVersion, localVersion)) {
                        refreshTasks.add(repository.loadProfile(playerId));
                    }
                }
                if (refreshTasks.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                return CompletableFuture.allOf(refreshTasks.toArray(new CompletableFuture[0]))
                        .thenAccept(ignored -> refreshTasks.forEach(task -> task.thenAccept(wardrobeService::primeProfile)));
            }).exceptionally(ex -> {
                plugin.getLogger().warning("Version sync batch failed: " + ex.getMessage());
                return null;
            });
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((ignored, throwable) -> finishPoll(start));
    }

    private void finishPoll(long startMs) {
        polling.set(false);
        lastPollMs = System.currentTimeMillis() - startMs;
    }
}
