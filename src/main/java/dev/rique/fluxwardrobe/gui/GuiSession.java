package dev.rique.fluxwardrobe.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GuiSession {

    private final UUID viewerId;
    private final UUID targetId;
    private final int page;
    private final Map<Integer, Integer> guiToWardrobeSlot = new HashMap<>();

    public GuiSession(UUID viewerId, UUID targetId, int page) {
        this.viewerId = viewerId;
        this.targetId = targetId;
        this.page = page;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public UUID targetId() {
        return targetId;
    }

    public int page() {
        return page;
    }

    public void bindSlot(int guiSlot, int wardrobeSlot) {
        guiToWardrobeSlot.put(guiSlot, wardrobeSlot);
    }

    public int resolveWardrobeSlot(int guiSlot) {
        return guiToWardrobeSlot.getOrDefault(guiSlot, -1);
    }
}
