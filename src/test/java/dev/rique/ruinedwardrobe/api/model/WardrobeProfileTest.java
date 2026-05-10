package dev.rique.ruinedwardrobe.api.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class WardrobeProfileTest {

    @Test
    void constructorCopiesSetMap() {
        WardrobeSet set = mock(WardrobeSet.class);
        Map<Integer, WardrobeSet> sets = new HashMap<>();
        sets.put(1, set);

        WardrobeProfile profile = new WardrobeProfile(UUID.randomUUID(), 0, -1, 1L, sets);
        sets.clear();

        assertSame(set, profile.getSet(1));
    }
}
