package dev.rique.prismwardrobe.core;

import dev.rique.prismwardrobe.api.model.WardrobeProfile;
import dev.rique.prismwardrobe.api.model.WardrobeSet;
import dev.rique.prismwardrobe.scheduler.SchedulerAdapter;
import dev.rique.prismwardrobe.storage.WardrobeRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
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
    private final Map<UUID, CompletableFuture<Void>> pendingSync = new ConcurrentHashMap<>();
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

    public CompletableFuture<Void> requestSync(Player player) {
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(null);
        }
        UUID playerId = player.getUniqueId();
        CompletableFuture<Void> completion = new CompletableFuture<>();
        CompletableFuture<Void> existing = pendingSync.putIfAbsent(playerId, completion);
        if (existing != null) {
            return existing;
        }
        schedulerAdapter.runPlayer(player, () -> {
            try {
                snapshotAndSync(player).whenComplete((ignored, throwable) -> {
                    pendingSync.remove(playerId, completion);
                    if (throwable != null) {
                        plugin.getLogger().warning("Armor sync failed for " + player.getName() + ": " + throwable.getMessage());
                        completion.completeExceptionally(throwable);
                        return;
                    }
                    completion.complete(null);
                });
            } catch (Throwable throwable) {
                pendingSync.remove(playerId, completion);
                completion.completeExceptionally(throwable);
            }
        });
        return completion;
    }

    public CompletableFuture<Void> clearSelectedSlot(UUID playerId) {
        bindingService.clearActiveSlot(playerId);
        return wardrobeService.getProfile(playerId).thenCompose(profile -> {
            if (profile.selectedSlot() < 1) {
                return CompletableFuture.completedFuture(null);
            }
            return repository.setSelectedSlot(playerId, -1)
                    .thenCompose(ignored -> repository.loadProfile(playerId))
                    .thenAccept(wardrobeService::primeProfile);
        });
    }

    public CompletableFuture<Void> handleDeath(Player player, boolean keepInventory, List<ItemStack> drops) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (keepInventory) {
            return CompletableFuture.completedFuture(null);
        }

        unbindDrops(drops);

        UUID playerId = player.getUniqueId();
        bindingService.clearActiveSlot(playerId);

        Map<Integer, Set<String>> lostPiecesBySlot = collectLostArmor(player);
        return wardrobeService.getProfile(playerId).thenCompose(profile -> {
            List<CompletableFuture<Void>> writes = new ArrayList<>();
            for (Map.Entry<Integer, Set<String>> entry : lostPiecesBySlot.entrySet()) {
                WardrobeSet existing = profile.getSet(entry.getKey());
                if (existing == null) {
                    continue;
                }
                WardrobeSet updated = removeLostPieces(existing, entry.getValue());
                CompletableFuture<Void> writeFuture = updated.hasAnyArmorPiece()
                        ? repository.saveSet(profile.playerId(), updated)
                        : repository.deleteSet(profile.playerId(), updated.slot());
                writes.add(writeFuture);
            }

            CompletableFuture<Void> setSelectedFuture = profile.selectedSlot() < 1
                    ? CompletableFuture.completedFuture(null)
                    : repository.setSelectedSlot(profile.playerId(), -1);

            CompletableFuture<Void> writesFuture = writes.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.allOf(writes.toArray(new CompletableFuture[0]));

            return writesFuture
                    .thenCompose(ignored -> setSelectedFuture)
                    .thenCompose(ignored -> repository.loadProfile(profile.playerId()))
                    .thenAccept(wardrobeService::primeProfile);
        });
    }

    public CompletableFuture<Void> flushOnlinePlayers() {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        schedulerAdapter.runGlobal(() -> {
            List<CompletableFuture<Void>> syncTasks = new ArrayList<>();
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                syncTasks.add(requestSync(onlinePlayer));
            }
            CompletableFuture.allOf(syncTasks.toArray(new CompletableFuture[0]))
                    .whenComplete((ignored, throwable) -> {
                        if (throwable != null) {
                            completion.completeExceptionally(throwable);
                            return;
                        }
                        completion.complete(null);
                    });
        });
        return completion;
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

    private CompletableFuture<Void> snapshotAndSync(Player player) {
        if (!player.isOnline()) {
            return CompletableFuture.completedFuture(null);
        }
        bindingService.sanitizeForeignBoundItems(player);

        int activeSlot = bindingService.resolveActiveSlot(player);
        if (activeSlot < 1) {
            return clearSelectedSlot(player.getUniqueId());
        }

        UUID playerId = player.getUniqueId();
        ItemStack helmet = boundPieceCopy(player.getInventory().getHelmet(), playerId, activeSlot);
        ItemStack chest = boundPieceCopy(player.getInventory().getChestplate(), playerId, activeSlot);
        ItemStack legs = boundPieceCopy(player.getInventory().getLeggings(), playerId, activeSlot);
        ItemStack boots = boundPieceCopy(player.getInventory().getBoots(), playerId, activeSlot);

        return wardrobeService.getProfile(playerId)
                .thenCompose(profile -> persistIfChanged(profile, activeSlot, helmet, chest, legs, boots));
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
                .thenCompose(ignored -> WardrobeSafetyDecisions.shouldClearSelectedSlotAfterSync(
                        noArmorRemaining, profile.selectedSlot(), activeSlot)
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

    private void unbindDrops(List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) {
            return;
        }
        ListIterator<ItemStack> iterator = drops.listIterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (!bindingService.isBound(drop)) {
                continue;
            }
            iterator.set(bindingService.unbindCopy(drop));
        }
    }

    private Map<Integer, Set<String>> collectLostArmor(Player player) {
        Map<Integer, Set<String>> lostPiecesBySlot = new HashMap<>();
        UUID playerId = player.getUniqueId();
        PlayerInventory inventory = player.getInventory();

        collectLostPiece(lostPiecesBySlot, playerId, inventory.getHelmet(), "helmet");
        collectLostPiece(lostPiecesBySlot, playerId, inventory.getChestplate(), "chestplate");
        collectLostPiece(lostPiecesBySlot, playerId, inventory.getLeggings(), "leggings");
        collectLostPiece(lostPiecesBySlot, playerId, inventory.getBoots(), "boots");

        return lostPiecesBySlot;
    }

    private void collectLostPiece(Map<Integer, Set<String>> lostPiecesBySlot, UUID playerId, ItemStack itemStack, String fallbackPiece) {
        if (!bindingService.isBoundTo(itemStack, playerId)) {
            return;
        }

        int slot = bindingService.getBoundSlot(itemStack);
        if (slot < 1) {
            return;
        }

        String piece = bindingService.getBoundPiece(itemStack);
        if (piece == null || piece.isBlank()) {
            piece = fallbackPiece;
        }

        lostPiecesBySlot.computeIfAbsent(slot, ignored -> new HashSet<>()).add(piece);
    }

    private WardrobeSet removeLostPieces(WardrobeSet existing, Set<String> lostPieces) {
        ItemStack helmet = lostPieces.contains("helmet") ? null : existing.helmet();
        ItemStack chestplate = lostPieces.contains("chestplate") ? null : existing.chestplate();
        ItemStack leggings = lostPieces.contains("leggings") ? null : existing.leggings();
        ItemStack boots = lostPieces.contains("boots") ? null : existing.boots();

        return new WardrobeSet(
                existing.slot(),
                existing.name(),
                existing.favorite(),
                helmet,
                chestplate,
                leggings,
                boots
        );
    }
}
