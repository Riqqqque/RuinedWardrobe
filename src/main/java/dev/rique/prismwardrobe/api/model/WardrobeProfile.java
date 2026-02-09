package dev.rique.prismwardrobe.api.model;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public record WardrobeProfile(
        UUID playerId,
        int bonusSlots,
        int selectedSlot,
        long version,
        Map<Integer, WardrobeSet> sets
) {
    public WardrobeProfile {
        sets = sets == null ? Collections.emptyMap() : Collections.unmodifiableMap(sets);
    }

    public WardrobeSet getSet(int slot) {
        return sets.get(slot);
    }
}

