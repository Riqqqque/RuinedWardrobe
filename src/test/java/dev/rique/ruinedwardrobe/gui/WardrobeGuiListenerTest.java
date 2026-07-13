package dev.rique.ruinedwardrobe.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        UUID playerId = UUID.randomUUID();
        WardrobeMenuHolder holder = new WardrobeMenuHolder(new GuiSession(playerId, UUID.randomUUID(), 0));

        when(player.getUniqueId()).thenReturn(playerId);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(holder);
        when(event.getClickedInventory()).thenReturn(null);

        listener.onClick(event);

        verify(event).setCancelled(true);
        verify(controller).resyncInventory(player);
        verify(controller, never()).handleMainClick(player, holder, event);
    }

    @Test
    void foreignViewerIsClosedBeforeMenuAction() {
        WardrobeGuiController controller = mock(WardrobeGuiController.class);
        WardrobeGuiListener listener = new WardrobeGuiListener(controller);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        WardrobeMenuHolder holder = new WardrobeMenuHolder(
                new GuiSession(UUID.randomUUID(), UUID.randomUUID(), 0));

        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(holder);

        listener.onClick(event);

        verify(event).setCancelled(true);
        verify(controller).closeInvalidSession(player);
        verify(controller, never()).handleMainClick(player, holder, event);
    }

    @Test
    void numberKeyPacketIsRejectedAndResynced() {
        WardrobeGuiController controller = mock(WardrobeGuiController.class);
        WardrobeGuiListener listener = new WardrobeGuiListener(controller);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        UUID playerId = UUID.randomUUID();
        WardrobeMenuHolder holder = new WardrobeMenuHolder(new GuiSession(playerId, playerId, 0));

        when(player.getUniqueId()).thenReturn(playerId);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(holder);
        when(event.getClickedInventory()).thenReturn(inventory);
        when(event.getClick()).thenReturn(ClickType.NUMBER_KEY);
        when(event.getAction()).thenReturn(InventoryAction.HOTBAR_SWAP);

        listener.onClick(event);

        verify(event).setCancelled(true);
        verify(controller).resyncInventory(player);
        verify(controller, never()).handleMainClick(player, holder, event);
    }

    @Test
    void dropPacketFromBottomInventoryIsRejected() {
        WardrobeGuiController controller = mock(WardrobeGuiController.class);
        WardrobeGuiListener listener = new WardrobeGuiListener(controller);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        Player player = mock(Player.class);
        PlayerInventory playerInventory = mock(PlayerInventory.class);
        Inventory inventory = mock(Inventory.class);
        UUID playerId = UUID.randomUUID();
        WardrobeMenuHolder holder = new WardrobeMenuHolder(new GuiSession(playerId, playerId, 0));

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getInventory()).thenReturn(playerInventory);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(holder);
        when(event.getClickedInventory()).thenReturn(playerInventory);
        when(event.getClick()).thenReturn(ClickType.DROP);
        when(event.getAction()).thenReturn(InventoryAction.DROP_ONE_SLOT);

        listener.onClick(event);

        verify(event).setCancelled(true);
        verify(controller).resyncInventory(player);
        verifyNoInteractions(playerInventory);
    }

    @Test
    void normalTopInventoryClickReachesController() {
        WardrobeGuiController controller = mock(WardrobeGuiController.class);
        WardrobeGuiListener listener = new WardrobeGuiListener(controller);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        UUID playerId = UUID.randomUUID();
        WardrobeMenuHolder holder = new WardrobeMenuHolder(new GuiSession(playerId, playerId, 0));

        when(player.getUniqueId()).thenReturn(playerId);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(holder);
        when(event.getClickedInventory()).thenReturn(inventory);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);

        listener.onClick(event);

        verify(event).setCancelled(true);
        verify(controller).handleMainClick(player, holder, event);
    }
}
