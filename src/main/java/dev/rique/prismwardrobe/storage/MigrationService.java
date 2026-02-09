package dev.rique.prismwardrobe.storage;

import dev.rique.prismwardrobe.config.DatabaseType;
import dev.rique.prismwardrobe.config.PluginConfig;
import dev.rique.prismwardrobe.util.ItemStackDataSerializer;
import org.bukkit.plugin.java.JavaPlugin;

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

        return sourceRepository.readSnapshot().thenCompose(snapshot -> {
            int playerCount = snapshot.players().size();
            int setCount = snapshot.sets().size();
            int metaCount = snapshot.metaRows().size();

            if (dryRun) {
                return CompletableFuture.completedFuture(new MigrationResult(true, playerCount, setCount, metaCount, "Dry-run completed"));
            }

            PluginConfig targetConfig = pluginConfig.withStorageType(targetType);
            DatabaseManager targetManager = new DatabaseManager(targetConfig);
            targetManager.initialize(plugin.getDataFolder());
            SqlWardrobeRepository targetRepository = new SqlWardrobeRepository(plugin, targetManager, new ItemStackDataSerializer());

            return targetRepository.initializeSchema()
                    .thenCompose(ignored -> targetRepository.writeSnapshot(snapshot))
                    .thenCompose(ignored -> targetRepository.readSnapshot())
                    .handle((copiedSnapshot, throwable) -> {
                        targetManager.shutdown();
                        if (throwable != null) {
                            return new MigrationResult(false, playerCount, setCount, metaCount, "Migration failed: " + throwable.getMessage());
                        }
                        boolean ok = copiedSnapshot.players().size() == playerCount
                                && copiedSnapshot.sets().size() == setCount
                                && copiedSnapshot.metaRows().size() == metaCount;
                        String message = ok
                                ? "Migration complete"
                                : "Migration finished with mismatched row counts";
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
