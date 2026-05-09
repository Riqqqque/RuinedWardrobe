package dev.rique.ruinedwardrobe.storage;

import dev.rique.ruinedwardrobe.api.model.WardrobeProfile;
import dev.rique.ruinedwardrobe.api.model.WardrobeSet;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface WardrobeRepository {

    CompletableFuture<Void> initializeSchema();

    CompletableFuture<WardrobeProfile> loadProfile(UUID playerId);

    CompletableFuture<Void> saveSet(UUID playerId, WardrobeSet wardrobeSet);

    CompletableFuture<Void> deleteSet(UUID playerId, int slot);

    CompletableFuture<Void> renameSet(UUID playerId, int slot, String name);

    CompletableFuture<Integer> getBonusSlots(UUID playerId);

    CompletableFuture<Integer> setBonusSlots(UUID playerId, int bonusSlots);

    CompletableFuture<Void> setSelectedSlot(UUID playerId, int selectedSlot);

    CompletableFuture<Map<UUID, Long>> fetchVersions(List<UUID> playerIds);

    CompletableFuture<Void> touchPlayer(UUID playerId);

    CompletableFuture<MigrationSnapshot> readSnapshot();

    CompletableFuture<Void> writeSnapshot(MigrationSnapshot snapshot);

    record MigrationSnapshot(List<PlayerRow> players, List<SetRow> sets, List<MetaRow> metaRows) {
    }

    record PlayerRow(UUID playerId, int bonusSlots, int selectedSlot, long version, long updatedAt) {
    }

    record SetRow(UUID playerId, int slot, String name, boolean favorite, String helmetData, String chestData, String legsData, String bootsData, long createdAt, long updatedAt, long version) {
    }

    record MetaRow(String key, String value) {
    }
}
