package dev.rique.ruinedwardrobe.core;

import dev.rique.ruinedwardrobe.api.model.RestrictionDecision;
import dev.rique.ruinedwardrobe.api.model.ResultCode;
import dev.rique.ruinedwardrobe.api.model.WardrobeProfile;
import dev.rique.ruinedwardrobe.api.model.WardrobeResult;
import dev.rique.ruinedwardrobe.api.model.WardrobeSet;
import dev.rique.ruinedwardrobe.cache.WardrobeCache;
import dev.rique.ruinedwardrobe.config.PluginConfig;
import dev.rique.ruinedwardrobe.scheduler.SchedulerAdapter;
import dev.rique.ruinedwardrobe.storage.WardrobeRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WardrobeServiceImplTest {

    @Test
    void clearArmorPieceRestoresEquippedArmorWhenDeleteFails() {
        UUID playerId = UUID.randomUUID();
        SchedulerAdapter schedulerAdapter = immediateScheduler();
        WardrobeRepository repository = mock(WardrobeRepository.class);
        WardrobeCache cache = mock(WardrobeCache.class);
        SlotLimitServiceImpl slotLimitService = mock(SlotLimitServiceImpl.class);
        RestrictionServiceImpl restrictionService = mock(RestrictionServiceImpl.class);
        WardrobeArmorBindingService bindingService = mock(WardrobeArmorBindingService.class);
        PluginConfig pluginConfig = mock(PluginConfig.class);
        WardrobeServiceImpl service = new WardrobeServiceImpl(
                schedulerAdapter,
                repository,
                cache,
                slotLimitService,
                restrictionService,
                bindingService,
                pluginConfig,
                WardrobeAuditLogger.disabled());

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        PluginManager pluginManager = mock(PluginManager.class);
        ItemStack liveHelmet = armorPiece();
        ItemStack capturedHelmet = armorPiece();
        ItemStack restoredHelmet = armorPiece();
        ItemStack liveUnboundHelmet = armorPiece();
        ItemStack liveUnboundHelmetClone = armorPiece();

        when(liveHelmet.clone()).thenReturn(capturedHelmet);
        when(capturedHelmet.clone()).thenReturn(restoredHelmet);
        when(liveUnboundHelmet.clone()).thenReturn(liveUnboundHelmetClone);

        WardrobeSet storedSet = new WardrobeSet(3, "Slot 3", false, armorPiece(), null, null, null);
        WardrobeProfile profile = new WardrobeProfile(playerId, 0, 3, 1L, Map.of(3, storedSet));

        when(cache.get(playerId)).thenReturn(Optional.of(profile));
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("ruinedwardrobe.use")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHelmet()).thenReturn(liveHelmet);
        when(slotLimitService.getMaxSlots(player)).thenReturn(9);
        when(bindingService.resolveActiveSlot(player)).thenReturn(3);
        when(bindingService.getActiveSlot(playerId)).thenReturn(3);
        when(bindingService.isBoundTo(liveHelmet, playerId)).thenReturn(true);
        when(bindingService.getBoundSlot(liveHelmet)).thenReturn(3);
        when(bindingService.unbindCopy(liveHelmet)).thenReturn(liveUnboundHelmet);
        when(repository.deleteSet(playerId, 3)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("write failed")));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            WardrobeResult result = service.clearArmorPiece(playerId, 3, "helmet").join();

            assertEquals(ResultCode.ERROR_STORAGE, result.code());
            verify(inventory).setHelmet(null);
            verify(inventory).setHelmet(restoredHelmet);
            verify(bindingService).setActiveSlot(playerId, 3);
            verify(repository, never()).setSelectedSlot(playerId, -1);
        }
    }

    @Test
    void clearArmorPieceUsesLiveEquippedArmorForRemainingPieces() {
        UUID playerId = UUID.randomUUID();
        SchedulerAdapter schedulerAdapter = immediateScheduler();
        WardrobeRepository repository = mock(WardrobeRepository.class);
        WardrobeCache cache = mock(WardrobeCache.class);
        SlotLimitServiceImpl slotLimitService = mock(SlotLimitServiceImpl.class);
        RestrictionServiceImpl restrictionService = mock(RestrictionServiceImpl.class);
        WardrobeArmorBindingService bindingService = mock(WardrobeArmorBindingService.class);
        PluginConfig pluginConfig = mock(PluginConfig.class);
        WardrobeServiceImpl service = new WardrobeServiceImpl(
                schedulerAdapter,
                repository,
                cache,
                slotLimitService,
                restrictionService,
                bindingService,
                pluginConfig,
                WardrobeAuditLogger.disabled());

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        PluginManager pluginManager = mock(PluginManager.class);
        ItemStack liveHelmet = armorPiece();
        ItemStack liveHelmetUnbound = armorPiece();
        ItemStack liveHelmetBase = armorPiece();
        ItemStack liveChest = armorPiece();
        ItemStack liveChestUnbound = armorPiece();
        ItemStack liveChestBase = armorPiece();
        ItemStack savedChest = armorPiece();
        ItemStack storedHelmet = armorPiece();
        ItemStack storedChest = armorPiece();
        ItemStack refreshedChest = armorPiece();

        when(liveHelmetUnbound.clone()).thenReturn(liveHelmetBase);
        when(liveChestUnbound.clone()).thenReturn(liveChestBase);
        when(liveChestBase.clone()).thenReturn(savedChest);
        when(storedChest.clone()).thenReturn(refreshedChest);

        WardrobeSet storedSet = new WardrobeSet(3, "Slot 3", false, storedHelmet, storedChest, null, null);
        WardrobeProfile profile = new WardrobeProfile(playerId, 0, 3, 1L, Map.of(3, storedSet));
        WardrobeProfile refreshed = new WardrobeProfile(playerId, 0, -1, 2L, Map.of());

        when(cache.get(playerId)).thenReturn(Optional.of(profile));
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("ruinedwardrobe.use")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHelmet()).thenReturn(liveHelmet);
        when(inventory.getChestplate()).thenReturn(liveChest);
        when(slotLimitService.getMaxSlots(player)).thenReturn(9);
        when(bindingService.resolveActiveSlot(player)).thenReturn(3);
        when(bindingService.getActiveSlot(playerId)).thenReturn(3);
        when(bindingService.isBoundTo(liveHelmet, playerId)).thenReturn(true);
        when(bindingService.isBoundTo(liveChest, playerId)).thenReturn(true);
        when(bindingService.getBoundSlot(liveHelmet)).thenReturn(3);
        when(bindingService.getBoundSlot(liveChest)).thenReturn(3);
        when(bindingService.unbindCopy(liveHelmet)).thenReturn(liveHelmetUnbound);
        when(bindingService.unbindCopy(liveChest)).thenReturn(liveChestUnbound);
        when(repository.saveSet(eq(playerId), any(WardrobeSet.class))).thenReturn(CompletableFuture.completedFuture(null));
        when(repository.setSelectedSlot(playerId, -1)).thenReturn(CompletableFuture.completedFuture(null));
        when(repository.loadProfile(playerId)).thenReturn(CompletableFuture.completedFuture(refreshed));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            WardrobeResult result = service.clearArmorPiece(playerId, 3, "helmet").join();

            assertEquals(ResultCode.SUCCESS, result.code());
            ArgumentCaptor<WardrobeSet> captor = ArgumentCaptor.forClass(WardrobeSet.class);
            verify(repository).saveSet(eq(playerId), captor.capture());
            assertSame(savedChest, captor.getValue().chestplate());
            assertEquals(null, captor.getValue().helmet());
        }
    }

    @Test
    void equipSetRestoresPreviousArmorWhenSelectedSlotWriteFails() {
        UUID playerId = UUID.randomUUID();
        SchedulerAdapter schedulerAdapter = immediateScheduler();
        WardrobeRepository repository = mock(WardrobeRepository.class);
        WardrobeCache cache = mock(WardrobeCache.class);
        SlotLimitServiceImpl slotLimitService = mock(SlotLimitServiceImpl.class);
        RestrictionServiceImpl restrictionService = mock(RestrictionServiceImpl.class);
        WardrobeArmorBindingService bindingService = mock(WardrobeArmorBindingService.class);
        PluginConfig pluginConfig = mock(PluginConfig.class);
        WardrobeServiceImpl service = new WardrobeServiceImpl(
                schedulerAdapter,
                repository,
                cache,
                slotLimitService,
                restrictionService,
                bindingService,
                pluginConfig,
                WardrobeAuditLogger.disabled());

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        PluginManager pluginManager = mock(PluginManager.class);
        ItemStack currentHelmet = armorPiece();
        ItemStack capturedCurrentHelmet = armorPiece();
        ItemStack restoredCurrentHelmet = armorPiece();
        ItemStack targetHelmet = armorPiece();
        ItemStack targetStoredHelmet = armorPiece();
        ItemStack targetBoundHelmet = armorPiece();

        when(currentHelmet.clone()).thenReturn(capturedCurrentHelmet);
        when(capturedCurrentHelmet.clone()).thenReturn(restoredCurrentHelmet);
        when(targetHelmet.clone()).thenReturn(targetStoredHelmet);

        WardrobeSet targetSet = new WardrobeSet(3, "Slot 3", false, targetHelmet, null, null, null);
        WardrobeProfile profile = new WardrobeProfile(playerId, 0, 2, 1L, Map.of(3, targetSet));

        when(cache.get(playerId)).thenReturn(Optional.of(profile));
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("ruinedwardrobe.use")).thenReturn(true);
        when(player.hasPermission("ruinedwardrobe.bypass.cooldown")).thenReturn(false);
        when(player.hasPermission("ruinedwardrobe.bypass.restrictions")).thenReturn(false);
        when(player.hasPermission("ruinedwardrobe.bypass.emptycheck")).thenReturn(false);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHelmet()).thenReturn(currentHelmet);
        when(slotLimitService.getMaxSlots(player)).thenReturn(9);
        when(restrictionService.canEquip(player)).thenReturn(RestrictionDecision.allow());
        when(bindingService.resolveActiveSlot(player)).thenReturn(2);
        when(bindingService.isBoundTo(currentHelmet, playerId)).thenReturn(true);
        when(bindingService.getBoundSlot(currentHelmet)).thenReturn(2);
        when(bindingService.bindCopy(any(ItemStack.class), eq(playerId), eq(3), eq("helmet"))).thenReturn(targetBoundHelmet);
        when(repository.setSelectedSlot(playerId, 3)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("write failed")));
        when(pluginConfig.equipCooldownMillis()).thenReturn(3000L);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            WardrobeResult result = service.equipSet(playerId, 3).join();

            assertEquals(ResultCode.ERROR_STORAGE, result.code());
            assertEquals(0L, service.getRemainingCooldownMillis(playerId));
            verify(inventory).setHelmet(targetBoundHelmet);
            verify(inventory).setHelmet(restoredCurrentHelmet);
            verify(bindingService).setActiveSlot(playerId, 3);
            verify(bindingService).setActiveSlot(playerId, 2);
            verify(pluginManager, never()).callEvent(any(dev.rique.ruinedwardrobe.api.event.WardrobeEquippedEvent.class));
        }
    }

    @Test
    void equipSetReplacesPreviousWardrobeArmorInsteadOfMergingPartialSets() {
        UUID playerId = UUID.randomUUID();
        SchedulerAdapter schedulerAdapter = immediateScheduler();
        WardrobeRepository repository = mock(WardrobeRepository.class);
        WardrobeCache cache = mock(WardrobeCache.class);
        SlotLimitServiceImpl slotLimitService = mock(SlotLimitServiceImpl.class);
        RestrictionServiceImpl restrictionService = mock(RestrictionServiceImpl.class);
        WardrobeArmorBindingService bindingService = mock(WardrobeArmorBindingService.class);
        PluginConfig pluginConfig = mock(PluginConfig.class);
        WardrobeServiceImpl service = new WardrobeServiceImpl(
                schedulerAdapter,
                repository,
                cache,
                slotLimitService,
                restrictionService,
                bindingService,
                pluginConfig,
                WardrobeAuditLogger.disabled());

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        PluginManager pluginManager = mock(PluginManager.class);
        ItemStack oldHelmet = armorPiece();
        ItemStack oldChest = armorPiece();
        ItemStack oldLegs = armorPiece();
        ItemStack oldBoots = armorPiece();
        ItemStack targetLegs = armorPiece();
        ItemStack targetStoredLegs = armorPiece();
        ItemStack targetBoundLegs = armorPiece();
        when(targetLegs.clone()).thenReturn(targetStoredLegs);
        WardrobeSet targetSet = new WardrobeSet(4, "Gold Pants", false, null, null, targetLegs, null);
        WardrobeProfile profile = new WardrobeProfile(playerId, 0, 2, 1L, Map.of(4, targetSet));
        WardrobeProfile refreshed = new WardrobeProfile(playerId, 0, 4, 2L, Map.of(4, targetSet));

        when(cache.get(playerId)).thenReturn(Optional.of(profile));
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("ruinedwardrobe.use")).thenReturn(true);
        when(player.hasPermission("ruinedwardrobe.bypass.cooldown")).thenReturn(false);
        when(player.hasPermission("ruinedwardrobe.bypass.restrictions")).thenReturn(false);
        when(player.hasPermission("ruinedwardrobe.bypass.emptycheck")).thenReturn(false);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHelmet()).thenReturn(oldHelmet);
        when(inventory.getChestplate()).thenReturn(oldChest);
        when(inventory.getLeggings()).thenReturn(oldLegs);
        when(inventory.getBoots()).thenReturn(oldBoots);
        when(slotLimitService.getMaxSlots(player)).thenReturn(9);
        when(restrictionService.canEquip(player)).thenReturn(RestrictionDecision.allow());
        when(bindingService.resolveActiveSlot(player)).thenReturn(2);
        when(bindingService.isBoundTo(oldHelmet, playerId)).thenReturn(true);
        when(bindingService.isBoundTo(oldChest, playerId)).thenReturn(true);
        when(bindingService.isBoundTo(oldLegs, playerId)).thenReturn(true);
        when(bindingService.isBoundTo(oldBoots, playerId)).thenReturn(true);
        when(bindingService.bindCopy(targetStoredLegs, playerId, 4, "leggings")).thenReturn(targetBoundLegs);
        when(repository.setSelectedSlot(playerId, 4)).thenReturn(CompletableFuture.completedFuture(null));
        when(repository.loadProfile(playerId)).thenReturn(CompletableFuture.completedFuture(refreshed));
        when(pluginConfig.equipCooldownMillis()).thenReturn(3000L);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            WardrobeResult result = service.equipSet(playerId, 4).join();

            assertEquals(ResultCode.SUCCESS_EQUIPPED, result.code());
            verify(inventory).setHelmet(null);
            verify(inventory).setChestplate(null);
            verify(inventory).setLeggings(null);
            verify(inventory).setLeggings(targetBoundLegs);
            verify(inventory).setBoots(null);
            verify(repository).setSelectedSlot(playerId, 4);
            verify(bindingService).setActiveSlot(playerId, 4);
        }
    }

    @Test
    void equipPartialSetDoesNotClearNormalArmorInUnusedSlots() {
        UUID playerId = UUID.randomUUID();
        SchedulerAdapter schedulerAdapter = immediateScheduler();
        WardrobeRepository repository = mock(WardrobeRepository.class);
        WardrobeCache cache = mock(WardrobeCache.class);
        SlotLimitServiceImpl slotLimitService = mock(SlotLimitServiceImpl.class);
        RestrictionServiceImpl restrictionService = mock(RestrictionServiceImpl.class);
        WardrobeArmorBindingService bindingService = mock(WardrobeArmorBindingService.class);
        PluginConfig pluginConfig = mock(PluginConfig.class);
        WardrobeServiceImpl service = new WardrobeServiceImpl(
                schedulerAdapter,
                repository,
                cache,
                slotLimitService,
                restrictionService,
                bindingService,
                pluginConfig,
                WardrobeAuditLogger.disabled());

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        PluginManager pluginManager = mock(PluginManager.class);
        ItemStack normalHelmet = armorPiece();
        ItemStack targetLegs = armorPiece();
        ItemStack targetStoredLegs = armorPiece();
        ItemStack targetBoundLegs = armorPiece();
        when(targetLegs.clone()).thenReturn(targetStoredLegs);
        WardrobeSet targetSet = new WardrobeSet(4, "Gold Pants", false, null, null, targetLegs, null);
        WardrobeProfile profile = new WardrobeProfile(playerId, 0, -1, 1L, Map.of(4, targetSet));
        WardrobeProfile refreshed = new WardrobeProfile(playerId, 0, 4, 2L, Map.of(4, targetSet));

        when(cache.get(playerId)).thenReturn(Optional.of(profile));
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("ruinedwardrobe.use")).thenReturn(true);
        when(player.hasPermission("ruinedwardrobe.bypass.cooldown")).thenReturn(false);
        when(player.hasPermission("ruinedwardrobe.bypass.restrictions")).thenReturn(false);
        when(player.hasPermission("ruinedwardrobe.bypass.emptycheck")).thenReturn(false);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHelmet()).thenReturn(normalHelmet);
        when(slotLimitService.getMaxSlots(player)).thenReturn(9);
        when(restrictionService.canEquip(player)).thenReturn(RestrictionDecision.allow());
        when(bindingService.resolveActiveSlot(player)).thenReturn(-1);
        when(bindingService.isBoundTo(normalHelmet, playerId)).thenReturn(false);
        when(bindingService.bindCopy(targetStoredLegs, playerId, 4, "leggings")).thenReturn(targetBoundLegs);
        when(repository.setSelectedSlot(playerId, 4)).thenReturn(CompletableFuture.completedFuture(null));
        when(repository.loadProfile(playerId)).thenReturn(CompletableFuture.completedFuture(refreshed));
        when(pluginConfig.equipCooldownMillis()).thenReturn(3000L);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            WardrobeResult result = service.equipSet(playerId, 4).join();

            assertEquals(ResultCode.SUCCESS_EQUIPPED, result.code());
            verify(inventory, never()).setHelmet(any());
            verify(inventory).setLeggings(targetBoundLegs);
        }
    }

    @Test
    void deleteSetRequiresUsePermission() {
        UUID playerId = UUID.randomUUID();
        SchedulerAdapter schedulerAdapter = immediateScheduler();
        WardrobeRepository repository = mock(WardrobeRepository.class);
        WardrobeServiceImpl service = new WardrobeServiceImpl(
                schedulerAdapter,
                repository,
                mock(WardrobeCache.class),
                mock(SlotLimitServiceImpl.class),
                mock(RestrictionServiceImpl.class),
                mock(WardrobeArmorBindingService.class),
                mock(PluginConfig.class),
                WardrobeAuditLogger.disabled());

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("ruinedwardrobe.use")).thenReturn(false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

            WardrobeResult result = service.deleteSet(playerId, 1).join();

            assertEquals(ResultCode.DENIED_NO_PERMISSION, result.code());
            verify(repository, never()).deleteSet(any(UUID.class), any(Integer.class));
        }
    }

    @Test
    void renameSetRejectsMissingSavedSet() {
        UUID playerId = UUID.randomUUID();
        SchedulerAdapter schedulerAdapter = immediateScheduler();
        WardrobeRepository repository = mock(WardrobeRepository.class);
        WardrobeCache cache = mock(WardrobeCache.class);
        SlotLimitServiceImpl slotLimitService = mock(SlotLimitServiceImpl.class);
        WardrobeProfile profile = new WardrobeProfile(playerId, 0, -1, 1L, Map.of());
        WardrobeServiceImpl service = new WardrobeServiceImpl(
                schedulerAdapter,
                repository,
                cache,
                slotLimitService,
                mock(RestrictionServiceImpl.class),
                mock(WardrobeArmorBindingService.class),
                mock(PluginConfig.class),
                WardrobeAuditLogger.disabled());

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("ruinedwardrobe.use")).thenReturn(true);
        when(slotLimitService.getMaxSlots(player)).thenReturn(9);
        when(cache.get(playerId)).thenReturn(Optional.of(profile));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

            WardrobeResult result = service.renameSet(playerId, 2, "Weekend").join();

            assertEquals(ResultCode.DENIED_NOTHING_SAVED, result.code());
            verify(repository, never()).renameSet(any(UUID.class), any(Integer.class), any(String.class));
        }
    }

    private SchedulerAdapter immediateScheduler() {
        SchedulerAdapter schedulerAdapter = mock(SchedulerAdapter.class);
        SchedulerAdapter.TaskHandle handle = mock(SchedulerAdapter.TaskHandle.class);
        when(schedulerAdapter.runPlayer(any(Player.class), any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return handle;
        });
        return schedulerAdapter;
    }

    private ItemStack armorPiece() {
        ItemStack itemStack = mock(ItemStack.class);
        org.bukkit.Material material = mock(org.bukkit.Material.class);
        when(material.isAir()).thenReturn(false);
        when(itemStack.getType()).thenReturn(material);
        when(itemStack.clone()).thenReturn(itemStack);
        return itemStack;
    }
}
