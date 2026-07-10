package dev.rique.ruinedwardrobe.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WardrobeGuiListenerTest {

    @Test
    void outsideClickInWardrobeViewIsCancelled() {
        WardrobeGuiController controller = mock(WardrobeGuiController.class);
        WardrobeGuiListener listener = new WardrobeGuiListener(controller);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        WardrobeMenuHolder holder = new WardrobeMenuHolder(new GuiSession(UUID.randomUUID(), UUID.randomUUID(), 0));

        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(holder);
        when(event.getClickedInventory()).thenReturn(null);

        listener.onClick(event);

        verify(event).setCancelled(true);
        verifyNoInteractions(controller);
    }
}
