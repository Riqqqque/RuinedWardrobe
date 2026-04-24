package dev.rique.fluxwardrobe.storage;

import dev.rique.fluxwardrobe.config.DatabaseType;
import dev.rique.fluxwardrobe.config.MessageFormatMode;
import dev.rique.fluxwardrobe.config.PluginConfig;
import dev.rique.fluxwardrobe.util.ItemStackDataSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlWardrobeRepositoryTest {

    @Test
    void readOnlyProfileLookupsDoNotCreatePlayerRows(@TempDir Path tempDir) {
        DatabaseManager manager = createManager(tempDir);
        UUID playerId = UUID.randomUUID();

        try {
            SqlWardrobeRepository repository = createRepository(tempDir, manager);
            repository.initializeSchema().join();

            repository.loadProfile(playerId).join();
            repository.getBonusSlots(playerId).join();

            int playerRows = manager.runQuery(connection -> countPlayerRows(connection, playerId)).join();

            assertEquals(0, playerRows);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void deletingSelectedSetClearsStoredSelectedSlot(@TempDir Path tempDir) {
        DatabaseManager manager = createManager(tempDir);
        UUID playerId = UUID.randomUUID();

        try {
            SqlWardrobeRepository repository = createRepository(tempDir, manager);
            repository.initializeSchema().join();
            seedCurrentSelectedSet(manager, playerId, 2);

            repository.deleteSet(playerId, 2).join();

            int selectedSlot = manager.runQuery(connection -> selectedSlot(connection, playerId)).join();

            assertEquals(-1, selectedSlot);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void initializeSchemaImportsLegacyTablesAndBacksThemUp(@TempDir Path tempDir) throws Exception {
        DatabaseManager manager = createManager(tempDir);
        UUID playerId = UUID.randomUUID();

        try {
            seedLegacySchema(manager, playerId);

            JavaPlugin plugin = mock(JavaPlugin.class);
            when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
            when(plugin.getLogger()).thenReturn(Logger.getLogger("SqlWardrobeRepositoryTest"));

            SqlWardrobeRepository repository = new SqlWardrobeRepository(plugin, manager, new ItemStackDataSerializer());
            repository.initializeSchema().join();

            WardrobeRepository.MigrationSnapshot snapshot = repository.readSnapshot().join();

            assertEquals(1, snapshot.players().size());
            assertEquals(1, snapshot.sets().size());

            WardrobeRepository.PlayerRow playerRow = snapshot.players().getFirst();
            WardrobeRepository.SetRow setRow = snapshot.sets().getFirst();

            assertEquals(playerId, playerRow.playerId());
            assertEquals(0, playerRow.bonusSlots());
            assertEquals(-1, playerRow.selectedSlot());
            assertEquals(0L, playerRow.version());
            assertEquals(0L, playerRow.updatedAt());

            assertEquals(playerId, setRow.playerId());
            assertEquals(1, setRow.slot());
            assertEquals("Slot 1", setRow.name());
            assertEquals("b64:legacy-payload", setRow.helmetData());
            assertEquals(10L, setRow.createdAt());
            assertEquals(10L, setRow.updatedAt());
            assertEquals(0L, setRow.version());

            Set<String> metaKeys = snapshot.metaRows().stream().map(WardrobeRepository.MetaRow::key).collect(java.util.stream.Collectors.toSet());
            assertTrue(metaKeys.contains("legacy-note"));
            assertTrue(metaKeys.contains("schema_version"));
            assertTrue(metaKeys.contains("item_payload_format"));

            boolean legacyPlayersTableExists = manager.runQuery(connection -> tableExists(connection, "pw_players")).join();
            boolean legacySetsTableExists = manager.runQuery(connection -> tableExists(connection, "pw_sets")).join();
            boolean legacyMetaTableExists = manager.runQuery(connection -> tableExists(connection, "pw_meta")).join();
            assertFalse(legacyPlayersTableExists);
            assertFalse(legacySetsTableExists);
            assertFalse(legacyMetaTableExists);

            Path backupDirectory = tempDir.resolve("backups");
            assertTrue(Files.exists(backupDirectory));
            try (var files = Files.list(backupDirectory)) {
                assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("legacy-schema-import-")));
            }
        } finally {
            manager.shutdown();
        }
    }

    private void seedLegacySchema(DatabaseManager manager, UUID playerId) {
        manager.runWrite(connection -> {
            try (var players = connection.prepareStatement("""
                    CREATE TABLE pw_players (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        bonus_slots INTEGER NOT NULL DEFAULT 0,
                        selected_slot INTEGER NOT NULL DEFAULT -1,
                        version BIGINT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL
                    )
                    """);
                 var sets = connection.prepareStatement("""
                    CREATE TABLE pw_sets (
                        player_uuid VARCHAR(36) NOT NULL,
                        slot_index INTEGER NOT NULL,
                        slot_name VARCHAR(48) NOT NULL,
                        favorite INTEGER NOT NULL DEFAULT 0,
                        helmet_json TEXT NULL,
                        chest_json TEXT NULL,
                        legs_json TEXT NULL,
                        boots_json TEXT NULL,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        version BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (player_uuid, slot_index)
                    )
                    """);
                 var meta = connection.prepareStatement("""
                    CREATE TABLE pw_meta (
                        `key` VARCHAR(64) PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """)) {
                players.execute();
                sets.execute();
                meta.execute();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

            try (var insertPlayer = connection.prepareStatement("""
                    INSERT INTO pw_players (player_uuid, bonus_slots, selected_slot, version, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """);
                 var insertSet = connection.prepareStatement("""
                    INSERT INTO pw_sets (player_uuid, slot_index, slot_name, favorite, helmet_json, chest_json, legs_json, boots_json, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
                 var insertMeta = connection.prepareStatement("""
                    INSERT INTO pw_meta (`key`, value)
                    VALUES (?, ?)
                    """)) {
                insertPlayer.setString(1, playerId.toString());
                insertPlayer.setInt(2, -4);
                insertPlayer.setInt(3, 2);
                insertPlayer.setLong(4, -8L);
                insertPlayer.setLong(5, -6L);
                insertPlayer.executeUpdate();

                insertSet.setString(1, playerId.toString());
                insertSet.setInt(2, 1);
                insertSet.setString(3, "   ");
                insertSet.setInt(4, 1);
                insertSet.setString(5, "b64:legacy-payload");
                insertSet.setString(6, null);
                insertSet.setString(7, null);
                insertSet.setString(8, null);
                insertSet.setLong(9, 10L);
                insertSet.setLong(10, 5L);
                insertSet.setLong(11, -9L);
                insertSet.executeUpdate();

                insertMeta.setString(1, "legacy-note");
                insertMeta.setString(2, "true");
                insertMeta.executeUpdate();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return null;
        }).join();
    }

    private boolean tableExists(java.sql.Connection connection, String tableName) {
        try (ResultSet rs = connection.getMetaData().getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private int countPlayerRows(java.sql.Connection connection, UUID playerId) {
        try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM fw_players WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private int selectedSlot(java.sql.Connection connection, UUID playerId) {
        try (var statement = connection.prepareStatement("SELECT selected_slot FROM fw_players WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt("selected_slot");
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void seedCurrentSelectedSet(DatabaseManager manager, UUID playerId, int slot) {
        manager.runWrite(connection -> {
            try (var insertPlayer = connection.prepareStatement("""
                    INSERT INTO fw_players (player_uuid, bonus_slots, selected_slot, version, updated_at)
                    VALUES (?, 0, ?, 1, 100)
                    """);
                 var insertSet = connection.prepareStatement("""
                    INSERT INTO fw_sets (player_uuid, slot_index, slot_name, favorite, helmet_data, chest_data, legs_data, boots_data, created_at, updated_at, version)
                    VALUES (?, ?, ?, 0, ?, NULL, NULL, NULL, 100, 100, 1)
                    """)) {
                insertPlayer.setString(1, playerId.toString());
                insertPlayer.setInt(2, slot);
                insertPlayer.executeUpdate();

                insertSet.setString(1, playerId.toString());
                insertSet.setInt(2, slot);
                insertSet.setString(3, "Slot " + slot);
                insertSet.setString(4, "yaml:item: invalid-for-delete-test");
                insertSet.executeUpdate();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return null;
        }).join();
    }

    private DatabaseManager createManager(Path tempDir) {
        PluginConfig pluginConfig = new PluginConfig(
                3,
                54,
                2,
                0L,
                false,
                "en_US",
                MessageFormatMode.BOTH,
                new PluginConfig.StorageSettings(
                        DatabaseType.SQLITE,
                        "data/wardrobe.db",
                        "127.0.0.1",
                        3306,
                        "fluxwardrobe",
                        "root",
                        "",
                        "useUnicode=true&characterEncoding=utf8&useSSL=false",
                        4,
                        1,
                        5000L),
                new PluginConfig.CacheSettings(1000L, 60L),
                new PluginConfig.SyncSettings(5L, 50),
                PluginConfig.RestrictionSettings.defaultSettings(),
                new PluginConfig.MetricsSettings(false, 0),
                new PluginConfig.IntegrationSettings(false, false, false),
                new PluginConfig.GuiBehaviorSettings(true, true, false, false, 0),
                new PluginConfig.AntiDupeSettings(false),
                new PluginConfig.DbExecutionSettings(64, 0, 10L, 1),
                120L
        );

        DatabaseManager manager = new DatabaseManager(pluginConfig);
        manager.initialize(tempDir.toFile());
        return manager;
    }

    private SqlWardrobeRepository createRepository(Path tempDir, DatabaseManager manager) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SqlWardrobeRepositoryTest"));
        return new SqlWardrobeRepository(plugin, manager, new ItemStackDataSerializer());
    }
}
