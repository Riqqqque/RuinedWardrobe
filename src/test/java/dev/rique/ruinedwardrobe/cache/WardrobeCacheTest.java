package dev.rique.ruinedwardrobe.cache;

import dev.rique.ruinedwardrobe.api.model.WardrobeProfile;
import dev.rique.ruinedwardrobe.config.PluginConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardrobeCacheTest {

    @Test
    void olderProfilesDoNotReplaceNewerCachedState() {
        WardrobeCache cache = new WardrobeCache(new PluginConfig.CacheSettings(100L, 60L));
        UUID playerId = UUID.randomUUID();
        WardrobeProfile newer = new WardrobeProfile(playerId, 4, 2, 5L, Map.of());
        WardrobeProfile older = new WardrobeProfile(playerId, 1, -1, 3L, Map.of());

        assertTrue(cache.putIfCurrent(newer));
        assertFalse(cache.putIfCurrent(older));

        WardrobeProfile cached = cache.get(playerId).orElseThrow();
        assertEquals(5L, cached.version());
        assertEquals(4, cached.bonusSlots());
        assertEquals(2, cached.selectedSlot());
    }

    @Test
    void equalVersionProfilesCanRefreshCachedState() {
        WardrobeCache cache = new WardrobeCache(new PluginConfig.CacheSettings(100L, 60L));
        UUID playerId = UUID.randomUUID();
        WardrobeProfile first = new WardrobeProfile(playerId, 1, -1, 3L, Map.of());
        WardrobeProfile second = new WardrobeProfile(playerId, 2, 1, 3L, Map.of());

        assertTrue(cache.putIfCurrent(first));
        assertTrue(cache.putIfCurrent(second));

        WardrobeProfile cached = cache.get(playerId).orElseThrow();
        assertEquals(3L, cached.version());
        assertEquals(2, cached.bonusSlots());
        assertEquals(1, cached.selectedSlot());
    }
}
