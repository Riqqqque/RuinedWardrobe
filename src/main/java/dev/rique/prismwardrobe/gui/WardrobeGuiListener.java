package dev.rique.prismwardrobe.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.List;

public final class WardrobeGuiListener implements Listener {

    private final WardrobeGuiController guiController;

    public WardrobeGuiListener(WardrobeGuiController guiController) {
        this.guiController = guiController;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }

        if (event.getInventory().getHolder() instanceof WardrobeMenuHolder holder) {
            // Bottom inventory remains player-controlled, except quick-move which can bypass GUI rules.
            if (event.getClickedInventory() == player.getInventory()) {
                if (event.getClick().isShiftClick()) {
                    event.setCancelled(true);
                }
                return;
            }

            // Clicking in the wardrobe GUI (top inventory)
            event.setCancelled(true);
            guiController.handleMainClick(player, holder, event);
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof WardrobeMenuHolder holder) {
            int topSize = event.getInventory().getSize();
            List<Integer> topSlots = event.getRawSlots().stream()
                    .filter(slot -> slot < topSize)
                    .toList();
            if (topSlots.isEmpty()) {
                return;
            }
            event.setCancelled(true);
            if (topSlots.size() == 1) {
                guiController.handleMainDrag(player, holder, topSlots.get(0), event.getOldCursor());
            }
            return;
        }
    }
}
