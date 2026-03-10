package dev.rique.prismwardrobe.core;

import dev.rique.prismwardrobe.api.model.WardrobeProfile;
import dev.rique.prismwardrobe.api.model.WardrobeSet;
import dev.rique.prismwardrobe.scheduler.SchedulerAdapter;
import dev.rique.prismwardrobe.storage.WardrobeRepository;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WardrobeArmorSyncServiceTest {

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

    private WardrobeArmorSyncService createService(
            JavaPlugin plugin,
            SchedulerAdapter schedulerAdapter,
            WardrobeRepository repository,
            WardrobeServiceImpl wardrobeService,
            WardrobeArmorBindingService bindingService
    ) {
        return new WardrobeArmorSyncService(plugin, schedulerAdapter, repository, wardrobeService, bindingService);
    }

    private ItemStack mockArmorPiece() {
        ItemStack item = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        when(item.clone()).thenReturn(clone);
        when(clone.getType()).thenReturn(org.bukkit.Material.STONE);
        return item;
    }
}
