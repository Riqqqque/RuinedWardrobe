package dev.rique.ruinedwardrobe.storage;

import dev.rique.ruinedwardrobe.config.DatabaseType;
import dev.rique.ruinedwardrobe.config.PluginConfig;
import dev.rique.ruinedwardrobe.core.WardrobeAuditLogger;
import dev.rique.ruinedwardrobe.util.ItemStackDataSerializer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class MigrationService {

    private static final Executor CLEANUP_EXECUTOR = runnable -> {
        Thread thread = new Thread(runnable, "RuinedWardrobe-Migration-Cleanup");
        thread.setDaemon(true);
        thread.start();
    };

    private final JavaPlugin plugin;
    private final PluginConfig pluginConfig;
    private final WardrobeRepository sourceRepository;

    public MigrationService(JavaPlugin plugin, PluginConfig pluginConfig, WardrobeRepository sourceRepository) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.sourceRepository = sourceRepository;
    }

    public CompletableFuture<MigrationResult> migrate(DatabaseType targetType, boolean dryRun) {
        return migrate(targetType, dryRun, false);
    }

    public CompletableFuture<MigrationResult> migrate(DatabaseType targetType, boolean dryRun, boolean force) {
        DatabaseType sourceType = pluginConfig.storageSettings().type();
        if (sourceType == targetType) {
            return CompletableFuture.completedFuture(new MigrationResult(false, 0, 0, 0, "Source and target storage are the same"));
        }

        return sourceRepository.readSnapshot().thenCompose(rawSnapshot -> {
            WardrobeRepository.MigrationSnapshot snapshot = SnapshotMigrationSupport.normalize(rawSnapshot);
            int playerCount = snapshot.players().size();
            int setCount = snapshot.sets().size();
            int metaCount = snapshot.metaRows().size();
            String sourceDigest = SnapshotMigrationSupport.snapshotDigest(snapshot);

            if (dryRun) {
                return CompletableFuture.completedFuture(new MigrationResult(
                        true,
                        playerCount,
                        setCount,
                        metaCount,
                        "Dry-run complete. Snapshot digest: " + sourceDigest));
            }

            File backupFile;
            try {
                backupFile = SnapshotMigrationSupport.writeBackup(
                        plugin.getDataFolder(),
                        "migration-" + sourceType.name().toLowerCase() + "-to-" + targetType.name().toLowerCase(),
                        Map.of(
                                "source-storage", sourceType.name(),
                                "target-storage", targetType.name()),
                        snapshot);
            } catch (IOException ex) {
                return CompletableFuture.completedFuture(new MigrationResult(
                        false,
                        playerCount,
                        setCount,
                        metaCount,
                        "Migration aborted: failed to write backup file (" + ex.getMessage() + ")"));
            }

            PluginConfig targetConfig = pluginConfig.withStorageType(targetType);
            DatabaseManager targetManager = new DatabaseManager(targetConfig);
            try {
                targetManager.initialize(plugin.getDataFolder());
            } catch (RuntimeException ex) {
                targetManager.shutdown();
                return CompletableFuture.completedFuture(new MigrationResult(
                        false,
                        playerCount,
                        setCount,
                        metaCount,
                        "Migration aborted after backup " + backupFile.getAbsolutePath()
                                + ": target storage unavailable (" + SnapshotMigrationSupport.rootMessage(ex) + ")"));
            }
            SqlWardrobeRepository targetRepository = new SqlWardrobeRepository(
                    plugin,
                    targetManager,
                    new ItemStackDataSerializer(),
                    WardrobeAuditLogger.disabled());

            CompletableFuture<MigrationResult> migration = targetRepository.initializeSchema()
                    .thenCompose(ignored -> targetRepository.readSnapshot())
                    .thenCompose(rawTargetSnapshot -> {
                        WardrobeRepository.MigrationSnapshot targetSnapshot = SnapshotMigrationSupport.normalize(rawTargetSnapshot);
                        boolean targetHasData = hasStoredWardrobeData(targetSnapshot);
                        if (targetHasData && !force) {
                            return CompletableFuture.completedFuture(new MigrationResult(
                                    false,
                                    playerCount,
                                    setCount,
                                    metaCount,
                                    "Migration aborted after backup " + backupFile.getAbsolutePath()
                                            + ": target " + targetType.name()
                                            + " already contains " + targetSnapshot.players().size()
                                            + " players and " + targetSnapshot.sets().size()
                                            + " sets. Re-run with --force to overwrite it."));
                        }

                        CompletableFuture<File> targetBackupFuture = targetHasData
                                ? writeTargetBackup(sourceType, targetType, targetSnapshot)
                                : CompletableFuture.completedFuture(null);

                        return targetBackupFuture
                                .thenCompose(targetBackup -> targetRepository.writeSnapshot(snapshot)
                                        .thenCompose(ignored -> targetRepository.readSnapshot())
                                        .thenApply(copiedSnapshot -> verifyMigration(
                                                copiedSnapshot,
                                                sourceDigest,
                                                backupFile,
                                                targetBackup,
                                                playerCount,
                                                setCount,
                                                metaCount)));
                    })
                    .exceptionally(throwable -> new MigrationResult(
                            false,
                            playerCount,
                            setCount,
                            metaCount,
                            "Migration failed after backup " + backupFile.getAbsolutePath() + ": "
                                    + SnapshotMigrationSupport.rootMessage(throwable)));

            return migration.thenCompose(result -> shutdownTarget(targetManager).thenApply(ignored -> result));
        });
    }

    private CompletableFuture<File> writeTargetBackup(
            DatabaseType sourceType,
            DatabaseType targetType,
            WardrobeRepository.MigrationSnapshot targetSnapshot) {
        try {
            return CompletableFuture.completedFuture(SnapshotMigrationSupport.writeBackup(
                    plugin.getDataFolder(),
                    "migration-target-" + targetType.name().toLowerCase() + "-before-"
                            + sourceType.name().toLowerCase() + "-copy",
                    Map.of(
                            "source-storage", sourceType.name(),
                            "target-storage", targetType.name(),
                            "backup-reason", "target-overwrite"),
                    targetSnapshot));
        } catch (IOException ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    private MigrationResult verifyMigration(
            WardrobeRepository.MigrationSnapshot copiedSnapshot,
            String sourceDigest,
            File sourceBackup,
            File targetBackup,
            int playerCount,
            int setCount,
            int metaCount) {
        WardrobeRepository.MigrationSnapshot normalizedTarget = SnapshotMigrationSupport.normalize(copiedSnapshot);
        String targetDigest = SnapshotMigrationSupport.snapshotDigest(normalizedTarget);
        boolean ok = sourceDigest.equals(targetDigest);
        String backupText = "Backup: " + sourceBackup.getAbsolutePath();
        if (targetBackup != null) {
            backupText += " Target backup: " + targetBackup.getAbsolutePath();
        }
        String message = ok
                ? "Migration complete. " + backupText + " Digest: " + targetDigest
                : "Migration verification failed. " + backupText
                + " Source digest: " + sourceDigest + " Target digest: " + targetDigest;
        return new MigrationResult(ok, playerCount, setCount, metaCount, message);
    }

    private boolean hasStoredWardrobeData(WardrobeRepository.MigrationSnapshot snapshot) {
        return !snapshot.players().isEmpty() || !snapshot.sets().isEmpty();
    }

    private CompletableFuture<Void> shutdownTarget(DatabaseManager targetManager) {
        return CompletableFuture.runAsync(targetManager::shutdown, CLEANUP_EXECUTOR)
                .exceptionally(ignored -> null);
    }

    public record MigrationResult(
            boolean success,
            int players,
            int sets,
            int metaRows,
            String message
    ) {
    }
}
