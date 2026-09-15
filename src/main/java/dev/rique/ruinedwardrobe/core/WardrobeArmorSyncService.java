package dev.rique.ruinedwardrobe.core;

import dev.rique.ruinedwardrobe.api.model.WardrobeProfile;
import dev.rique.ruinedwardrobe.api.model.WardrobeSet;
import dev.rique.ruinedwardrobe.config.PluginConfig;
import dev.rique.ruinedwardrobe.scheduler.SchedulerAdapter;
import dev.rique.ruinedwardrobe.storage.WardrobeRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private final PluginConfig.ArmorSyncSettings settings;
    private final PluginConfig.DeathSettings deathSettings;
    private final WardrobeAuditLogger auditLogger;
    private final Map<UUID, PendingPlayerSync> pendingSync = new ConcurrentHashMap<>();
    private final Set<UUID> deathInProgress = ConcurrentHashMap.newKeySet();
    private SchedulerAdapter.TaskHandle periodicTask;
    private int scanCursor;

    public WardrobeArmorSyncService(
            JavaPlugin plugin,
            SchedulerAdapter schedulerAdapter,
            WardrobeRepository repository,
            WardrobeServiceImpl wardrobeService,
            WardrobeArmorBindingService bindingService,
            PluginConfig.ArmorSyncSettings settings,
            PluginConfig.DeathSettings deathSettings,
            WardrobeAuditLogger auditLogger
    ) {
        this.plugin = plugin;
        this.schedulerAdapter = schedulerAdapter;
        this.repository = repository;
        this.wardrobeService = wardrobeService;
        this.bindingService = bindingService;
        this.settings = settings;
        this.deathSettings = deathSettings;
        this.auditLogger = auditLogger;
    }

    public void start() {
        if (!settings.enabled()) {
            return;
        }
        long intervalTicks = settings.scanIntervalTicks();
        periodicTask = schedulerAdapter.runTimerAsync(this::syncOnlinePlayers, intervalTicks, intervalTicks);
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
        if (deathInProgress.contains(playerId)) {
            return CompletableFuture.completedFuture(null);
        }
        PendingPlayerSync pending = new PendingPlayerSync();
        while (true) {
            PendingPlayerSync existing = pendingSync.putIfAbsent(playerId, pending);
            if (existing == null) {
                runQueuedSync(player, pending);
                return pending.completion();
            }
            if (existing.requestAgain()) {
                return existing.completion();
            }
            pendingSync.remove(playerId, existing);
        }
    }

    private void runQueuedSync(Player player, PendingPlayerSync pending) {
        UUID playerId = player.getUniqueId();
        try {
            schedulerAdapter.runPlayer(player, () -> {
                try {
                    PlayerArmorSnapshot snapshot = captureSnapshot(player, true);
                    wardrobeService.runExclusive(playerId, () -> persistSnapshotUnlocked(snapshot, true)).whenComplete((ignored, throwable) -> {
                        if (throwable != null) {
                            plugin.getLogger().warning("Armor sync failed for " + player.getName() + ": " + throwable.getMessage());
                            auditLogger.error(
                                    "ARMOR_SYNC_ERROR",
                                    playerId,
                                    player.getName(),
                                    throwable,
                                    Map.of("phase", "queued-sync"));
                            pendingSync.remove(playerId, pending);
                            pending.completeExceptionally(throwable);
                            return;
                        }
                        if (pending.consumeRerun()) {
                            runQueuedSync(player, pending);
                            return;
                        }
                        pendingSync.remove(playerId, pending);
                        pending.complete(null);
                    });
                } catch (Throwable throwable) {
                    pendingSync.remove(playerId, pending);
                    pending.completeExceptionally(throwable);
                }
            }, () -> {
                pendingSync.remove(playerId, pending);
                pending.completeExceptionally(new IllegalStateException("Player retired before armor sync"));
            });
        } catch (Throwable throwable) {
            pendingSync.remove(playerId, pending);
            pending.completeExceptionally(throwable);
        }
    }

    public CompletableFuture<Void> syncNow(Player player) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (deathInProgress.contains(player.getUniqueId())) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<PlayerArmorSnapshot> captureFuture = new CompletableFuture<>();
        try {
            schedulerAdapter.runPlayer(player, () -> {
                try {
                    captureFuture.complete(captureSnapshot(player, false));
                } catch (Throwable throwable) {
                    captureFuture.completeExceptionally(throwable);
                }
            }, () -> captureFuture.complete(null));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
        return captureFuture.thenCompose(this::persistAfterPendingSync);
    }

    public CompletableFuture<Void> syncNowFromPlayerEvent(Player player) {
        try {
            return persistAfterPendingSync(captureSnapshot(player, false));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    private CompletableFuture<Void> persistAfterPendingSync(PlayerArmorSnapshot snapshot) {
        if (snapshot == null) {
            return CompletableFuture.completedFuture(null);
        }
        PendingPlayerSync pending = pendingSync.get(snapshot.playerId());
        CompletableFuture<Void> previousSync = pending == null
                ? CompletableFuture.completedFuture(null)
                : pending.completion().exceptionally(ignored -> null);
        return previousSync.thenCompose(
                ignored -> wardrobeService.runExclusive(snapshot.playerId(), () -> persistSnapshotUnlocked(snapshot, false)));
    }

    public CompletableFuture<Void> clearSelectedSlot(UUID playerId) {
        return wardrobeService.runExclusive(playerId, () -> clearSelectedSlotUnlocked(playerId, true));
    }

    public CompletableFuture<Void> handleDeath(Player player, boolean keepInventory, List<ItemStack> drops) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (keepInventory) {
            return CompletableFuture.completedFuture(null);
        }
        UUID playerId = player.getUniqueId();
        if (!deathInProgress.add(playerId)) {
            removeBoundDrops(drops);
            return CompletableFuture.completedFuture(null);
        }
        if (deathSettings.keepWardrobeOnDeath()) {
            return preserveWardrobeOnDeath(player, drops);
        }

        DeathLossCapture capture = captureDeathLoss(player, drops);
        bindingService.clearActiveSlot(playerId);

        int affectedSlots = capture.piecesBySlot().size();
        int lostPieces = countLostPieces(capture.piecesBySlot());
        if (lostPieces == 0) {
            auditLogger.record(
                    "DEATH_VANILLA_LOSS",
                    playerId,
                    player.getName(),
                    Map.of("affectedSlots", 0, "lostPieces", 0, "releasedDrops", 0));
            return CompletableFuture.completedFuture(null);
        }

        PendingPlayerSync pending = pendingSync.get(playerId);
        CompletableFuture<Void> previousSync = pending == null
                ? CompletableFuture.completedFuture(null)
                : pending.completion().exceptionally(ignored -> null);

        return previousSync.thenCompose(ignored -> wardrobeService.runExclusive(playerId, () ->
                repository.consumeArmorPiecesOnDeath(playerId, capture.piecesBySlot())
                        .thenCompose(next -> refreshProfile(playerId, true))
                        .thenRun(() -> {
                            releaseDeathDrops(capture);
                            auditLogger.record(
                                    "DEATH_VANILLA_LOSS",
                                    playerId,
                                    player.getName(),
                                    Map.of(
                                            "affectedSlots", affectedSlots,
                                            "lostPieces", lostPieces,
                                            "releasedDrops", capture.dropsToRelease().size()));
                        })));
    }

    public CompletableFuture<Void> flushOnlinePlayers() {
        List<CompletableFuture<Void>> syncTasks = new ArrayList<>();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!hasTrackedArmor(onlinePlayer)) {
                continue;
            }
            syncTasks.add(syncNow(onlinePlayer));
        }
        return CompletableFuture.allOf(syncTasks.toArray(CompletableFuture<?>[]::new));
    }

    public CompletableFuture<Void> flushOnlinePlayersFromCurrentThread() {
        List<CompletableFuture<Void>> syncTasks = new ArrayList<>();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!hasTrackedArmor(onlinePlayer) || deathInProgress.contains(onlinePlayer.getUniqueId())) {
                continue;
            }
            try {
                PlayerArmorSnapshot snapshot = captureSnapshot(onlinePlayer, false);
                syncTasks.add(wardrobeService.runExclusive(
                        onlinePlayer.getUniqueId(),
                        () -> persistSnapshotUnlocked(snapshot, false)));
            } catch (Throwable throwable) {
                syncTasks.add(CompletableFuture.failedFuture(throwable));
            }
        }
        return CompletableFuture.allOf(syncTasks.toArray(CompletableFuture<?>[]::new));
    }

    public void finishDeath(UUID playerId) {
        if (playerId != null) {
            deathInProgress.remove(playerId);
        }
    }

    private void syncOnlinePlayers() {
        schedulerAdapter.runGlobal(() -> {
            List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (onlinePlayers.isEmpty()) {
                scanCursor = 0;
                return;
            }

            int batchSize = Math.min(settings.scanBatchSize(), onlinePlayers.size());
            int start = Math.floorMod(scanCursor, onlinePlayers.size());
            scanCursor = (start + batchSize) % onlinePlayers.size();

            for (int i = 0; i < batchSize; i++) {
                Player onlinePlayer = onlinePlayers.get((start + i) % onlinePlayers.size());
                if (!onlinePlayer.isOnline() || !hasTrackedArmor(onlinePlayer)) {
                    continue;
                }
                requestSync(onlinePlayer);
            }
        });
    }

    private CompletableFuture<Void> preserveWardrobeOnDeath(Player player, List<ItemStack> drops) {
        boolean removedBoundDrop = removeBoundDrops(drops);
        if (!removedBoundDrop && !hasTrackedArmor(player)) {
            return CompletableFuture.completedFuture(null);
        }
        UUID playerId = player.getUniqueId();
        return syncNowFromPlayerEvent(player)
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to save wardrobe armor before death preservation for "
                            + player.getName() + ": " + ex.getMessage());
                    auditLogger.error(
                            "DEATH_PRESERVE_SYNC_ERROR",
                            playerId,
                            player.getName(),
                            ex,
                            Map.of("removedBoundDrops", removedBoundDrop));
                    return null;
                })
                .thenCompose(ignored -> clearSelectedSlot(playerId, true))
                .thenRun(() -> auditLogger.record(
                        "DEATH_PRESERVE",
                        playerId,
                        player.getName(),
                        Map.of("removedBoundDrops", removedBoundDrop)));
    }

    public boolean hasTrackedArmor(Player player) {
        if (player == null) {
            return false;
        }
        return bindingService.getActiveSlot(player.getUniqueId()) > 0 || bindingService.hasAnyBoundArmor(player);
    }

    private CompletableFuture<Void> clearSelectedSlot(UUID playerId, boolean refreshCache) {
        return wardrobeService.runExclusive(playerId, () -> clearSelectedSlotUnlocked(playerId, refreshCache));
    }

    private CompletableFuture<Void> clearSelectedSlotUnlocked(UUID playerId, boolean refreshCache) {
        bindingService.clearActiveSlot(playerId);
        return wardrobeService.getProfile(playerId).thenCompose(profile -> {
            if (profile.selectedSlot() < 1) {
                return CompletableFuture.completedFuture(null);
            }
            return repository.setSelectedSlot(playerId, -1)
                    .thenCompose(ignored -> refreshProfile(playerId, refreshCache));
        });
    }

    private PlayerArmorSnapshot captureSnapshot(Player player, boolean requireOnline) {
        if (player == null) {
            return null;
        }
        if (requireOnline && !player.isOnline()) {
            return null;
        }
        WardrobeArmorBindingService.SanitizationResult sanitization = bindingService.sanitizeBoundItems(player);
        if (sanitization.changed()) {
            auditLogger.record(
                    "BOUND_ARMOR_RECONCILED",
                    player.getUniqueId(),
                    player.getName(),
                    Map.of(
                            "source", "sync-capture",
                            "foreignRemoved", sanitization.foreignRemoved(),
                            "duplicatesRemoved", sanitization.duplicatesRemoved(),
                            "detachedUnbound", sanitization.detachedUnbound()));
        }

        int activeSlot = bindingService.resolveActiveSlot(player);
        UUID playerId = player.getUniqueId();
        return new PlayerArmorSnapshot(
                playerId,
                activeSlot,
                boundPieceCopy(player.getInventory().getHelmet(), playerId, activeSlot, "helmet"),
                boundPieceCopy(player.getInventory().getChestplate(), playerId, activeSlot, "chestplate"),
                boundPieceCopy(player.getInventory().getLeggings(), playerId, activeSlot, "leggings"),
                boundPieceCopy(player.getInventory().getBoots(), playerId, activeSlot, "boots"));
    }

    private CompletableFuture<Void> persistSnapshotUnlocked(PlayerArmorSnapshot snapshot, boolean refreshCache) {
        if (snapshot == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (snapshot.activeSlot() < 1) {
            return clearSelectedSlotUnlocked(snapshot.playerId(), refreshCache);
        }
        return wardrobeService.getProfile(snapshot.playerId())
                .thenCompose(profile -> persistIfChanged(
                        profile,
                        snapshot.activeSlot(),
                        snapshot.helmet(),
                        snapshot.chestplate(),
                        snapshot.leggings(),
                        snapshot.boots(),
                        refreshCache));
    }

    private CompletableFuture<Void> persistIfChanged(
            WardrobeProfile profile,
            int activeSlot,
            ItemStack helmet,
            ItemStack chest,
            ItemStack legs,
            ItemStack boots,
            boolean refreshCache
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
        boolean noArmorRemaining = !updated.hasAnyArmorPiece();
        boolean setChanged = !isEqual(existing, updated);
        boolean selectedSlotNeedsUpdate = noArmorRemaining
                ? WardrobeSafetyDecisions.shouldClearSelectedSlotAfterSync(true, profile.selectedSlot(), activeSlot)
                : profile.selectedSlot() != activeSlot;
        if (!setChanged && !selectedSlotNeedsUpdate) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> writeFuture = setChanged
                ? (noArmorRemaining
                ? repository.deleteSet(profile.playerId(), activeSlot)
                : repository.saveSet(profile.playerId(), updated))
                : CompletableFuture.completedFuture(null);

        int selectedSlot = noArmorRemaining ? -1 : activeSlot;
        return writeFuture
                .thenCompose(ignored -> selectedSlotNeedsUpdate
                        ? repository.setSelectedSlot(profile.playerId(), selectedSlot)
                        : CompletableFuture.completedFuture(null))
                .thenCompose(ignored -> refreshProfile(profile.playerId(), refreshCache))
                .thenRun(() -> {
                    if (auditLogger.logSuccessfulSyncs()) {
                        auditLogger.record(
                                "ARMOR_SYNC",
                                profile.playerId(),
                                null,
                                Map.of(
                                        "slot", activeSlot,
                                        "setChanged", setChanged,
                                        "selectedSlotUpdated", selectedSlotNeedsUpdate,
                                        "pieces", pieceCount(updated)));
                    }
                    if (noArmorRemaining) {
                        bindingService.clearActiveSlot(profile.playerId());
                    }
                });
    }

    private ItemStack boundPieceCopy(ItemStack source, UUID playerId, int activeSlot, String expectedPiece) {
        if (!bindingService.isBoundTo(source, playerId)) {
            return null;
        }
        if (bindingService.getBoundSlot(source) != activeSlot) {
            return null;
        }
        if (!expectedPiece.equals(bindingService.getBoundPiece(source))) {
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

    private boolean removeBoundDrops(List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) {
            return false;
        }
        boolean removed = false;
        ListIterator<ItemStack> iterator = drops.listIterator();
        while (iterator.hasNext()) {
            if (!bindingService.isBound(iterator.next())) {
                continue;
            }
            iterator.remove();
            removed = true;
        }
        return removed;
    }

    private DeathLossCapture captureDeathLoss(Player player, List<ItemStack> drops) {
        Map<Integer, Set<String>> lostPiecesBySlot = new HashMap<>();
        Map<DeathPieceKey, ItemStack> canonicalDrops = new LinkedHashMap<>();
        UUID playerId = player.getUniqueId();
        PlayerInventory inventory = player.getInventory();

        collectLostPiece(lostPiecesBySlot, canonicalDrops, playerId, inventory.getHelmet(), "helmet");
        collectLostPiece(lostPiecesBySlot, canonicalDrops, playerId, inventory.getChestplate(), "chestplate");
        collectLostPiece(lostPiecesBySlot, canonicalDrops, playerId, inventory.getLeggings(), "leggings");
        collectLostPiece(lostPiecesBySlot, canonicalDrops, playerId, inventory.getBoots(), "boots");

        Set<DeathPieceKey> requestedDrops = new HashSet<>();
        if (drops != null) {
            ListIterator<ItemStack> iterator = drops.listIterator();
            while (iterator.hasNext()) {
                ItemStack drop = iterator.next();
                if (!bindingService.isBound(drop)) {
                    continue;
                }
                if (bindingService.isBoundTo(drop, playerId)) {
                    int slot = bindingService.getBoundSlot(drop);
                    String piece = bindingService.getBoundPiece(drop);
                    if (slot > 0 && isStoredArmorPiece(piece)) {
                        requestedDrops.add(new DeathPieceKey(slot, piece));
                    }
                }
                iterator.remove();
            }
        }

        List<ItemStack> dropsToRelease = canonicalDrops.entrySet().stream()
                .filter(entry -> requestedDrops.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .map(ItemStack::clone)
                .toList();
        return new DeathLossCapture(
                Map.copyOf(lostPiecesBySlot),
                dropsToRelease,
                player.getLocation().clone());
    }

    private void collectLostPiece(
            Map<Integer, Set<String>> lostPiecesBySlot,
            Map<DeathPieceKey, ItemStack> canonicalDrops,
            UUID playerId,
            ItemStack itemStack,
            String expectedPiece
    ) {
        if (!bindingService.isBoundTo(itemStack, playerId)) {
            return;
        }

        int slot = bindingService.getBoundSlot(itemStack);
        if (slot < 1) {
            return;
        }

        String piece = bindingService.getBoundPiece(itemStack);
        if (piece == null || piece.isBlank()) {
            piece = expectedPiece;
        }
        if (!expectedPiece.equals(piece)) {
            return;
        }

        lostPiecesBySlot.computeIfAbsent(slot, ignored -> new HashSet<>()).add(piece);
        canonicalDrops.put(new DeathPieceKey(slot, piece), bindingService.unbindCopy(itemStack));
    }

    private boolean isStoredArmorPiece(String piece) {
        return "helmet".equals(piece)
                || "chestplate".equals(piece)
                || "leggings".equals(piece)
                || "boots".equals(piece);
    }

    private void releaseDeathDrops(DeathLossCapture capture) {
        if (capture.dropsToRelease().isEmpty()) {
            return;
        }
        try {
            schedulerAdapter.runLocation(capture.location(), () -> {
                for (ItemStack itemStack : capture.dropsToRelease()) {
                    capture.location().getWorld().dropItemNaturally(capture.location(), itemStack.clone());
                }
            });
        } catch (Throwable throwable) {
            auditLogger.error(
                    "DEATH_DROP_RELEASE_ERROR",
                    null,
                    null,
                    throwable,
                    Map.of("drops", capture.dropsToRelease().size()));
        }
    }

    private int countLostPieces(Map<Integer, Set<String>> lostPiecesBySlot) {
        int total = 0;
        for (Set<String> pieces : lostPiecesBySlot.values()) {
            total += pieces.size();
        }
        return total;
    }

    private int pieceCount(WardrobeSet set) {
        int count = 0;
        count += hasPiece(set.helmet()) ? 1 : 0;
        count += hasPiece(set.chestplate()) ? 1 : 0;
        count += hasPiece(set.leggings()) ? 1 : 0;
        count += hasPiece(set.boots()) ? 1 : 0;
        return count;
    }

    private boolean hasPiece(ItemStack itemStack) {
        return itemStack != null && !itemStack.getType().isAir();
    }

    private CompletableFuture<Void> refreshProfile(UUID playerId, boolean refreshCache) {
        if (!refreshCache) {
            wardrobeService.invalidateCache(playerId);
            return CompletableFuture.completedFuture(null);
        }
        return repository.loadProfile(playerId)
                .thenAccept(wardrobeService::primeProfile)
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to refresh wardrobe cache for " + playerId + ": " + ex.getMessage());
                    wardrobeService.invalidateCache(playerId);
                    return null;
                });
    }

    private record PlayerArmorSnapshot(
            UUID playerId,
            int activeSlot,
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots
    ) {
    }

    private record DeathPieceKey(int slot, String piece) {
    }

    private record DeathLossCapture(
            Map<Integer, Set<String>> piecesBySlot,
            List<ItemStack> dropsToRelease,
            Location location
    ) {
    }

    private static final class PendingPlayerSync {

        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private boolean rerunRequested;
        private boolean finishing;

        synchronized boolean requestAgain() {
            if (finishing) {
                return false;
            }
            rerunRequested = true;
            return true;
        }

        synchronized boolean consumeRerun() {
            if (rerunRequested) {
                rerunRequested = false;
                return true;
            }
            finishing = true;
            return false;
        }

        CompletableFuture<Void> completion() {
            return completion;
        }

        void complete(Void value) {
            completion.complete(value);
        }

        void completeExceptionally(Throwable throwable) {
            completion.completeExceptionally(throwable);
        }
    }
}
