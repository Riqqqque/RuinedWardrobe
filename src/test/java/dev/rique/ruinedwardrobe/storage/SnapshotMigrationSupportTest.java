package dev.rique.ruinedwardrobe.storage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotMigrationSupportTest {

    @Test
    void normalizeDropsEmptySetRowsAndClearsSelectedSlot() {
        UUID playerId = UUID.randomUUID();
        WardrobeRepository.MigrationSnapshot snapshot = new WardrobeRepository.MigrationSnapshot(
                List.of(new WardrobeRepository.PlayerRow(playerId, 0, 2, 5L, 100L)),
                List.of(new WardrobeRepository.SetRow(
                        playerId,
                        2,
                        "Empty",
                        false,
                        " ",
                        null,
                        "",
                        null,
                        100L,
                        100L,
                        1L)),
                List.of());

        WardrobeRepository.MigrationSnapshot normalized = SnapshotMigrationSupport.normalize(snapshot);

        assertTrue(normalized.sets().isEmpty());
        assertEquals(-1, normalized.players().getFirst().selectedSlot());
    }
}
