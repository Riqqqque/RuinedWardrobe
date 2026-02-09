package dev.rique.prismwardrobe.core;

import dev.rique.prismwardrobe.api.model.WardrobeProfile;
import dev.rique.prismwardrobe.api.model.WardrobeSet;
import dev.rique.prismwardrobe.scheduler.SchedulerAdapter;
import dev.rique.prismwardrobe.storage.WardrobeRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class WardrobeArmorSyncService {

    private final JavaPlugin plugin;
    private final SchedulerAdapter schedulerAdapter;
    private final WardrobeRepository repository;
    private final WardrobeServiceImpl wardrobeService;
    private final WardrobeArmorBindingService bindingService;
    private final Set<UUID> pendingSync = ConcurrentHashMap.newKeySet();
    private SchedulerAdapter.TaskHandle periodicTask;

    public WardrobeArmorSyncService(
            JavaPlugin plugin,
            SchedulerAdapter schedulerAdapter,
            WardrobeRepository repository,
            WardrobeServiceImpl wardrobeService,
            WardrobeArmorBindingService bindingService
    ) {
        this.plugin = plugin;
        this.schedulerAdapter = schedulerAdapter;
        this.repository = repository;
        this.wardrobeService = wardrobeService;
        this.bindingService = bindingService;
    }

    public void start() {
        periodicTask = schedulerAdapter.runTimerAsync(this::syncOnlinePlayers, 40L, 40L);
    }

    public void stop() {
        if (periodicTask != null) {
            periodicTask.cancel();
        }
    }

    public void requestSync(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!pendingSync.add(playerId)) {
            return;
        }
        schedulerAdapter.runPlayer(player, () -> {
            try {
                snapshotAndSync(player);
            } finally {
                pendingSync.remove(playerId);
            }
        });
    }

    private void syncOnlinePlayers() {
        schedulerAdapter.runGlobal(() -> {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (!bindingService.hasAnyBoundArmor(onlinePlayer)) {
                    continue;
                }
                requestSync(onlinePlayer);
            }
        });
    }

    private void snapshotAndSync(Player player) {
        if (!player.isOnline()) {
            return;
        }
        bindingService.sanitizeForeignBoundItems(player);

        int activeSlot = bindingService.resolveActiveSlot(player);
        if (activeSlot < 1) {
            bindingService.clearActiveSlot(player.getUniqueId());
            return;
        }

        UUID playerId = player.getUniqueId();
        ItemStack helmet = boundPieceCopy(player.getInventory().getHelmet(), playerId, activeSlot);
        ItemStack chest = boundPieceCopy(player.getInventory().getChestplate(), playerId, activeSlot);
        ItemStack legs = boundPieceCopy(player.getInventory().getLeggings(), playerId, activeSlot);
        ItemStack boots = boundPieceCopy(player.getInventory().getBoots(), playerId, activeSlot);

        wardrobeService.getProfile(playerId)
                .thenCompose(profile -> persistIfChanged(profile, activeSlot, helmet, chest, legs, boots))
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Armor sync failed for " + player.getName() + ": " + ex.getMessage());
                    return null;
                });
    }

    private CompletableFuture<Void> persistIfChanged(
            WardrobeProfile profile,
            int activeSlot,
            ItemStack helmet,
            ItemStack chest,
            ItemStack legs,
            ItemStack boots
    ) {
        WardrobeSet existing = profile.getSet(activeSlot);
        if (existing == null) {
            return CompletableFuture.completedFuture(null);
        }
        WardrobeSet updated = new WardrobeSet(
                activeSlot,
                existing.name(),
                existing.favorite(),
                helmet,
                chest,
                legs,
                boots
        );
        if (isEqual(existing, updated)) {
            return CompletableFuture.completedFuture(null);
        }

        boolean noArmorRemaining = !updated.hasAnyArmorPiece();
        CompletableFuture<Void> writeFuture = noArmorRemaining
                ? repository.deleteSet(profile.playerId(), activeSlot)
                : repository.saveSet(profile.playerId(), updated);

        return writeFuture
                .thenCompose(ignored -> noArmorRemaining && profile.selectedSlot() == activeSlot
                        ? repository.setSelectedSlot(profile.playerId(), -1)
                        : CompletableFuture.completedFuture(null))
                .thenCompose(ignored -> repository.loadProfile(profile.playerId()))
                .thenAccept(refreshed -> {
                    wardrobeService.primeProfile(refreshed);
                    if (noArmorRemaining) {
                        bindingService.clearActiveSlot(profile.playerId());
                    }
                });
    }

    private ItemStack boundPieceCopy(ItemStack source, UUID playerId, int activeSlot) {
        if (!bindingService.isBoundTo(source, playerId)) {
            return null;
        }
        if (bindingService.getBoundSlot(source) != activeSlot) {
            return null;
        }
        return bindingService.unbindCopy(source);
    }

    private boolean isEqual(WardrobeSet left, WardrobeSet right) {
        return Objects.equals(left.helmet(), right.helmet())
                && Objects.equals(left.chestplate(), right.chestplate())
                && Objects.equals(left.leggings(), right.leggings())
                && Objects.equals(left.boots(), right.boots());
    }
}
