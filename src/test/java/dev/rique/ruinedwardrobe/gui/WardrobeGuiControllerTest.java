package dev.rique.ruinedwardrobe.gui;

import dev.rique.ruinedwardrobe.api.model.ResultCode;
import dev.rique.ruinedwardrobe.api.model.WardrobeProfile;
import dev.rique.ruinedwardrobe.api.model.WardrobeResult;
import dev.rique.ruinedwardrobe.api.model.WardrobeSet;
import dev.rique.ruinedwardrobe.config.GuiConfig;
import dev.rique.ruinedwardrobe.config.PluginConfig;
import dev.rique.ruinedwardrobe.core.SlotLimitServiceImpl;
import dev.rique.ruinedwardrobe.core.WardrobeArmorBindingService;
import dev.rique.ruinedwardrobe.core.WardrobeServiceImpl;
import dev.rique.ruinedwardrobe.lang.MessageService;
import dev.rique.ruinedwardrobe.scheduler.SchedulerAdapter;
import dev.rique.ruinedwardrobe.util.ArmorPieceMatcher;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WardrobeGuiControllerTest {

    @Test
    void pickupMovesSavedArmorToCursorEvenWhenInventoryIsFull() {
        Fixture fixture = new Fixture();
        ItemStack savedChestplate = item();
        WardrobeProfile profile = profileWithSet(fixture.playerId, 3,
                new WardrobeSet(3, "Slot 3", false, null, savedChestplate, null, null));

        when(fixture.player.getItemOnCursor()).thenReturn(null);
        when(fixture.inventory.firstEmpty()).thenReturn(-1);
        CompletableFuture<WardrobeProfile> refreshProfile = new CompletableFuture<>();
        when(fixture.wardrobeService.getProfile(fixture.playerId))
                .thenReturn(CompletableFuture.completedFuture(profile))
                .thenReturn(refreshProfile);
        when(fixture.wardrobeService.clearArmorPiece(fixture.playerId, 3, "chestplate"))
                .thenReturn(CompletableFuture.completedFuture(
                        new WardrobeResult(ResultCode.SUCCESS, "success.armor-taken", Map.of())));

        fixture.controller.handleMainClick(
                fixture.player,
                fixture.holder(9, 3),
                fixture.clickEvent(9, ClickType.LEFT));

        ArgumentCaptor<ItemStack> cursorItem = ArgumentCaptor.forClass(ItemStack.class);
        verify(fixture.player).setItemOnCursor(cursorItem.capture());
        assertSame(savedChestplate, cursorItem.getValue());
        verify(fixture.messageService, never()).send(
                eq(fixture.player),
                eq("error.inventory-full"),
                anyMap());
    }

    @Test
    void placingArmorOverSavedPieceReturnsPreviousPieceToCursor() {
        Fixture fixture = new Fixture();
        ItemStack newChestplate = item();
        ItemStack oldChestplate = item();
        AtomicReference<ItemStack> cursor = new AtomicReference<>(newChestplate);
        WardrobeProfile profile = profileWithSet(fixture.playerId, 3,
                new WardrobeSet(3, "Slot 3", false, null, oldChestplate, null, null));

        when(fixture.player.getItemOnCursor()).thenAnswer(ignored -> cursor.get());
        doAnswer(invocation -> {
            cursor.set(invocation.getArgument(0));
            return null;
        }).when(fixture.player).setItemOnCursor(nullable(ItemStack.class));
        CompletableFuture<WardrobeProfile> refreshProfile = new CompletableFuture<>();
        when(fixture.wardrobeService.getProfile(fixture.playerId))
                .thenReturn(CompletableFuture.completedFuture(profile))
                .thenReturn(refreshProfile);
        when(fixture.wardrobeService.setArmorPiece(eq(fixture.playerId), eq(3), eq("chestplate"), any(ItemStack.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        new WardrobeResult(ResultCode.SUCCESS, "success.armor-placed", Map.of())));

        try (MockedStatic<ArmorPieceMatcher> matcher = mockStatic(ArmorPieceMatcher.class)) {
            matcher.when(() -> ArmorPieceMatcher.matchesWardrobePiece(eq("chestplate"), any(Material.class)))
                    .thenReturn(true);

            fixture.controller.handleMainClick(
                    fixture.player,
                    fixture.holder(9, 3),
                    fixture.clickEvent(9, ClickType.LEFT));
        }

        ArgumentCaptor<ItemStack> storedItem = ArgumentCaptor.forClass(ItemStack.class);
        verify(fixture.wardrobeService).setArmorPiece(
                eq(fixture.playerId),
                eq(3),
                eq("chestplate"),
                storedItem.capture());
        assertSame(newChestplate, storedItem.getValue());
        assertNotNull(cursor.get());
        assertSame(oldChestplate, cursor.get());
    }

    private static WardrobeProfile profileWithSet(UUID playerId, int slot, WardrobeSet set) {
        return new WardrobeProfile(playerId, 0, -1, 1L, Map.of(slot, set));
    }

    private static ItemStack item() {
        Material material = mock(Material.class);
        ItemStack itemStack = mock(ItemStack.class);
        when(material.isAir()).thenReturn(false);
        when(itemStack.getType()).thenReturn(material);
        when(itemStack.getAmount()).thenReturn(1);
        when(itemStack.clone()).thenReturn(itemStack);
        return itemStack;
    }

    private static final class Fixture {
        private final UUID playerId = UUID.randomUUID();
        private final Player player = mock(Player.class);
        private final PlayerInventory inventory = mock(PlayerInventory.class);
        private final SchedulerAdapter schedulerAdapter = mock(SchedulerAdapter.class);
        private final GuiConfig guiConfig = mock(GuiConfig.class);
        private final PluginConfig pluginConfig = mock(PluginConfig.class);
        private final MessageService messageService = mock(MessageService.class);
        private final WardrobeServiceImpl wardrobeService = mock(WardrobeServiceImpl.class);
        private final SlotLimitServiceImpl slotLimitService = mock(SlotLimitServiceImpl.class);
        private final WardrobeArmorBindingService bindingService = mock(WardrobeArmorBindingService.class);
        private final WardrobeGuiController controller;

        private Fixture() {
            when(player.getUniqueId()).thenReturn(playerId);
            when(player.isOnline()).thenReturn(true);
            when(player.getInventory()).thenReturn(inventory);
            when(schedulerAdapter.runPlayer(eq(player), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.<Runnable>getArgument(1).run();
                return mock(SchedulerAdapter.TaskHandle.class);
            });
            when(guiConfig.previousPageSlot()).thenReturn(-1);
            when(guiConfig.nextPageSlot()).thenReturn(-1);
            when(guiConfig.closeSlot()).thenReturn(-1);
            when(guiConfig.equipButtonSlots()).thenReturn(List.of());
            when(guiConfig.slotDisplayIndices()).thenReturn(List.of(0));
            when(guiConfig.helmetRow()).thenReturn(0);
            when(guiConfig.chestplateRow()).thenReturn(1);
            when(guiConfig.leggingsRow()).thenReturn(2);
            when(guiConfig.bootsRow()).thenReturn(3);
            when(pluginConfig.guiBehaviorSettings())
                    .thenReturn(new PluginConfig.GuiBehaviorSettings(false, false, false, false, 0));

            controller = new WardrobeGuiController(
                    schedulerAdapter,
                    guiConfig,
                    pluginConfig,
                    messageService,
                    wardrobeService,
                    slotLimitService,
                    bindingService);
        }

        private WardrobeMenuHolder holder(int rawSlot, int wardrobeSlot) {
            GuiSession session = new GuiSession(playerId, playerId, 0);
            session.bindSlot(rawSlot, wardrobeSlot);
            return new WardrobeMenuHolder(session);
        }

        private InventoryClickEvent clickEvent(int rawSlot, ClickType clickType) {
            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getRawSlot()).thenReturn(rawSlot);
            when(event.getClick()).thenReturn(clickType);
            return event;
        }
    }
}
