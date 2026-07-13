package dev.rique.ruinedwardrobe.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.List;

public final class WardrobeGuiListener implements Listener {

    private final WardrobeGuiController guiController;

    public WardrobeGuiListener(WardrobeGuiController guiController) {
        this.guiController = guiController;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof WardrobeMenuHolder holder) {
            if (!holder.session().viewerId().equals(player.getUniqueId())) {
                event.setCancelled(true);
                guiController.closeInvalidSession(player);
                return;
            }
            if (event.getClickedInventory() == null) {
                event.setCancelled(true);
                guiController.resyncInventory(player);
                return;
            }

            if (event.getClickedInventory() == player.getInventory()) {
                if (!WardrobeClickPolicy.isAllowedDirectAction(event)) {
                    event.setCancelled(true);
                    guiController.resyncInventory(player);
                }
                return;
            }

            event.setCancelled(true);
            if (!WardrobeClickPolicy.isAllowedDirectAction(event)) {
                guiController.resyncInventory(player);
                return;
            }
            guiController.handleMainClick(player, holder, event);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof WardrobeMenuHolder holder) {
            if (!holder.session().viewerId().equals(player.getUniqueId())) {
                event.setCancelled(true);
                guiController.closeInvalidSession(player);
                return;
            }
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
            } else {
                guiController.resyncInventory(player);
            }
            return;
        }
    }
}
