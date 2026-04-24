package dev.rique.fluxwardrobe.storage;

import dev.rique.fluxwardrobe.config.DatabaseType;
import dev.rique.fluxwardrobe.config.PluginConfig;
import dev.rique.fluxwardrobe.util.ItemStackDataSerializer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class MigrationService {

    private final JavaPlugin plugin;
    private final PluginConfig pluginConfig;
    private final WardrobeRepository sourceRepository;

    public MigrationService(JavaPlugin plugin, PluginConfig pluginConfig, WardrobeRepository sourceRepository) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.sourceRepository = sourceRepository;
    }

    public CompletableFuture<MigrationResult> migrate(DatabaseType targetType, boolean dryRun) {
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
            SqlWardrobeRepository targetRepository = new SqlWardrobeRepository(plugin, targetManager, new ItemStackDataSerializer());

            return targetRepository.initializeSchema()
                    .thenCompose(ignored -> targetRepository.writeSnapshot(snapshot))
                    .thenCompose(ignored -> targetRepository.readSnapshot())
                    .handle((copiedSnapshot, throwable) -> {
                        targetManager.shutdown();
                        if (throwable != null) {
                            return new MigrationResult(
                                    false,
                                    playerCount,
                                    setCount,
                                    metaCount,
                                    "Migration failed after backup " + backupFile.getAbsolutePath() + ": "
                                            + SnapshotMigrationSupport.rootMessage(throwable));
                        }

                        WardrobeRepository.MigrationSnapshot normalizedTarget = SnapshotMigrationSupport.normalize(copiedSnapshot);
                        String targetDigest = SnapshotMigrationSupport.snapshotDigest(normalizedTarget);
                        boolean ok = sourceDigest.equals(targetDigest);
                        String message = ok
                                ? "Migration complete. Backup: " + backupFile.getAbsolutePath() + " Digest: " + targetDigest
                                : "Migration verification failed. Backup: " + backupFile.getAbsolutePath()
                                + " Source digest: " + sourceDigest + " Target digest: " + targetDigest;
                        return new MigrationResult(ok, playerCount, setCount, metaCount, message);
                    });
        });
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
