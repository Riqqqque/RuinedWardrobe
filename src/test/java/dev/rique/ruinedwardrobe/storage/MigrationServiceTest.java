package dev.rique.ruinedwardrobe.storage;

import dev.rique.ruinedwardrobe.config.DatabaseType;
import dev.rique.ruinedwardrobe.config.MessageFormatMode;
import dev.rique.ruinedwardrobe.config.PluginConfig;
import dev.rique.ruinedwardrobe.core.WardrobeAuditLogger;
import dev.rique.ruinedwardrobe.util.ItemStackDataSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MigrationServiceTest {

    @Test
    void refusesToOverwriteNonEmptyTargetWithoutForce(@TempDir Path tempDir) throws Exception {
        PluginConfig sourceConfig = config(DatabaseType.MYSQL);
        UUID sourcePlayer = UUID.randomUUID();
        UUID targetPlayer = UUID.randomUUID();
        seedTarget(tempDir, sourceConfig.withStorageType(DatabaseType.SQLITE), snapshot(targetPlayer, "target-payload"));

        WardrobeRepository sourceRepository = mock(WardrobeRepository.class);
        when(sourceRepository.readSnapshot()).thenReturn(CompletableFuture.completedFuture(snapshot(sourcePlayer, "source-payload")));

        MigrationService service = new MigrationService(plugin(tempDir), sourceConfig, sourceRepository);

        MigrationService.MigrationResult result = service.migrate(DatabaseType.SQLITE, false).join();

        assertFalse(result.success());
        assertTrue(result.message().contains("--force"));
        WardrobeRepository.MigrationSnapshot targetAfter = readTarget(tempDir, sourceConfig.withStorageType(DatabaseType.SQLITE));
        assertEquals(targetPlayer, targetAfter.players().getFirst().playerId());
        assertEquals("yaml:target-payload", targetAfter.sets().getFirst().helmetData());
    }

    @Test
    void forceMigrationBacksUpAndOverwritesNonEmptyTarget(@TempDir Path tempDir) throws Exception {
        PluginConfig sourceConfig = config(DatabaseType.MYSQL);
        UUID sourcePlayer = UUID.randomUUID();
        UUID targetPlayer = UUID.randomUUID();
        seedTarget(tempDir, sourceConfig.withStorageType(DatabaseType.SQLITE), snapshot(targetPlayer, "target-payload"));

        WardrobeRepository sourceRepository = mock(WardrobeRepository.class);
        when(sourceRepository.readSnapshot()).thenReturn(CompletableFuture.completedFuture(snapshot(sourcePlayer, "source-payload")));

        MigrationService service = new MigrationService(plugin(tempDir), sourceConfig, sourceRepository);

        MigrationService.MigrationResult result = service.migrate(DatabaseType.SQLITE, false, true).join();

        assertTrue(result.success());
        assertTrue(result.message().contains("Target backup:"));
        WardrobeRepository.MigrationSnapshot targetAfter = readTarget(tempDir, sourceConfig.withStorageType(DatabaseType.SQLITE));
        assertEquals(sourcePlayer, targetAfter.players().getFirst().playerId());
        assertEquals("yaml:source-payload", targetAfter.sets().getFirst().helmetData());

        Path backupDirectory = tempDir.resolve("backups");
        try (var backups = Files.list(backupDirectory)) {
            assertTrue(backups.anyMatch(path -> path.getFileName().toString().startsWith("migration-target-sqlite-before-mysql-copy-")));
        }
    }

    private void seedTarget(Path tempDir, PluginConfig targetConfig, WardrobeRepository.MigrationSnapshot snapshot) {
        DatabaseManager manager = new DatabaseManager(targetConfig);
        manager.initialize(tempDir.toFile());
        try {
            SqlWardrobeRepository repository = new SqlWardrobeRepository(
                    plugin(tempDir),
                    manager,
                    new ItemStackDataSerializer(),
                    WardrobeAuditLogger.disabled());
            repository.initializeSchema().join();
            repository.writeSnapshot(snapshot).join();
        } finally {
            manager.shutdown();
        }
    }

    private WardrobeRepository.MigrationSnapshot readTarget(Path tempDir, PluginConfig targetConfig) {
        DatabaseManager manager = new DatabaseManager(targetConfig);
        manager.initialize(tempDir.toFile());
        try {
            SqlWardrobeRepository repository = new SqlWardrobeRepository(
                    plugin(tempDir),
                    manager,
                    new ItemStackDataSerializer(),
                    WardrobeAuditLogger.disabled());
            repository.initializeSchema().join();
            return repository.readSnapshot().join();
        } finally {
            manager.shutdown();
        }
    }

    private WardrobeRepository.MigrationSnapshot snapshot(UUID playerId, String payload) {
        return new WardrobeRepository.MigrationSnapshot(
                List.of(new WardrobeRepository.PlayerRow(playerId, 0, 1, 1L, 100L)),
                List.of(new WardrobeRepository.SetRow(
                        playerId,
                        1,
                        "Slot 1",
                        false,
                        "yaml:" + payload,
                        null,
                        null,
                        null,
                        100L,
                        100L,
                        1L)),
                List.of(
                        new WardrobeRepository.MetaRow("schema_version", "1"),
                        new WardrobeRepository.MetaRow("item_payload_format", "yaml-v1")));
    }

    private JavaPlugin plugin(Path tempDir) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("MigrationServiceTest"));
        return plugin;
    }

    private PluginConfig config(DatabaseType storageType) {
        return new PluginConfig(
                3,
                54,
                2,
                0L,
                false,
                "en_US",
                MessageFormatMode.BOTH,
                new PluginConfig.StorageSettings(
                        storageType,
                        "data/wardrobe.db",
                        "127.0.0.1",
                        3306,
                        "ruinedwardrobe",
                        "root",
                        "",
                        "useUnicode=true&characterEncoding=utf8&useSSL=false",
                        4,
                        1,
                        5000L),
                new PluginConfig.CacheSettings(1000L, 60L),
                new PluginConfig.SyncSettings(5L, 50),
                new PluginConfig.SessionSettings(false, false),
                new PluginConfig.ArmorSyncSettings(true, 40L, 500, 10L),
                new PluginConfig.DeathSettings(false),
                PluginConfig.RestrictionSettings.defaultSettings(),
                new PluginConfig.MetricsSettings(false, 0),
                new PluginConfig.IntegrationSettings(false, false, false),
                new PluginConfig.GuiBehaviorSettings(true, true, false, false, 0),
                new PluginConfig.AntiDupeSettings(false),
                new PluginConfig.DbExecutionSettings(64, 0, 10L, 1),
                new PluginConfig.HealthSettings(true, 120L),
                new PluginConfig.AuditSettings(false, "logs", 4096, false, true, true));
    }
}
