package dev.rique.ruinedwardrobe.storage;

import dev.rique.ruinedwardrobe.api.model.WardrobeProfile;
import dev.rique.ruinedwardrobe.api.model.WardrobeSet;
import dev.rique.ruinedwardrobe.core.WardrobeAuditLogger;
import dev.rique.ruinedwardrobe.util.ItemStackDataSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SqlWardrobeRepository implements WardrobeRepository {

    private static final String PLAYERS_TABLE = "rw_players";
    private static final String SETS_TABLE = "rw_sets";
    private static final String META_TABLE = "rw_meta";
    private static final String VERSION_INDEX = "idx_rw_players_version";
    private static final String SCHEMA_VERSION_KEY = "schema_version";
    private static final String ITEM_FORMAT_KEY = "item_payload_format";
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final int VERSION_FETCH_CHUNK_SIZE = 100;

    private static final SchemaLayout CURRENT_SCHEMA = new SchemaLayout(
            PLAYERS_TABLE,
            SETS_TABLE,
            META_TABLE,
            "helmet_data",
            "chest_data",
            "legs_data",
            "boots_data");
    private static final List<SchemaLayout> LEGACY_SCHEMAS = List.of(
            legacySchema('f', "helmet_data", "chest_data", "legs_data", "boots_data"),
            legacySchema('p', "helmet_json", "chest_json", "legs_json", "boots_json"));

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final ItemStackDataSerializer itemSerializer;
    private final WardrobeAuditLogger auditLogger;

    public SqlWardrobeRepository(
            JavaPlugin plugin,
            DatabaseManager databaseManager,
            ItemStackDataSerializer itemSerializer,
            WardrobeAuditLogger auditLogger) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.itemSerializer = itemSerializer;
        this.auditLogger = auditLogger;
    }

    private static SchemaLayout legacySchema(
            char prefix,
            String helmetColumn,
            String chestColumn,
            String legsColumn,
            String bootsColumn) {
        String tablePrefix = prefix + "w_";
        return new SchemaLayout(
                tablePrefix + "players",
                tablePrefix + "sets",
                tablePrefix + "meta",
                helmetColumn,
                chestColumn,
                legsColumn,
                bootsColumn);
    }

    @Override
    public CompletableFuture<Void> initializeSchema() {
        return databaseManager.runWrite(connection -> {
            try {
                return withTransaction(connection, () -> {
                    createMetaTable(connection);
                    createPlayersTable(connection);
                    createSetsTable(connection);
                    createVersionIndex(connection);

                    LegacyImportResult importResult = importLegacySchemaIfNeeded(connection);
                    upsertMeta(connection, SCHEMA_VERSION_KEY, String.valueOf(CURRENT_SCHEMA_VERSION));
                    upsertMeta(connection, ITEM_FORMAT_KEY, itemSerializer.storageFormatId());
                    repairData(connection);
                    return importResult.message();
                });
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }).thenAccept(message -> {
            if (message != null && !message.isBlank()) {
                plugin.getLogger().info(message);
            }
        });
    }

    @Override
    public CompletableFuture<WardrobeProfile> loadProfile(UUID playerId) {
        return databaseManager.runQuery(connection -> {
            try {
                PlayerState playerState = readPlayerState(connection, playerId);
                Map<Integer, WardrobeSet> sets = readSets(connection, playerId);
                int selectedSlot = playerState.selectedSlot();
                if (selectedSlot > 0) {
                    WardrobeSet selected = sets.get(selectedSlot);
                    if (selected == null || !selected.hasAnyArmorPiece()) {
                        selectedSlot = -1;
                    }
                }
                return new WardrobeProfile(playerId, playerState.bonusSlots(), selectedSlot, playerState.version(), sets);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveSet(UUID playerId, WardrobeSet wardrobeSet) {
        return databaseManager.runWrite(connection -> {
            try {
                withTransaction(connection, () -> {
                    ensurePlayerRowExists(connection, playerId);
                    long now = System.currentTimeMillis();

                    int changed;
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE rw_sets
                            SET slot_name=?, favorite=?, helmet_data=?, chest_data=?, legs_data=?, boots_data=?, updated_at=?, version=version+1
                            WHERE player_uuid=? AND slot_index=?
                            """)) {
                        bindSet(statement, wardrobeSet, now);
                        statement.setString(8, playerId.toString());
                        statement.setInt(9, wardrobeSet.slot());
                        changed = statement.executeUpdate();
                    }

                    if (changed == 0) {
                        try (PreparedStatement statement = connection.prepareStatement("""
                                INSERT INTO rw_sets (player_uuid, slot_index, slot_name, favorite, helmet_data, chest_data, legs_data, boots_data, created_at, updated_at, version)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """)) {
                            statement.setString(1, playerId.toString());
                            statement.setInt(2, wardrobeSet.slot());
                            statement.setString(3, sanitizeSetName(wardrobeSet.slot(), wardrobeSet.name()));
                            statement.setInt(4, wardrobeSet.favorite() ? 1 : 0);
                            statement.setString(5, itemSerializer.serialize(wardrobeSet.helmet()));
                            statement.setString(6, itemSerializer.serialize(wardrobeSet.chestplate()));
                            statement.setString(7, itemSerializer.serialize(wardrobeSet.leggings()));
                            statement.setString(8, itemSerializer.serialize(wardrobeSet.boots()));
                            statement.setLong(9, now);
                            statement.setLong(10, now);
                            statement.setLong(11, 1L);
                            statement.executeUpdate();
                        }
                    }

                    bumpVersion(connection, playerId, now);
                    return null;
                });
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> deleteSet(UUID playerId, int slot) {
        return databaseManager.runWrite(connection -> {
            try {
                withTransaction(connection, () -> {
                    ensurePlayerRowExists(connection, playerId);
                    try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + SETS_TABLE + " WHERE player_uuid=? AND slot_index=?")) {
                        statement.setString(1, playerId.toString());
                        statement.setInt(2, slot);
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE " + PLAYERS_TABLE + " SET selected_slot=-1 WHERE player_uuid=? AND selected_slot=?")) {
                        statement.setString(1, playerId.toString());
                        statement.setInt(2, slot);
                        statement.executeUpdate();
                    }
                    bumpVersion(connection, playerId, System.currentTimeMillis());
                    return null;
                });
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> renameSet(UUID playerId, int slot, String name) {
        return databaseManager.runWrite(connection -> {
            try {
                withTransaction(connection, () -> {
                    ensurePlayerRowExists(connection, playerId);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE " + SETS_TABLE + " SET slot_name=?, updated_at=?, version=version+1 WHERE player_uuid=? AND slot_index=?")) {
                        statement.setString(1, sanitizeSetName(slot, name));
                        statement.setLong(2, System.currentTimeMillis());
                        statement.setString(3, playerId.toString());
                        statement.setInt(4, slot);
                        statement.executeUpdate();
                    }
                    bumpVersion(connection, playerId, System.currentTimeMillis());
                    return null;
                });
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Integer> getBonusSlots(UUID playerId) {
        return databaseManager.runQuery(connection -> {
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT bonus_slots FROM " + PLAYERS_TABLE + " WHERE player_uuid=?")) {
                    statement.setString(1, playerId.toString());
                    try (ResultSet rs = statement.executeQuery()) {
                        return rs.next() ? rs.getInt("bonus_slots") : 0;
                    }
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> setBonusSlots(UUID playerId, int bonusSlots) {
        return databaseManager.runWrite(connection -> {
            try {
                return withTransaction(connection, () -> {
                    ensurePlayerRowExists(connection, playerId);
                    int oldValue = 0;
                    try (PreparedStatement select = connection.prepareStatement(
                            "SELECT bonus_slots FROM " + PLAYERS_TABLE + " WHERE player_uuid=?")) {
                        select.setString(1, playerId.toString());
                        try (ResultSet rs = select.executeQuery()) {
                            if (rs.next()) {
                                oldValue = rs.getInt("bonus_slots");
                            }
                        }
                    }
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE " + PLAYERS_TABLE + " SET bonus_slots=?, version=version+1, updated_at=? WHERE player_uuid=?")) {
                        update.setInt(1, Math.max(0, bonusSlots));
                        update.setLong(2, System.currentTimeMillis());
                        update.setString(3, playerId.toString());
                        update.executeUpdate();
                    }
                    return oldValue;
                });
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setSelectedSlot(UUID playerId, int selectedSlot) {
        return databaseManager.runWrite(connection -> {
            try {
                withTransaction(connection, () -> {
                    ensurePlayerRowExists(connection, playerId);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE " + PLAYERS_TABLE + " SET selected_slot=?, version=version+1, updated_at=? WHERE player_uuid=?")) {
                        statement.setInt(1, selectedSlot < 1 ? -1 : selectedSlot);
                        statement.setLong(2, System.currentTimeMillis());
                        statement.setString(3, playerId.toString());
                        statement.executeUpdate();
                    }
                    return null;
                });
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Map<UUID, Long>> fetchVersions(List<UUID> playerIds) {
        if (playerIds.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return databaseManager.runQuery(connection -> {
            try {
                Map<UUID, Long> versions = new HashMap<>();
                for (int i = 0; i < playerIds.size(); i += VERSION_FETCH_CHUNK_SIZE) {
                    int end = Math.min(playerIds.size(), i + VERSION_FETCH_CHUNK_SIZE);
                    List<UUID> chunk = playerIds.subList(i, end);
                    StringBuilder query = new StringBuilder("SELECT player_uuid, version FROM " + PLAYERS_TABLE + " WHERE player_uuid IN (");
                    for (int j = 0; j < chunk.size(); j++) {
                        query.append('?');
                        if (j + 1 < chunk.size()) {
                            query.append(',');
                        }
                    }
                    query.append(')');

                    try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
                        int index = 1;
                        for (UUID uuid : chunk) {
                            statement.setString(index++, uuid.toString());
                        }
                        try (ResultSet rs = statement.executeQuery()) {
                            while (rs.next()) {
                                versions.put(UUID.fromString(rs.getString("player_uuid")), rs.getLong("version"));
                            }
                        }
                    }
                }
                return versions;
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Override
    public CompletableFuture<Void> touchPlayer(UUID playerId) {
        return databaseManager.runWrite(connection -> {
            try {
                touchPlayerRow(connection, playerId);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<MigrationSnapshot> readSnapshot() {
        return databaseManager.runQuery(connection -> {
            try {
                return readSnapshot(connection, CURRENT_SCHEMA);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Override
    public CompletableFuture<Void> writeSnapshot(MigrationSnapshot snapshot) {
        return databaseManager.runWrite(connection -> {
            try {
                MigrationSnapshot normalized = SnapshotMigrationSupport.normalize(snapshot);
                withTransaction(connection, () -> {
                    clearTables(connection);
                    insertSnapshot(connection, normalized);
                    upsertMeta(connection, SCHEMA_VERSION_KEY, String.valueOf(CURRENT_SCHEMA_VERSION));
                    upsertMeta(connection, ITEM_FORMAT_KEY, itemSerializer.storageFormatId());
                    repairData(connection);
                    return null;
                });
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return null;
        });
    }

    private void createMetaTable(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                CREATE TABLE IF NOT EXISTS rw_meta (
                    `key` VARCHAR(64) PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """)) {
            statement.execute();
        }
    }

    private void createPlayersTable(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                CREATE TABLE IF NOT EXISTS rw_players (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    bonus_slots INTEGER NOT NULL DEFAULT 0,
                    selected_slot INTEGER NOT NULL DEFAULT -1,
                    version BIGINT NOT NULL DEFAULT 0,
                    updated_at BIGINT NOT NULL
                )
                """)) {
            statement.execute();
        }
    }

    private void createSetsTable(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                CREATE TABLE IF NOT EXISTS rw_sets (
                    player_uuid VARCHAR(36) NOT NULL,
                    slot_index INTEGER NOT NULL,
                    slot_name VARCHAR(48) NOT NULL,
                    favorite INTEGER NOT NULL DEFAULT 0,
                    helmet_data TEXT NULL,
                    chest_data TEXT NULL,
                    legs_data TEXT NULL,
                    boots_data TEXT NULL,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    version BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (player_uuid, slot_index)
                )
                """)) {
            statement.execute();
        }
    }

    private void createVersionIndex(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                CREATE INDEX IF NOT EXISTS idx_rw_players_version
                ON rw_players(version)
                """)) {
            statement.execute();
        }
    }

    private LegacyImportResult importLegacySchemaIfNeeded(Connection connection) throws SQLException, IOException {
        List<SchemaLayout> legacySchemas = existingLegacySchemas(connection);
        if (legacySchemas.isEmpty()) {
            return LegacyImportResult.none();
        }
        if (currentSchemaHasData(connection)) {
            return new LegacyImportResult("Detected legacy wardrobe tables, but RuinedWardrobe tables already contain data. Skipping automatic legacy import.");
        }

        SchemaLayout sourceSchema = null;
        MigrationSnapshot preparedLegacySnapshot = null;
        for (SchemaLayout legacySchema : legacySchemas) {
            MigrationSnapshot candidate = prepareLegacySnapshot(readSnapshot(connection, legacySchema));
            if (candidate.players().isEmpty() && candidate.sets().isEmpty() && candidate.metaRows().isEmpty()) {
                dropLegacySchema(connection, legacySchema);
                continue;
            }
            sourceSchema = legacySchema;
            preparedLegacySnapshot = candidate;
            break;
        }

        if (sourceSchema == null) {
            return new LegacyImportResult("Removed empty legacy wardrobe tables during startup cleanup.");
        }

        if (preparedLegacySnapshot.players().isEmpty()
                && preparedLegacySnapshot.sets().isEmpty()
                && preparedLegacySnapshot.metaRows().isEmpty()) {
            dropLegacySchema(connection, sourceSchema);
            return new LegacyImportResult("Removed empty legacy wardrobe tables during startup cleanup.");
        }

        File backupFile = SnapshotMigrationSupport.writeBackup(
                plugin.getDataFolder(),
                "legacy-schema-import",
                Map.of(
                        "source-schema", "legacy",
                        "target-schema", "current"),
                preparedLegacySnapshot);

        insertSnapshot(connection, preparedLegacySnapshot);
        upsertMeta(connection, SCHEMA_VERSION_KEY, String.valueOf(CURRENT_SCHEMA_VERSION));
        upsertMeta(connection, ITEM_FORMAT_KEY, itemSerializer.storageFormatId());
        repairData(connection);

        MigrationSnapshot importedSnapshot = SnapshotMigrationSupport.normalize(readSnapshot(connection, CURRENT_SCHEMA));
        String sourceDigest = SnapshotMigrationSupport.snapshotDigest(preparedLegacySnapshot);
        String importedDigest = SnapshotMigrationSupport.snapshotDigest(importedSnapshot);
        if (!sourceDigest.equals(importedDigest)) {
            throw new IllegalStateException("Legacy schema import verification failed. Backup: " + backupFile.getAbsolutePath()
                    + " Source digest: " + sourceDigest + " Imported digest: " + importedDigest);
        }

        dropLegacySchema(connection, sourceSchema);
        return new LegacyImportResult(
                "Imported legacy wardrobe data into RuinedWardrobe. Players: " + preparedLegacySnapshot.players().size()
                        + ", Sets: " + preparedLegacySnapshot.sets().size()
                        + ", Backup: " + backupFile.getAbsolutePath()
                        + ", Digest: " + importedDigest);
    }

    private MigrationSnapshot prepareLegacySnapshot(MigrationSnapshot snapshot) {
        MigrationSnapshot normalized = SnapshotMigrationSupport.normalize(snapshot);
        Map<String, MetaRow> metaRows = new LinkedHashMap<>();
        for (MetaRow row : normalized.metaRows()) {
            metaRows.put(row.key(), row);
        }
        metaRows.put(SCHEMA_VERSION_KEY, new MetaRow(SCHEMA_VERSION_KEY, String.valueOf(CURRENT_SCHEMA_VERSION)));
        metaRows.put(ITEM_FORMAT_KEY, new MetaRow(ITEM_FORMAT_KEY, itemSerializer.storageFormatId()));
        return new MigrationSnapshot(
                normalized.players(),
                normalized.sets(),
                new ArrayList<>(metaRows.values()));
    }

    private List<SchemaLayout> existingLegacySchemas(Connection connection) throws SQLException {
        List<SchemaLayout> existing = new ArrayList<>();
        for (SchemaLayout schema : LEGACY_SCHEMAS) {
            if (tableExists(connection, schema.playersTable())
                    || tableExists(connection, schema.setsTable())
                    || tableExists(connection, schema.metaTable())) {
                existing.add(schema);
            }
        }
        return existing;
    }

    private boolean currentSchemaHasData(Connection connection) throws SQLException {
        return rowCount(connection, PLAYERS_TABLE) > 0 || rowCount(connection, SETS_TABLE) > 0;
    }

    private long rowCount(Connection connection, String tableName) throws SQLException {
        if (!tableExists(connection, tableName)) {
            return 0L;
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        if (tableExists(metaData, connection.getCatalog(), tableName)) {
            return true;
        }
        return tableExists(metaData, connection.getCatalog(), tableName.toUpperCase(Locale.ROOT));
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        try (ResultSet rs = metaData.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private void dropLegacySchema(Connection connection, SchemaLayout schema) throws SQLException {
        execute(connection, "DROP TABLE IF EXISTS " + schema.metaTable());
        execute(connection, "DROP TABLE IF EXISTS " + schema.setsTable());
        execute(connection, "DROP TABLE IF EXISTS " + schema.playersTable());
    }

    private MigrationSnapshot readSnapshot(Connection connection, SchemaLayout schema) throws SQLException {
        List<PlayerRow> players = new ArrayList<>();
        List<SetRow> sets = new ArrayList<>();
        List<MetaRow> metaRows = new ArrayList<>();

        if (tableExists(connection, schema.playersTable())) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_uuid, bonus_slots, selected_slot, version, updated_at FROM " + schema.playersTable());
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    players.add(new PlayerRow(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getInt("bonus_slots"),
                            rs.getInt("selected_slot"),
                            rs.getLong("version"),
                            rs.getLong("updated_at")));
                }
            }
        }

        if (tableExists(connection, schema.setsTable())) {
            String query = """
                    SELECT player_uuid, slot_index, slot_name, favorite, %s, %s, %s, %s, created_at, updated_at, version
                    FROM %s
                    """.formatted(
                    schema.helmetColumn(),
                    schema.chestColumn(),
                    schema.legsColumn(),
                    schema.bootsColumn(),
                    schema.setsTable());
            try (PreparedStatement statement = connection.prepareStatement(query);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    sets.add(new SetRow(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getInt("slot_index"),
                            rs.getString("slot_name"),
                            rs.getInt("favorite") == 1,
                            rs.getString(schema.helmetColumn()),
                            rs.getString(schema.chestColumn()),
                            rs.getString(schema.legsColumn()),
                            rs.getString(schema.bootsColumn()),
                            rs.getLong("created_at"),
                            rs.getLong("updated_at"),
                            rs.getLong("version")));
                }
            }
        }

        if (tableExists(connection, schema.metaTable())) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT `key`, value FROM " + schema.metaTable());
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    metaRows.add(new MetaRow(rs.getString("key"), rs.getString("value")));
                }
            }
        }

        return new MigrationSnapshot(players, sets, metaRows);
    }

    private void insertSnapshot(Connection connection, MigrationSnapshot snapshot) throws SQLException {
        try (PreparedStatement players = connection.prepareStatement("""
                INSERT INTO rw_players (player_uuid, bonus_slots, selected_slot, version, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            for (PlayerRow row : snapshot.players()) {
                players.setString(1, row.playerId().toString());
                players.setInt(2, Math.max(0, row.bonusSlots()));
                players.setInt(3, row.selectedSlot() < 1 ? -1 : row.selectedSlot());
                players.setLong(4, Math.max(0L, row.version()));
                players.setLong(5, Math.max(0L, row.updatedAt()));
                players.addBatch();
            }
            players.executeBatch();
        }

        try (PreparedStatement sets = connection.prepareStatement("""
                INSERT INTO rw_sets (player_uuid, slot_index, slot_name, favorite, helmet_data, chest_data, legs_data, boots_data, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (SetRow row : snapshot.sets()) {
                sets.setString(1, row.playerId().toString());
                sets.setInt(2, row.slot());
                sets.setString(3, sanitizeSetName(row.slot(), row.name()));
                sets.setInt(4, row.favorite() ? 1 : 0);
                sets.setString(5, row.helmetData());
                sets.setString(6, row.chestData());
                sets.setString(7, row.legsData());
                sets.setString(8, row.bootsData());
                sets.setLong(9, Math.max(0L, row.createdAt()));
                sets.setLong(10, Math.max(Math.max(0L, row.createdAt()), Math.max(0L, row.updatedAt())));
                sets.setLong(11, Math.max(0L, row.version()));
                sets.addBatch();
            }
            sets.executeBatch();
        }

        try (PreparedStatement meta = connection.prepareStatement("INSERT INTO " + META_TABLE + " (`key`, value) VALUES (?, ?)")) {
            for (MetaRow row : snapshot.metaRows()) {
                meta.setString(1, row.key());
                meta.setString(2, row.value() == null ? "" : row.value());
                meta.addBatch();
            }
            meta.executeBatch();
        }
    }

    private void repairData(Connection connection) throws SQLException {
        execute(connection, "UPDATE " + PLAYERS_TABLE + " SET bonus_slots=0 WHERE bonus_slots < 0");
        execute(connection, "UPDATE " + PLAYERS_TABLE + " SET selected_slot=-1 WHERE selected_slot = 0");
        execute(connection, "UPDATE " + PLAYERS_TABLE + " SET version=0 WHERE version < 0");
        execute(connection, "UPDATE " + PLAYERS_TABLE + " SET updated_at=0 WHERE updated_at < 0");
        execute(connection, "DELETE FROM " + SETS_TABLE + " WHERE slot_index < 1");
        execute(connection, "DELETE FROM " + SETS_TABLE + " WHERE helmet_data IS NULL AND chest_data IS NULL AND legs_data IS NULL AND boots_data IS NULL");
        execute(connection, "UPDATE " + SETS_TABLE + " SET favorite=0 WHERE favorite NOT IN (0, 1)");
        execute(connection, "UPDATE " + SETS_TABLE + " SET version=0 WHERE version < 0");
        execute(connection, "UPDATE " + SETS_TABLE + " SET created_at=0 WHERE created_at < 0");
        execute(connection, "UPDATE " + SETS_TABLE + " SET updated_at=created_at WHERE updated_at < created_at");
        execute(connection, """
                UPDATE rw_players
                SET selected_slot=-1
                WHERE selected_slot > 0
                  AND NOT EXISTS (
                      SELECT 1
                      FROM rw_sets
                      WHERE rw_sets.player_uuid = rw_players.player_uuid
                        AND rw_sets.slot_index = rw_players.selected_slot
                  )
                """);
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private void clearTables(Connection connection) throws SQLException {
        execute(connection, "DELETE FROM " + META_TABLE);
        execute(connection, "DELETE FROM " + SETS_TABLE);
        execute(connection, "DELETE FROM " + PLAYERS_TABLE);
    }

    private void upsertMeta(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + META_TABLE + " SET value=? WHERE `key`=?")) {
            update.setString(1, value);
            update.setString(2, key);
            if (update.executeUpdate() > 0) {
                return;
            }
        }

        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + META_TABLE + " (`key`, value) VALUES (?, ?)")) {
            insert.setString(1, key);
            insert.setString(2, value);
            insert.executeUpdate();
        } catch (SQLException ex) {
            if (!isDuplicateKey(ex)) {
                throw ex;
            }
        }
    }

    private PlayerState readPlayerState(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT bonus_slots, selected_slot, version FROM " + PLAYERS_TABLE + " WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return new PlayerState(0, -1, 0L);
                }
                return new PlayerState(rs.getInt("bonus_slots"), rs.getInt("selected_slot"), rs.getLong("version"));
            }
        }
    }

    private Map<Integer, WardrobeSet> readSets(Connection connection, UUID playerId) throws SQLException {
        Map<Integer, WardrobeSet> sets = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT slot_index, slot_name, favorite, helmet_data, chest_data, legs_data, boots_data
                FROM rw_sets
                WHERE player_uuid=?
                """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot_index");
                    ItemStack helmet = readPiece(playerId, slot, "helmet", rs.getString("helmet_data"));
                    ItemStack chestplate = readPiece(playerId, slot, "chestplate", rs.getString("chest_data"));
                    ItemStack leggings = readPiece(playerId, slot, "leggings", rs.getString("legs_data"));
                    ItemStack boots = readPiece(playerId, slot, "boots", rs.getString("boots_data"));
                    WardrobeSet set = new WardrobeSet(
                            slot,
                            sanitizeSetName(slot, rs.getString("slot_name")),
                            rs.getInt("favorite") == 1,
                            helmet,
                            chestplate,
                            leggings,
                            boots);
                    if (set.hasAnyArmorPiece()) {
                        sets.put(slot, set);
                    }
                }
            }
        }
        return sets;
    }

    private ItemStack readPiece(UUID playerId, int slot, String piece, String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return itemSerializer.deserialize(payload);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to deserialize " + piece + " for " + playerId + " slot " + slot + ": " + ex.getMessage());
            auditLogger.error(
                    "ITEM_DESERIALIZE_ERROR",
                    playerId,
                    null,
                    ex,
                    Map.of("slot", slot, "piece", piece, "payloadPrefix", payload.substring(0, Math.min(payload.length(), 24))));
            return null;
        }
    }

    private void ensurePlayerRowExists(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO rw_players (player_uuid, bonus_slots, selected_slot, version, updated_at)
                VALUES (?, 0, -1, 0, ?)
                """)) {
            insert.setString(1, playerId.toString());
            insert.setLong(2, System.currentTimeMillis());
            insert.executeUpdate();
        } catch (SQLException ex) {
            if (!isDuplicateKey(ex)) {
                throw ex;
            }
        }
    }

    private void touchPlayerRow(Connection connection, UUID playerId) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + PLAYERS_TABLE + " SET updated_at=? WHERE player_uuid=?")) {
            update.setLong(1, now);
            update.setString(2, playerId.toString());
            if (update.executeUpdate() > 0) {
                return;
            }
        }

        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO rw_players (player_uuid, bonus_slots, selected_slot, version, updated_at)
                VALUES (?, 0, -1, 0, ?)
                """)) {
            insert.setString(1, playerId.toString());
            insert.setLong(2, now);
            insert.executeUpdate();
        } catch (SQLException ex) {
            if (!isDuplicateKey(ex)) {
                throw ex;
            }
        }
    }

    private boolean isDuplicateKey(SQLException ex) {
        String state = ex.getSQLState();
        int code = ex.getErrorCode();
        return "23000".equals(state) || code == 1062 || code == 1555 || code == 19;
    }

    private void bumpVersion(Connection connection, UUID playerId, long timestamp) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + PLAYERS_TABLE + " SET version=version+1, updated_at=? WHERE player_uuid=?")) {
            update.setLong(1, timestamp);
            update.setString(2, playerId.toString());
            update.executeUpdate();
        }
    }

    private void bindSet(PreparedStatement statement, WardrobeSet set, long now) throws SQLException {
        statement.setString(1, sanitizeSetName(set.slot(), set.name()));
        statement.setInt(2, set.favorite() ? 1 : 0);
        statement.setString(3, itemSerializer.serialize(set.helmet()));
        statement.setString(4, itemSerializer.serialize(set.chestplate()));
        statement.setString(5, itemSerializer.serialize(set.leggings()));
        statement.setString(6, itemSerializer.serialize(set.boots()));
        statement.setLong(7, now);
    }

    private String sanitizeSetName(int slot, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            return "Slot " + slot;
        }
        if (trimmed.length() > 48) {
            return trimmed.substring(0, 48);
        }
        return trimmed;
    }

    private <T> T withTransaction(Connection connection, SqlSupplier<T> supplier) throws Exception {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T value = supplier.get();
            connection.commit();
            return value;
        } catch (Exception ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private record PlayerState(int bonusSlots, int selectedSlot, long version) {
    }

    private record SchemaLayout(
            String playersTable,
            String setsTable,
            String metaTable,
            String helmetColumn,
            String chestColumn,
            String legsColumn,
            String bootsColumn
    ) {
    }

    private record LegacyImportResult(String message) {
        static LegacyImportResult none() {
            return new LegacyImportResult(null);
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws Exception;
    }
}
