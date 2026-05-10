package dev.rique.ruinedwardrobe.core;

import dev.rique.ruinedwardrobe.api.model.WardrobeProfile;
import dev.rique.ruinedwardrobe.api.model.WardrobeSet;
import dev.rique.ruinedwardrobe.config.PluginConfig;
import dev.rique.ruinedwardrobe.scheduler.SchedulerAdapter;
import dev.rique.ruinedwardrobe.storage.WardrobeRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WardrobeArmorSyncServiceTest {

    @Test
    void detectsTrackedArmorWithoutForcingDatabaseWorkForIdlePlayers() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerAdapter schedulerAdapter = mock(SchedulerAdapter.class);
        WardrobeRepository repository = mock(WardrobeRepository.class);
        WardrobeServiceImpl wardrobeService = mock(WardrobeServiceImpl.class);
        WardrobeArmorBindingService bindingService = mock(WardrobeArmorBindingService.class);
        WardrobeArmorSyncService service = createService(plugin, schedulerAdapter, repository, wardrobeService, bindingService);

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        assertFalse(service.hasTrackedArmor(player));

        when(bindingService.getActiveSlot(playerId)).thenReturn(3);
        assertTrue(service.hasTrackedArmor(player));

        when(bindingService.getActiveSlot(playerId)).thenReturn(-1);
        when(bindingService.hasAnyBoundArmor(player)).thenReturn(true);
        assertTrue(service.hasTrackedArmor(player));
    }

    @Test
    void shutdownFlushDoesNotScheduleBackOntoGlobalThreadForIdlePlayers() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerAdapter schedulerAdapter = mock(SchedulerAdapter.class);
        WardrobeRepository repository = mock(WardrobeRepository.class);
        WardrobeServiceImpl wardrobeService = mock(WardrobeServiceImpl.class);
        WardrobeArmorBindingService bindingService = mock(WardrobeArmorBindingService.class);
        WardrobeArmorSyncService service = createService(plugin, schedulerAdapter, repository, wardrobeService, bindingService);

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(bindingService.getActiveSlot(playerId)).thenReturn(-1);
        when(bindingService.hasAnyBoundArmor(player)).thenReturn(false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));

            service.flushOnlinePlayers().join();

            verify(schedulerAdapter, never()).runGlobal(any(Runnable.class));
            verifyNoInteractions(repository, wardrobeService);
        }
    }

    @Test
    void keepsWardrobeArmorUntouchedWhenKeepInventoryIsEnabled() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerAdapter schedulerAdapter = mock(SchedulerAdapter.class);
        WardrobeRepository repository = mock(WardrobeRepository.class);
        WardrobeServiceImpl wardrobeService = mock(WardrobeServiceImpl.class);
        WardrobeArmorBindingService bindingService = mock(WardrobeArmorBindingService.class);
        WardrobeArmorSyncService service = createService(
                plugin,
                schedulerAdapter,
                repository,
                wardrobeService,
                bindingService);

        Player player = mock(Player.class);
        List<ItemStack> drops = new ArrayList<>(List.of(mock(ItemStack.class)));

        service.handleDeath(player, true, drops).join();

        verifyNoInteractions(bindingService, repository, wardrobeService, schedulerAdapter);
        verifyNoInteractions(player);
    }

    @Test
    void dropsUnboundArmorAndConsumesSavedSetWhenKeepInventoryIsDisabled() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(mock(PluginLogger.class));
        SchedulerAdapter schedulerAdapter = mock(SchedulerAdapter.class);
        WardrobeRepository repository = mock(WardrobeRepository.class);
        WardrobeServiceImpl wardrobeService = mock(WardrobeServiceImpl.class);
        WardrobeArmorBindingService bindingService = mock(WardrobeArmorBindingService.class);
        WardrobeArmorSyncService service = createService(plugin, schedulerAdapter, repository, wardrobeService, bindingService);

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack boundHelmet = mock(ItemStack.class);
        ItemStack storedHelmet = mockArmorPiece();
        ItemStack unboundHelmet = mock(ItemStack.class);
        List<ItemStack> drops = new ArrayList<>(List.of(boundHelmet));

        WardrobeSet storedSet = new WardrobeSet(4, "Slot 4", false, storedHelmet, null, null, null);
        WardrobeProfile profile = new WardrobeProfile(playerId, 0, 4, 1L, Map.of(4, storedSet));
        WardrobeProfile refreshed = new WardrobeProfile(playerId, 0, -1, 2L, Map.of());

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHelmet()).thenReturn(boundHelmet);

        when(bindingService.isBound(boundHelmet)).thenReturn(true);
        when(bindingService.unbindCopy(boundHelmet)).thenReturn(unboundHelmet);
        when(bindingService.isBoundTo(boundHelmet, playerId)).thenReturn(true);
        when(bindingService.getBoundSlot(boundHelmet)).thenReturn(4);
        when(bindingService.getBoundPiece(boundHelmet)).thenReturn("helmet");

        when(wardrobeService.getProfile(playerId)).thenReturn(CompletableFuture.completedFuture(profile));
        when(repository.deleteSet(playerId, 4)).thenReturn(CompletableFuture.completedFuture(null));
        when(repository.setSelectedSlot(playerId, -1)).thenReturn(CompletableFuture.completedFuture(null));
        when(repository.loadProfile(playerId)).thenReturn(CompletableFuture.completedFuture(refreshed));

        service.handleDeath(player, false, drops).join();

        assertSame(unboundHelmet, drops.get(0));
        verify(bindingService).clearActiveSlot(playerId);
        verify(repository).deleteSet(playerId, 4);
        verify(repository).setSelectedSlot(playerId, -1);
        verify(wardrobeService).primeProfile(refreshed);
        verify(repository, never()).saveSet(playerId, storedSet);
    }

    @Test
    void preservesWardrobeSetAndRemovesBoundDropsWhenConfigured() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(mock(PluginLogger.class));
        SchedulerAdapter schedulerAdapter = mock(SchedulerAdapter.class);
        WardrobeRepository repository = mock(WardrobeRepository.class);
        WardrobeServiceImpl wardrobeService = mock(WardrobeServiceImpl.class);
        WardrobeArmorBindingService bindingService = mock(WardrobeArmorBindingService.class);
        WardrobeArmorSyncService service = createService(plugin, schedulerAdapter, repository, wardrobeService, bindingService, true);

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack boundHelmet = mockArmorPiece();
        ItemStack storedHelmet = mockArmorPiece();
        ItemStack liveHelmet = mockArmorPiece();
        List<ItemStack> drops = new ArrayList<>(List.of(boundHelmet));

        WardrobeSet storedSet = new WardrobeSet(4, "Slot 4", false, storedHelmet, null, null, null);
        WardrobeProfile profile = new WardrobeProfile(playerId, 0, 4, 1L, Map.of(4, storedSet));
        WardrobeProfile refreshed = new WardrobeProfile(playerId, 0, -1, 2L, Map.of(4, storedSet));

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Rique");
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHelmet()).thenReturn(boundHelmet);

        when(bindingService.isBound(boundHelmet)).thenReturn(true);
        when(bindingService.hasAnyBoundArmor(player)).thenReturn(true);
        when(bindingService.resolveActiveSlot(player)).thenReturn(4);
        when(bindingService.isBoundTo(boundHelmet, playerId)).thenReturn(true);
        when(bindingService.getBoundSlot(boundHelmet)).thenReturn(4);
        when(bindingService.unbindCopy(boundHelmet)).thenReturn(liveHelmet);

        when(wardrobeService.getProfile(playerId)).thenReturn(CompletableFuture.completedFuture(profile));
        when(repository.saveSet(org.mockito.ArgumentMatchers.eq(playerId), org.mockito.ArgumentMatchers.any(WardrobeSet.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(repository.setSelectedSlot(playerId, -1)).thenReturn(CompletableFuture.completedFuture(null));
        when(repository.loadProfile(playerId)).thenReturn(CompletableFuture.completedFuture(refreshed));

        service.handleDeath(player, false, drops).join();

        assertTrue(drops.isEmpty());
        verify(repository).saveSet(org.mockito.ArgumentMatchers.eq(playerId), org.mockito.ArgumentMatchers.any(WardrobeSet.class));
        verify(repository).setSelectedSlot(playerId, -1);
        verify(repository, never()).deleteSet(playerId, 4);
        verify(wardrobeService).primeProfile(refreshed);
    }

    @Test
    void preserveDeathModeDoesNotTouchDatabaseForIdlePlayers() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SchedulerAdapter schedulerAdapter = mock(SchedulerAdapter.class);
        WardrobeRepository repository = mock(WardrobeRepository.class);
        WardrobeServiceImpl wardrobeService = mock(WardrobeServiceImpl.class);
        WardrobeArmorBindingService bindingService = mock(WardrobeArmorBindingService.class);
        WardrobeArmorSyncService service = createService(plugin, schedulerAdapter, repository, wardrobeService, bindingService, true);

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        service.handleDeath(player, false, new ArrayList<>()).join();

        verifyNoInteractions(repository, wardrobeService, schedulerAdapter);
    }

    @Test
    void syncNowRepairsSelectedSlotWithoutRequiringOnlinePlayer() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(mock(PluginLogger.class));
        SchedulerAdapter schedulerAdapter = mock(SchedulerAdapter.class);
        WardrobeRepository repository = mock(WardrobeRepository.class);
        WardrobeServiceImpl wardrobeService = mock(WardrobeServiceImpl.class);
        WardrobeArmorBindingService bindingService = mock(WardrobeArmorBindingService.class);
        WardrobeArmorSyncService service = createService(plugin, schedulerAdapter, repository, wardrobeService, bindingService);

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack storedHelmet = mockArmorPiece();
        WardrobeSet storedSet = new WardrobeSet(4, "Slot 4", false, storedHelmet, null, null, null);
        ItemStack profileHelmet = storedSet.helmet();
        ItemStack boundHelmet = mock(ItemStack.class);
        WardrobeProfile profile = new WardrobeProfile(playerId, 0, -1, 1L, Map.of(4, storedSet));

        when(profileHelmet.clone()).thenReturn(profileHelmet);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(false);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHelmet()).thenReturn(boundHelmet);
        when(bindingService.resolveActiveSlot(player)).thenReturn(4);
        when(bindingService.isBoundTo(boundHelmet, playerId)).thenReturn(true);
        when(bindingService.getBoundSlot(boundHelmet)).thenReturn(4);
        when(bindingService.unbindCopy(boundHelmet)).thenReturn(profileHelmet);
        when(wardrobeService.getProfile(playerId)).thenReturn(CompletableFuture.completedFuture(profile));
        when(repository.setSelectedSlot(playerId, 4)).thenReturn(CompletableFuture.completedFuture(null));

        service.syncNow(player).join();

        verify(repository).setSelectedSlot(playerId, 4);
        verify(repository, never()).saveSet(playerId, storedSet);
        verify(wardrobeService).invalidateCache(playerId);
    }

    private WardrobeArmorSyncService createService(
            JavaPlugin plugin,
            SchedulerAdapter schedulerAdapter,
            WardrobeRepository repository,
            WardrobeServiceImpl wardrobeService,
            WardrobeArmorBindingService bindingService
    ) {
        return createService(plugin, schedulerAdapter, repository, wardrobeService, bindingService, false);
    }

    private WardrobeArmorSyncService createService(
            JavaPlugin plugin,
            SchedulerAdapter schedulerAdapter,
            WardrobeRepository repository,
            WardrobeServiceImpl wardrobeService,
            WardrobeArmorBindingService bindingService,
            boolean keepWardrobeOnDeath
    ) {
        return new WardrobeArmorSyncService(plugin, schedulerAdapter, repository, wardrobeService, bindingService,
                new PluginConfig.ArmorSyncSettings(true, 40L, 500, 10L),
                new PluginConfig.DeathSettings(keepWardrobeOnDeath),
                WardrobeAuditLogger.disabled());
    }

    private ItemStack mockArmorPiece() {
        ItemStack item = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        when(item.clone()).thenReturn(clone);
        org.bukkit.Material material = mock(org.bukkit.Material.class);
        when(material.isAir()).thenReturn(false);
        when(item.getType()).thenReturn(material);
        when(clone.getType()).thenReturn(material);
        return item;
    }
}
