package dev.rique.fluxwardrobe.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class WardrobeMenuHolder implements InventoryHolder {

    private final GuiSession session;

    public WardrobeMenuHolder(GuiSession session) {
        this.session = session;
    }

    public GuiSession session() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

