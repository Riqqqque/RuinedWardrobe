package dev.rique.ruinedwardrobe.gui;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

final class WardrobeClickPolicy {

    private WardrobeClickPolicy() {
    }

    static boolean isAllowedDirectAction(InventoryClickEvent event) {
        if (event == null || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) {
            return false;
        }
        return switch (event.getAction()) {
            case NOTHING,
                    PICKUP_ALL,
                    PICKUP_SOME,
                    PICKUP_HALF,
                    PICKUP_ONE,
                    PLACE_ALL,
                    PLACE_SOME,
                    PLACE_ONE,
                    SWAP_WITH_CURSOR -> true;
            default -> false;
        };
    }
}
