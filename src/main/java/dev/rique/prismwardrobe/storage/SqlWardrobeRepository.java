package dev.rique.prismwardrobe.storage;

import dev.rique.prismwardrobe.api.model.WardrobeProfile;
import dev.rique.prismwardrobe.api.model.WardrobeSet;
import dev.rique.prismwardrobe.util.ItemStackDataSerializer;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SqlWardrobeRepository implements WardrobeRepository {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final ItemStackDataSerializer itemSerializer;

    public SqlWardrobeRepository(JavaPlugin plugin, DatabaseManager databaseManager, ItemStackDataSerializer itemSerializer) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.itemSerializer = itemSerializer;
    }

    @Override
    public CompletableFuture<Void> initializeSchema() {
        return databaseManager.runWrite(connection -> {
            try {
                // Execute DDL sequentially so dependent objects (index -> table) are created in order.
                try (PreparedStatement players = connection.prepareStatement("""
                        CREATE TABLE IF NOT EXISTS pw_players (
                            player_uuid VARCHAR(36) PRIMARY KEY,
                            bonus_slots INTEGER NOT NULL DEFAULT 0,
                            selected_slot INTEGER NOT NULL DEFAULT -1,
                            version BIGINT NOT NULL DEFAULT 0,
                            updated_at BIGINT NOT NULL
                        )
                        """)) {
                    players.execute();
                }
                try (PreparedStatement sets = connection.prepareStatement("""
                        CREATE TABLE IF NOT EXISTS pw_sets (
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
                        """)) {
                    sets.execute();
                }
                try (PreparedStatement meta = connection.prepareStatement("""
                        CREATE TABLE IF NOT EXISTS pw_meta (
                            `key` VARCHAR(64) PRIMARY KEY,
                            value TEXT NOT NULL
                        )
                        """)) {
                    meta.execute();
                }
                try (PreparedStatement index = connection.prepareStatement("""
                        CREATE INDEX IF NOT EXISTS idx_pw_players_version
                        ON pw_players(version)
                        """)) {
                    index.execute();
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<WardrobeProfile> loadProfile(UUID playerId) {
        return databaseManager.runQuery(connection -> {
            try {
                ensurePlayerRowExists(connection, playerId);
                PlayerState playerState = readPlayerState(connection, playerId);
                Map<Integer, WardrobeSet> sets = readSets(connection, playerId);
                int selectedSlot = playerState.selectedSlot;
                if (selectedSlot > 0) {
                    WardrobeSet selected = sets.get(selectedSlot);
                    if (selected == null || !selected.hasAnyArmorPiece()) {
                        selectedSlot = -1;
                    }
                }
                return new WardrobeProfile(playerId, playerState.bonusSlots, selectedSlot, playerState.version, sets);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveSet(UUID playerId, WardrobeSet wardrobeSet) {
        return databaseManager.runWrite(connection -> {
            try {
                touchPlayerRow(connection, playerId);
                long now = System.currentTimeMillis();
                String updateSql = """
                        UPDATE pw_sets
                        SET slot_name=?, favorite=?, helmet_json=?, chest_json=?, legs_json=?, boots_json=?, updated_at=?, version=version+1
                        WHERE player_uuid=? AND slot_index=?
                        """;
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                    bindSet(statement, wardrobeSet, now);
                    statement.setString(8, playerId.toString());
                    statement.setInt(9, wardrobeSet.slot());
                    changed = statement.executeUpdate();
                }

                if (changed == 0) {
                    String insertSql = """
                            INSERT INTO pw_sets (player_uuid, slot_index, slot_name, favorite, helmet_json, chest_json, legs_json, boots_json, created_at, updated_at, version)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                        statement.setString(1, playerId.toString());
                        statement.setInt(2, wardrobeSet.slot());
                        statement.setString(3, wardrobeSet.name());
                        statement.setInt(4, wardrobeSet.favorite() ? 1 : 0);
                        statement.setString(5, itemSerializer.toJson(wardrobeSet.helmet()));
                        statement.setString(6, itemSerializer.toJson(wardrobeSet.chestplate()));
                        statement.setString(7, itemSerializer.toJson(wardrobeSet.leggings()));
                        statement.setString(8, itemSerializer.toJson(wardrobeSet.boots()));
                        statement.setLong(9, now);
                        statement.setLong(10, now);
                        statement.setLong(11, 1L);
                        statement.executeUpdate();
                    }
                }

                bumpVersion(connection, playerId, now);
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
                touchPlayerRow(connection, playerId);
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM pw_sets WHERE player_uuid=? AND slot_index=?")) {
                    statement.setString(1, playerId.toString());
                    statement.setInt(2, slot);
                    statement.executeUpdate();
                }
                bumpVersion(connection, playerId, System.currentTimeMillis());
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> renameSet(UUID playerId, int slot, String name) {
        return databaseManager.runWrite(connection -> {
            try {
                touchPlayerRow(connection, playerId);
                try (PreparedStatement statement = connection.prepareStatement("UPDATE pw_sets SET slot_name=?, updated_at=?, version=version+1 WHERE player_uuid=? AND slot_index=?")) {
                    statement.setString(1, name);
                    statement.setLong(2, System.currentTimeMillis());
                    statement.setString(3, playerId.toString());
                    statement.setInt(4, slot);
                    statement.executeUpdate();
                }
                bumpVersion(connection, playerId, System.currentTimeMillis());
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Integer> getBonusSlots(UUID playerId) {
        return databaseManager.runQuery(connection -> {
            try {
                ensurePlayerRowExists(connection, playerId);
                try (PreparedStatement statement = connection.prepareStatement("SELECT bonus_slots FROM pw_players WHERE player_uuid=?")) {
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
                touchPlayerRow(connection, playerId);
                int oldValue = 0;
                try (PreparedStatement select = connection.prepareStatement("SELECT bonus_slots FROM pw_players WHERE player_uuid=?")) {
                    select.setString(1, playerId.toString());
                    try (ResultSet rs = select.executeQuery()) {
                        if (rs.next()) {
                            oldValue = rs.getInt("bonus_slots");
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE pw_players SET bonus_slots=?, version=version+1, updated_at=? WHERE player_uuid=?")) {
                    update.setInt(1, Math.max(0, bonusSlots));
                    update.setLong(2, System.currentTimeMillis());
                    update.setString(3, playerId.toString());
                    update.executeUpdate();
                }
                return oldValue;
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setSelectedSlot(UUID playerId, int selectedSlot) {
        return databaseManager.runWrite(connection -> {
            try {
                touchPlayerRow(connection, playerId);
                try (PreparedStatement statement = connection.prepareStatement("UPDATE pw_players SET selected_slot=?, version=version+1, updated_at=? WHERE player_uuid=?")) {
                    statement.setInt(1, selectedSlot);
                    statement.setLong(2, System.currentTimeMillis());
                    statement.setString(3, playerId.toString());
                    statement.executeUpdate();
                }
            } catch (SQLException ex) {
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
                int chunkSize = 100;
                for (int i = 0; i < playerIds.size(); i += chunkSize) {
                    int end = Math.min(playerIds.size(), i + chunkSize);
                    List<UUID> chunk = playerIds.subList(i, end);
                    StringBuilder query = new StringBuilder("SELECT player_uuid, version FROM pw_players WHERE player_uuid IN (");
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
                List<PlayerRow> players = new ArrayList<>();
                List<SetRow> sets = new ArrayList<>();
                List<MetaRow> meta = new ArrayList<>();

                try (PreparedStatement statement = connection.prepareStatement("SELECT player_uuid, bonus_slots, selected_slot, version, updated_at FROM pw_players");
                     ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        players.add(new PlayerRow(
                                UUID.fromString(rs.getString("player_uuid")),
                                rs.getInt("bonus_slots"),
                                rs.getInt("selected_slot"),
                                rs.getLong("version"),
                                rs.getLong("updated_at")
                        ));
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT player_uuid, slot_index, slot_name, favorite, helmet_json, chest_json, legs_json, boots_json, created_at, updated_at, version
                        FROM pw_sets
                        """);
                     ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        sets.add(new SetRow(
                                UUID.fromString(rs.getString("player_uuid")),
                                rs.getInt("slot_index"),
                                rs.getString("slot_name"),
                                rs.getInt("favorite") == 1,
                                rs.getString("helmet_json"),
                                rs.getString("chest_json"),
                                rs.getString("legs_json"),
                                rs.getString("boots_json"),
                                rs.getLong("created_at"),
                                rs.getLong("updated_at"),
                                rs.getLong("version")
                        ));
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("SELECT `key`, value FROM pw_meta");
                     ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        meta.add(new MetaRow(rs.getString("key"), rs.getString("value")));
                    }
                }
                return new MigrationSnapshot(players, sets, meta);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Override
    public CompletableFuture<Void> writeSnapshot(MigrationSnapshot snapshot) {
        return databaseManager.runWrite(connection -> {
            try {
                connection.setAutoCommit(false);
                try {
                    clearTables(connection);
                    for (PlayerRow row : snapshot.players()) {
                        try (PreparedStatement statement = connection.prepareStatement("""
                                INSERT INTO pw_players (player_uuid, bonus_slots, selected_slot, version, updated_at)
                                VALUES (?, ?, ?, ?, ?)
                                """)) {
                            statement.setString(1, row.playerId().toString());
                            statement.setInt(2, row.bonusSlots());
                            statement.setInt(3, row.selectedSlot());
                            statement.setLong(4, row.version());
                            statement.setLong(5, row.updatedAt());
                            statement.executeUpdate();
                        }
                    }
                    for (SetRow row : snapshot.sets()) {
                        try (PreparedStatement statement = connection.prepareStatement("""
                                INSERT INTO pw_sets (player_uuid, slot_index, slot_name, favorite, helmet_json, chest_json, legs_json, boots_json, created_at, updated_at, version)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """)) {
                            statement.setString(1, row.playerId().toString());
                            statement.setInt(2, row.slot());
                            statement.setString(3, row.name());
                            statement.setInt(4, row.favorite() ? 1 : 0);
                            statement.setString(5, row.helmetJson());
                            statement.setString(6, row.chestJson());
                            statement.setString(7, row.legsJson());
                            statement.setString(8, row.bootsJson());
                            statement.setLong(9, row.createdAt());
                            statement.setLong(10, row.updatedAt());
                            statement.setLong(11, row.version());
                            statement.executeUpdate();
                        }
                    }
                    for (MetaRow row : snapshot.metaRows()) {
                        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO pw_meta (`key`, value) VALUES (?, ?)")) {
                            statement.setString(1, row.key());
                            statement.setString(2, row.value());
                            statement.executeUpdate();
                        }
                    }
                    connection.commit();
                } catch (Exception ex) {
                    connection.rollback();
                    throw ex;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return null;
        });
    }

    private void clearTables(Connection connection) throws SQLException {
        try (PreparedStatement meta = connection.prepareStatement("DELETE FROM pw_meta");
             PreparedStatement sets = connection.prepareStatement("DELETE FROM pw_sets");
             PreparedStatement players = connection.prepareStatement("DELETE FROM pw_players")) {
            meta.executeUpdate();
            sets.executeUpdate();
            players.executeUpdate();
        }
    }

    private PlayerState readPlayerState(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT bonus_slots, selected_slot, version FROM pw_players WHERE player_uuid=?")) {
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
                SELECT slot_index, slot_name, favorite, helmet_json, chest_json, legs_json, boots_json
                FROM pw_sets
                WHERE player_uuid=?
                """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot_index");
                    try {
                        WardrobeSet set = new WardrobeSet(
                                slot,
                                rs.getString("slot_name"),
                                rs.getInt("favorite") == 1,
                                itemSerializer.fromJson(rs.getString("helmet_json")),
                                itemSerializer.fromJson(rs.getString("chest_json")),
                                itemSerializer.fromJson(rs.getString("legs_json")),
                                itemSerializer.fromJson(rs.getString("boots_json"))
                        );
                        if (!set.hasAnyArmorPiece()) {
                            continue;
                        }
                        sets.put(slot, set);
                    } catch (Exception parseError) {
                        plugin.getLogger().warning("Failed to deserialize wardrobe set for " + playerId + " slot " + slot + ": " + parseError.getMessage());
                    }
                }
            }
        }
        return sets;
    }

    private void ensurePlayerRowExists(Connection connection, UUID playerId) throws SQLException {
        String insertSql = """
                INSERT INTO pw_players (player_uuid, bonus_slots, selected_slot, version, updated_at)
                VALUES (?, 0, -1, 0, ?)
                """;
        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
            insert.setString(1, playerId.toString());
            insert.setLong(2, System.currentTimeMillis());
            insert.executeUpdate();
        } catch (SQLException ex) {
            // Another thread may have inserted already.
            if (!isDuplicateKey(ex)) {
                throw ex;
            }
        }
    }

    private void touchPlayerRow(Connection connection, UUID playerId) throws SQLException {
        long now = System.currentTimeMillis();
        String updateSql = "UPDATE pw_players SET updated_at=? WHERE player_uuid=?";
        try (PreparedStatement update = connection.prepareStatement(updateSql)) {
            update.setLong(1, now);
            update.setString(2, playerId.toString());
            int changed = update.executeUpdate();
            if (changed > 0) {
                return;
            }
        }

        String insertSql = """
                INSERT INTO pw_players (player_uuid, bonus_slots, selected_slot, version, updated_at)
                VALUES (?, 0, -1, 0, ?)
                """;
        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
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
        try (PreparedStatement update = connection.prepareStatement("UPDATE pw_players SET version=version+1, updated_at=? WHERE player_uuid=?")) {
            update.setLong(1, timestamp);
            update.setString(2, playerId.toString());
            update.executeUpdate();
        }
    }

    private void bindSet(PreparedStatement statement, WardrobeSet set, long now) throws Exception {
        statement.setString(1, set.name());
        statement.setInt(2, set.favorite() ? 1 : 0);
        statement.setString(3, itemSerializer.toJson(set.helmet()));
        statement.setString(4, itemSerializer.toJson(set.chestplate()));
        statement.setString(5, itemSerializer.toJson(set.leggings()));
        statement.setString(6, itemSerializer.toJson(set.boots()));
        statement.setLong(7, now);
    }

    private record PlayerState(int bonusSlots, int selectedSlot, long version) {
    }
}
