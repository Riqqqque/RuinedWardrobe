package dev.rique.ruinedwardrobe.listener;

import dev.rique.ruinedwardrobe.core.WardrobeArmorBindingService;
import dev.rique.ruinedwardrobe.core.WardrobeArmorSyncService;
import dev.rique.ruinedwardrobe.core.WardrobeAuditLogger;
import dev.rique.ruinedwardrobe.lang.MessageService;
import dev.rique.ruinedwardrobe.scheduler.SchedulerAdapter;
import org.bukkit.GameMode;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WardrobeArmorProtectionListenerTest {

    @Test
    void detachedBoundInventoryItemIsUnboundInsteadOfCancelled() {
        Fixture fixture = new Fixture(GameMode.SURVIVAL);
        ItemStack boundItem = mock(ItemStack.class);
        ItemStack unboundItem = mock(ItemStack.class);
        InventoryClickEvent event = fixture.clickEvent(boundItem, InventoryType.SlotType.CONTAINER);

        when(fixture.bindingService.isBound(boundItem)).thenReturn(true);
        when(fixture.bindingService.isBoundTo(boundItem, fixture.playerId)).thenReturn(true);
        when(fixture.bindingService.getBoundSlot(boundItem)).thenReturn(3);
        when(fixture.bindingService.unbindCopy(boundItem)).thenReturn(unboundItem);

        fixture.listener.onClick(event);

        verify(event).setCurrentItem(unboundItem);
        verify(event, never()).setCancelled(true);
        verify(fixture.bindingService).setActiveSlot(fixture.playerId, 3);
        verify(fixture.armorSyncService).requestSync(fixture.player);
    }

    @Test
    void sameOwnerDuplicateIsRemovedInsteadOfConvertedToNormalArmor() {
        Fixture fixture = new Fixture(GameMode.SURVIVAL);
        ItemStack duplicate = mock(ItemStack.class);
        InventoryClickEvent event = fixture.clickEvent(duplicate, InventoryType.SlotType.CONTAINER);

        when(fixture.bindingService.isBound(duplicate)).thenReturn(true);
        when(fixture.bindingService.hasCanonicalArmorCopy(fixture.player, duplicate)).thenReturn(true);

        fixture.listener.onClick(event);

        verify(event).setCurrentItem(null);
        verify(event).setCancelled(true);
        verify(fixture.bindingService, never()).unbindCopy(duplicate);
        verify(fixture.armorSyncService, never()).requestSync(fixture.player);
        verify(fixture.player).updateInventory();
    }

    @Test
    void equippedWardrobeArmorStaysLockedInSurvival() {
        Fixture fixture = new Fixture(GameMode.SURVIVAL);
        ItemStack boundItem = mock(ItemStack.class);
        InventoryClickEvent event = fixture.clickEvent(boundItem, InventoryType.SlotType.ARMOR);

        when(fixture.bindingService.isBoundTo(boundItem, fixture.playerId)).thenReturn(true);

        fixture.listener.onClick(event);

        verify(event).setCancelled(true);
        verify(event, never()).setCurrentItem(any());
        verify(fixture.armorSyncService, never()).requestSync(fixture.player);
    }

    @Test
    void equippedArmorReportedThroughCraftingInventoryStaysLockedInSurvival() {
        Fixture fixture = new Fixture(GameMode.SURVIVAL);
        ItemStack boundItem = mock(ItemStack.class);
        InventoryClickEvent event = fixture.clickEvent(boundItem, InventoryType.SlotType.ARMOR, true);

        when(fixture.bindingService.isBoundTo(boundItem, fixture.playerId)).thenReturn(true);

        fixture.listener.onClick(event);

        verify(event).setCancelled(true);
        verify(event, never()).setCurrentItem(any());
        verify(fixture.armorSyncService, never()).requestSync(fixture.player);
    }

    @Test
    void equippedWardrobeArmorStaysLockedInCreative() {
        Fixture fixture = new Fixture(GameMode.CREATIVE);
        ItemStack boundItem = mock(ItemStack.class);
        InventoryClickEvent event = fixture.clickEvent(boundItem, InventoryType.SlotType.ARMOR);

        when(fixture.bindingService.isBound(boundItem)).thenReturn(true);

        fixture.listener.onClick(event);

        verify(event).setCancelled(true);
        verify(event, never()).setCurrentItem(any());
        verify(fixture.bindingService, never()).unbindCopy(any());
        verify(fixture.armorSyncService, never()).requestSync(fixture.player);
        verify(fixture.player).updateInventory();
    }

    @Test
    void creativeBoundCursorPacketIsCancelledWithoutUnbinding() {
        Fixture fixture = new Fixture(GameMode.CREATIVE);
        ItemStack boundItem = mock(ItemStack.class);
        InventoryCreativeEvent event = mock(InventoryCreativeEvent.class);

        when(event.getWhoClicked()).thenReturn(fixture.player);
        when(event.getCursor()).thenReturn(boundItem);
        when(event.getClick()).thenReturn(ClickType.CREATIVE);
        when(fixture.bindingService.isBound(boundItem)).thenReturn(true);

        fixture.listener.onCreative(event);

        verify(event).setCancelled(true);
        verify(event, never()).setCursor(any());
        verify(fixture.bindingService, never()).unbindCopy(any());
        verify(fixture.armorSyncService, never()).requestSync(fixture.player);
        verify(fixture.player).updateInventory();
    }

    @Test
    void creativeBoundDropIsCancelledWithoutCreatingMovableCopy() {
        Fixture fixture = new Fixture(GameMode.CREATIVE);
        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
        Item droppedEntity = mock(Item.class);
        ItemStack boundItem = mock(ItemStack.class);

        when(event.getPlayer()).thenReturn(fixture.player);
        when(event.getItemDrop()).thenReturn(droppedEntity);
        when(droppedEntity.getItemStack()).thenReturn(boundItem);
        when(fixture.bindingService.isBound(boundItem)).thenReturn(true);

        fixture.listener.onDrop(event);

        verify(event).setCancelled(true);
        verify(droppedEntity, never()).setItemStack(any());
        verify(fixture.bindingService, never()).unbindCopy(any());
        verify(fixture.armorSyncService, never()).requestSync(fixture.player);
        verify(fixture.player).updateInventory();
    }

    @Test
    void creativeBoundOffhandSwapIsCancelled() {
        Fixture fixture = new Fixture(GameMode.CREATIVE);
        PlayerSwapHandItemsEvent event = mock(PlayerSwapHandItemsEvent.class);
        ItemStack boundItem = mock(ItemStack.class);

        when(event.getPlayer()).thenReturn(fixture.player);
        when(event.getOffHandItem()).thenReturn(boundItem);
        when(fixture.bindingService.isBound(boundItem)).thenReturn(true);

        fixture.listener.onSwap(event);

        verify(event).setCancelled(true);
        verify(event, never()).setOffHandItem(any());
        verify(fixture.bindingService, never()).unbindCopy(any());
        verify(fixture.player).updateInventory();
    }

    @Test
    void droppedBoundItemIsUnboundInsteadOfCancelled() {
        Fixture fixture = new Fixture(GameMode.SURVIVAL);
        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
        Item droppedEntity = mock(Item.class);
        ItemStack boundItem = mock(ItemStack.class);
        ItemStack unboundItem = mock(ItemStack.class);

        when(event.getPlayer()).thenReturn(fixture.player);
        when(event.getItemDrop()).thenReturn(droppedEntity);
        when(droppedEntity.getItemStack()).thenReturn(boundItem);
        when(fixture.bindingService.isBound(boundItem)).thenReturn(true);
        when(fixture.bindingService.unbindCopy(boundItem)).thenReturn(unboundItem);

        fixture.listener.onDrop(event);

        verify(droppedEntity).setItemStack(unboundItem);
        verify(event, never()).setCancelled(true);
        verify(fixture.armorSyncService).requestSync(fixture.player);
    }

    @Test
    void droppedDuplicateIsCancelledAndReconciled() {
        Fixture fixture = new Fixture(GameMode.SURVIVAL);
        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
        Item droppedEntity = mock(Item.class);
        ItemStack duplicate = mock(ItemStack.class);

        when(event.getPlayer()).thenReturn(fixture.player);
        when(event.getItemDrop()).thenReturn(droppedEntity);
        when(droppedEntity.getItemStack()).thenReturn(duplicate);
        when(fixture.bindingService.isBound(duplicate)).thenReturn(true);
        when(fixture.bindingService.hasCanonicalArmorCopy(fixture.player, duplicate)).thenReturn(true);

        fixture.listener.onDrop(event);

        verify(event).setCancelled(true);
        verify(droppedEntity, never()).setItemStack(any());
        verify(fixture.bindingService).sanitizeBoundItems(fixture.player);
        verify(fixture.bindingService, never()).unbindCopy(duplicate);
    }

    private static final class Fixture {
        private final UUID playerId = UUID.randomUUID();
        private final Player player = mock(Player.class);
        private final PlayerInventory playerInventory = mock(PlayerInventory.class);
        private final WardrobeArmorBindingService bindingService = mock(WardrobeArmorBindingService.class);
        private final WardrobeArmorSyncService armorSyncService = mock(WardrobeArmorSyncService.class);
        private final SchedulerAdapter schedulerAdapter = mock(SchedulerAdapter.class);
        private final WardrobeArmorProtectionListener listener;

        private Fixture(GameMode gameMode) {
            when(player.getUniqueId()).thenReturn(playerId);
            when(player.getName()).thenReturn("Rique");
            when(player.isOnline()).thenReturn(true);
            when(player.getGameMode()).thenReturn(gameMode);
            when(player.getInventory()).thenReturn(playerInventory);
            when(bindingService.sanitizeBoundItems(player)).thenReturn(
                    new WardrobeArmorBindingService.SanitizationResult(0, 0, 0));
            when(armorSyncService.requestSync(player)).thenReturn(CompletableFuture.completedFuture(null));
            when(schedulerAdapter.runPlayer(eq(player), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.<Runnable>getArgument(1).run();
                return mock(SchedulerAdapter.TaskHandle.class);
            });
            listener = new WardrobeArmorProtectionListener(
                    bindingService,
                    armorSyncService,
                    WardrobeAuditLogger.disabled(),
                    mock(MessageService.class),
                    schedulerAdapter,
                    false);
        }

        private InventoryClickEvent clickEvent(ItemStack currentItem, InventoryType.SlotType slotType) {
            return clickEvent(currentItem, slotType, false);
        }

        private InventoryClickEvent clickEvent(
                ItemStack currentItem,
                InventoryType.SlotType slotType,
                boolean clickedInTopInventory) {
            InventoryClickEvent event = mock(InventoryClickEvent.class);
            InventoryView view = mock(InventoryView.class);
            Inventory topInventory = mock(Inventory.class);
            when(event.getWhoClicked()).thenReturn(player);
            when(event.getClickedInventory()).thenReturn(clickedInTopInventory ? topInventory : playerInventory);
            when(event.getView()).thenReturn(view);
            when(view.getTopInventory()).thenReturn(topInventory);
            when(topInventory.getHolder()).thenReturn(null);
            when(event.getCurrentItem()).thenReturn(currentItem);
            when(event.getCursor()).thenReturn(null);
            when(event.getClick()).thenReturn(ClickType.LEFT);
            when(event.getHotbarButton()).thenReturn(-1);
            when(event.getSlotType()).thenReturn(slotType);
            return event;
        }
    }
}
