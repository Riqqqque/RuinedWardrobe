package dev.rique.ruinedwardrobe.core;

import dev.rique.ruinedwardrobe.api.WardrobeService;
import dev.rique.ruinedwardrobe.api.event.WardrobeDeletedEvent;
import dev.rique.ruinedwardrobe.api.event.WardrobeEquippedEvent;
import dev.rique.ruinedwardrobe.api.event.WardrobePreEquipEvent;
import dev.rique.ruinedwardrobe.api.event.WardrobePreSaveEvent;
import dev.rique.ruinedwardrobe.api.event.WardrobeSavedEvent;
import dev.rique.ruinedwardrobe.api.model.RestrictionDecision;
import dev.rique.ruinedwardrobe.api.model.ResultCode;
import dev.rique.ruinedwardrobe.api.model.WardrobeProfile;
import dev.rique.ruinedwardrobe.api.model.WardrobeResult;
import dev.rique.ruinedwardrobe.api.model.WardrobeSet;
import dev.rique.ruinedwardrobe.cache.WardrobeCache;
import dev.rique.ruinedwardrobe.config.PluginConfig;
import dev.rique.ruinedwardrobe.scheduler.SchedulerAdapter;
import dev.rique.ruinedwardrobe.storage.WardrobeRepository;
import dev.rique.ruinedwardrobe.util.ArmorPieceMatcher;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class WardrobeServiceImpl implements WardrobeService {

    private final SchedulerAdapter schedulerAdapter;
    private final WardrobeRepository repository;
    private final WardrobeCache cache;
    private final SlotLimitServiceImpl slotLimitService;
    private final RestrictionServiceImpl restrictionService;
    private final WardrobeArmorBindingService bindingService;
    private final PluginConfig pluginConfig;
    private final WardrobeAuditLogger auditLogger;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public WardrobeServiceImpl(
            SchedulerAdapter schedulerAdapter,
            WardrobeRepository repository,
            WardrobeCache cache,
            SlotLimitServiceImpl slotLimitService,
            RestrictionServiceImpl restrictionService,
            WardrobeArmorBindingService bindingService,
            PluginConfig pluginConfig,
            WardrobeAuditLogger auditLogger
    ) {
        this.schedulerAdapter = schedulerAdapter;
        this.repository = repository;
        this.cache = cache;
        this.slotLimitService = slotLimitService;
        this.restrictionService = restrictionService;
        this.bindingService = bindingService;
        this.pluginConfig = pluginConfig;
        this.auditLogger = auditLogger;
    }

    @Override
    public CompletableFuture<WardrobeProfile> getProfile(UUID playerId) {
        return cache.get(playerId)
                .map(CompletableFuture::completedFuture)
                .orElseGet(() -> repository.loadProfile(playerId).thenApply(profile -> {
                    cache.put(profile);
                    slotLimitService.primeBonus(playerId, profile.bonusSlots());
                    return profile;
                }));
    }

    @Override
    public CompletableFuture<WardrobeResult> saveSet(UUID playerId, int slot) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.ERROR_PLAYER_OFFLINE, "error.player-offline", Map.of()));
        }
        if (!PermissionNodes.has(player, "use")) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_NO_PERMISSION, "error.no-permission", Map.of()));
        }
        int maxSlots = slotLimitService.getMaxSlots(player);
        if (slot < 1 || slot > maxSlots) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_SLOT_LOCKED, "error.slot-locked", Map.of("slot", String.valueOf(slot), "max", String.valueOf(maxSlots))));
        }

        return getProfile(playerId).thenCompose(profile -> onPlayer(player, () -> {
            WardrobePreSaveEvent event = new WardrobePreSaveEvent(player, slot);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return SaveCapture.cancelledCapture();
            }
            PlayerInventory inventory = player.getInventory();
            WardrobeSet current = profile.getSet(slot);
            String name = current == null || current.name().isBlank() ? "Slot " + slot : current.name();
            boolean favorite = current != null && current.favorite();
            WardrobeSet newSet = new WardrobeSet(
                    slot,
                    name,
                    favorite,
                    bindingService.unbindCopy(inventory.getHelmet()),
                    bindingService.unbindCopy(inventory.getChestplate()),
                    bindingService.unbindCopy(inventory.getLeggings()),
                    bindingService.unbindCopy(inventory.getBoots())
            );
            return SaveCapture.of(newSet);
        })).thenCompose(capture -> {
            if (capture.cancelled()) {
                return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_EVENT_CANCELLED, "error.event-cancelled", Map.of()));
            }
            if (capture.wardrobeSet() == null || !capture.wardrobeSet().hasAnyArmorPiece()) {
                return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_NOTHING_SAVED, "error.no-armor-to-save", Map.of()));
            }
            return repository.saveSet(playerId, capture.wardrobeSet())
                    .thenCompose(ignored -> refreshProfileCache(playerId))
                    .thenApply(updated -> {
                        auditLogger.record(
                                "SAVE_SET",
                                playerId,
                                player.getName(),
                                setAuditDetails(slot, capture.wardrobeSet()));
                        onPlayer(player, () -> {
                            Bukkit.getPluginManager().callEvent(new WardrobeSavedEvent(player, capture.wardrobeSet()));
                            return null;
                        });
                        return new WardrobeResult(ResultCode.SUCCESS_SAVED, "success.saved", Map.of("slot", String.valueOf(slot), "name", capture.wardrobeSet().name()));
                    })
                    .exceptionally(ex -> {
                        auditLogger.error("SAVE_SET_ERROR", playerId, player.getName(), ex, Map.of("slot", slot));
                        return storageErrorResult(ex);
                    });
        });
    }

    @Override
    public CompletableFuture<WardrobeResult> equipSet(UUID playerId, int slot) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.ERROR_PLAYER_OFFLINE, "error.player-offline", Map.of()));
        }
        if (!PermissionNodes.has(player, "use")) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_NO_PERMISSION, "error.no-permission", Map.of()));
        }

        int maxSlots = slotLimitService.getMaxSlots(player);
        if (slot < 1 || slot > maxSlots) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_SLOT_LOCKED, "error.slot-locked", Map.of("slot", String.valueOf(slot), "max", String.valueOf(maxSlots))));
        }

        return getProfile(playerId).thenCompose(profile -> onPlayer(player, () -> {
            WardrobeSet set = profile.getSet(slot);
            boolean toggleOff = profile.selectedSlot() == slot && hasAnyBoundArmorForSlot(player, playerId, slot);

            if (!toggleOff && (set == null || !set.hasAnyArmorPiece())) {
                return EquipAttempt.denied(new WardrobeResult(ResultCode.DENIED_NOTHING_SAVED, "error.nothing-saved", Map.of("slot", String.valueOf(slot))));
            }
            if (toggleOff) {
                ArmorRuntimeState previousState = captureArmorRuntimeState(player);
                clearOwnedBoundArmor(player.getInventory(), playerId);
                bindingService.clearActiveSlot(playerId);
                String name = set == null || set.name().isBlank() ? "Slot " + slot : set.name();
                return EquipAttempt.mutated(
                        new WardrobeResult(ResultCode.SUCCESS, "success.unequipped",
                                Map.of("slot", String.valueOf(slot), "name", name)),
                        previousState,
                        null,
                        -1);
            }

            if (!PermissionNodes.has(player, "bypass.cooldown")) {
                long remaining = getRemainingCooldownMillis(playerId);
                if (remaining > 0) {
                    long seconds = Math.max(1L, (remaining + 999) / 1000);
                    return EquipAttempt.denied(new WardrobeResult(ResultCode.DENIED_COOLDOWN, "error.cooldown", Map.of("seconds", String.valueOf(seconds))));
                }
            }

            if (!PermissionNodes.has(player, "bypass.restrictions")) {
                RestrictionDecision decision = restrictionService.canEquip(player);
                if (!decision.allowed()) {
                    return EquipAttempt.denied(new WardrobeResult(decision.code(), decision.messageKey(), decision.placeholders()));
                }
            }

            if (!PermissionNodes.has(player, "bypass.emptycheck")) {
                Map<String, String> blockedSlot = firstNonEmptyAffectedSlot(player, set);
                if (!blockedSlot.isEmpty()) {
                    return EquipAttempt.denied(new WardrobeResult(ResultCode.DENIED_EMPTY_ARMOR_REQUIRED, "error.armor-slot-not-empty", blockedSlot));
                }
            }

            WardrobePreEquipEvent preEquipEvent = new WardrobePreEquipEvent(player, set);
            Bukkit.getPluginManager().callEvent(preEquipEvent);
            if (preEquipEvent.isCancelled()) {
                return EquipAttempt.denied(new WardrobeResult(ResultCode.DENIED_EVENT_CANCELLED, "error.event-cancelled", Map.of()));
            }

            ArmorRuntimeState previousState = captureArmorRuntimeState(player);
            PlayerInventory inventory = player.getInventory();
            clearOwnedBoundArmor(inventory, playerId);
            if (set.helmet() != null && !set.helmet().getType().isAir()) {
                inventory.setHelmet(bindingService.bindCopy(set.helmet(), playerId, slot, "helmet"));
            }
            if (set.chestplate() != null && !set.chestplate().getType().isAir()) {
                inventory.setChestplate(bindingService.bindCopy(set.chestplate(), playerId, slot, "chestplate"));
            }
            if (set.leggings() != null && !set.leggings().getType().isAir()) {
                inventory.setLeggings(bindingService.bindCopy(set.leggings(), playerId, slot, "leggings"));
            }
            if (set.boots() != null && !set.boots().getType().isAir()) {
                inventory.setBoots(bindingService.bindCopy(set.boots(), playerId, slot, "boots"));
            }
            bindingService.setActiveSlot(playerId, slot);
            return EquipAttempt.mutated(
                    new WardrobeResult(ResultCode.SUCCESS_EQUIPPED, "success.equipped", Map.of("slot", String.valueOf(slot), "name", set.name())),
                    previousState,
                    set,
                    slot);
        }).thenCompose(attempt -> {
            if (!attempt.result().isSuccess()) {
                auditBlocked("EQUIP_DENIED", player, playerId, attempt.result(), Map.of("slot", slot));
                return CompletableFuture.completedFuture(attempt.result());
            }
            return repository.setSelectedSlot(playerId, attempt.selectedSlot())
                    .thenCompose(ignored -> refreshProfileCache(playerId))
                    .thenCompose(ignored -> finalizeEquip(player, playerId, attempt))
                    .thenApply(ignored -> {
                        String action = attempt.selectedSlot() < 1 ? "UNEQUIP" : "EQUIP";
                        auditLogger.record(
                                action,
                                playerId,
                                player.getName(),
                                equipAuditDetails(slot, attempt));
                        return attempt.result();
                    })
                    .exceptionallyCompose(ex -> {
                        auditLogger.error("EQUIP_ERROR", playerId, player.getName(), ex, Map.of("slot", slot));
                        return restorePlayerState(player, attempt.previousState())
                                .thenApply(ignored -> storageErrorResult(ex));
                    });
        }));
    }

    @Override
    public CompletableFuture<WardrobeResult> deleteSet(UUID playerId, int slot) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.ERROR_PLAYER_OFFLINE, "error.player-offline", Map.of()));
        }
        if (!PermissionNodes.has(player, "use")) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_NO_PERMISSION, "error.no-permission", Map.of()));
        }
        int maxSlots = slotLimitService.getMaxSlots(player);
        if (slot < 1 || slot > maxSlots) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_INVALID_SLOT, "error.invalid-slot", Map.of("slot", String.valueOf(slot))));
        }
        return getProfile(playerId).thenCompose(profile -> {
            if (profile.getSet(slot) == null) {
                WardrobeResult result = new WardrobeResult(ResultCode.DENIED_NOTHING_SAVED, "error.nothing-saved", Map.of("slot", String.valueOf(slot)));
                auditBlocked("DELETE_DENIED", player, playerId, result, Map.of("slot", slot));
                return CompletableFuture.completedFuture(result);
            }
            return onPlayer(player, () -> bindingService.getActiveSlot(playerId) == slot && bindingService.hasAnyBoundArmor(player))
                    .thenCompose(equipped -> {
                        if (equipped) {
                            WardrobeResult result = new WardrobeResult(ResultCode.DENIED_EVENT_CANCELLED, "error.cannot-delete-equipped", Map.of("slot", String.valueOf(slot)));
                            auditBlocked("DELETE_DENIED", player, playerId, result, Map.of("slot", slot, "equipped", true));
                            return CompletableFuture.completedFuture(result);
                        }
                        return repository.deleteSet(playerId, slot)
                                .thenCompose(ignored -> refreshProfileCache(playerId))
                                .thenCompose(updated -> onPlayer(player, () -> {
                                    auditLogger.record("DELETE_SET", playerId, player.getName(), Map.of("slot", slot));
                                    Bukkit.getPluginManager().callEvent(new WardrobeDeletedEvent(player, slot));
                                    return new WardrobeResult(ResultCode.SUCCESS_DELETED, "success.deleted", Map.of("slot", String.valueOf(slot)));
                                }))
                                .exceptionally(ex -> {
                                    auditLogger.error("DELETE_SET_ERROR", playerId, player.getName(), ex, Map.of("slot", slot));
                                    return storageErrorResult(ex);
                                });
                    });
        });
    }

    @Override
    public CompletableFuture<WardrobeResult> renameSet(UUID playerId, int slot, String newName) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.ERROR_PLAYER_OFFLINE, "error.player-offline", Map.of()));
        }
        if (!PermissionNodes.has(player, "use")) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_NO_PERMISSION, "error.no-permission", Map.of()));
        }
        int maxSlots = slotLimitService.getMaxSlots(player);
        if (slot < 1 || slot > maxSlots) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_INVALID_SLOT, "error.invalid-slot", Map.of("slot", String.valueOf(slot))));
        }
        if (newName == null || newName.isBlank()) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_INVALID_SLOT, "error.invalid-name", Map.of()));
        }
        String trimmedName = newName.trim();
        if (trimmedName.length() > 48) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_INVALID_SLOT, "error.name-too-long", Map.of("max", "48")));
        }
        return getProfile(playerId).thenCompose(profile -> {
            if (profile.getSet(slot) == null) {
                return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_NOTHING_SAVED, "error.nothing-saved", Map.of("slot", String.valueOf(slot))));
            }
            return repository.renameSet(playerId, slot, trimmedName)
                    .thenCompose(ignored -> refreshProfileCache(playerId))
                    .thenApply(updated -> {
                        auditLogger.record("RENAME_SET", playerId, player.getName(), Map.of("slot", slot, "name", trimmedName));
                        return new WardrobeResult(ResultCode.SUCCESS_RENAMED, "success.renamed", Map.of("slot", String.valueOf(slot), "name", trimmedName));
                    })
                    .exceptionally(ex -> {
                        auditLogger.error("RENAME_SET_ERROR", playerId, player.getName(), ex, Map.of("slot", slot, "name", trimmedName));
                        return storageErrorResult(ex);
                    });
                });
    }

    public CompletableFuture<WardrobeResult> setArmorPiece(UUID playerId, int slot, String piece, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_INVALID_SLOT, "error.wrong-armor-type", Map.of()));
        }
        if (!isValidPieceType(piece) || !isMatchingArmorPiece(itemStack.getType(), piece)) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_INVALID_SLOT, "error.wrong-armor-type", Map.of()));
        }
        return mutateSetPiece(playerId, slot, piece, bindingService.unbindCopy(itemStack), "success.armor-placed", false);
    }

    public CompletableFuture<WardrobeResult> clearArmorPiece(UUID playerId, int slot, String piece) {
        if (!isValidPieceType(piece)) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_INVALID_SLOT, "error.invalid-slot", Map.of("slot", String.valueOf(slot))));
        }
        return mutateSetPiece(playerId, slot, piece, null, "success.armor-taken", true);
    }

    public void invalidateCache(UUID playerId) {
        cache.invalidate(playerId);
        slotLimitService.invalidate(playerId);
    }

    public Optional<WardrobeProfile> cachedProfile(UUID playerId) {
        return cache.get(playerId);
    }

    public void primeProfile(WardrobeProfile profile) {
        cache.put(profile);
        slotLimitService.primeBonus(profile.playerId(), profile.bonusSlots());
    }

    public long getRemainingCooldownMillis(UUID playerId) {
        Long expiresAt = cooldowns.get(playerId);
        if (expiresAt == null) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        if (expiresAt <= now) {
            cooldowns.remove(playerId, expiresAt);
            return 0L;
        }
        return expiresAt - now;
    }

    private Map<String, String> firstNonEmptyAffectedSlot(Player player, WardrobeSet set) {
        PlayerInventory inventory = player.getInventory();
        if (set.helmet() != null
                && !set.helmet().getType().isAir()
                && inventory.getHelmet() != null
                && !inventory.getHelmet().getType().isAir()
                && !bindingService.isBoundTo(inventory.getHelmet(), player.getUniqueId())) {
            return Map.of("slot", "helmet");
        }
        if (set.chestplate() != null
                && !set.chestplate().getType().isAir()
                && inventory.getChestplate() != null
                && !inventory.getChestplate().getType().isAir()
                && !bindingService.isBoundTo(inventory.getChestplate(), player.getUniqueId())) {
            return Map.of("slot", "chestplate");
        }
        if (set.leggings() != null
                && !set.leggings().getType().isAir()
                && inventory.getLeggings() != null
                && !inventory.getLeggings().getType().isAir()
                && !bindingService.isBoundTo(inventory.getLeggings(), player.getUniqueId())) {
            return Map.of("slot", "leggings");
        }
        if (set.boots() != null
                && !set.boots().getType().isAir()
                && inventory.getBoots() != null
                && !inventory.getBoots().getType().isAir()
                && !bindingService.isBoundTo(inventory.getBoots(), player.getUniqueId())) {
            return Map.of("slot", "boots");
        }
        return Map.of();
    }

    private boolean hasAnyBoundArmorForSlot(Player player, UUID playerId, int slot) {
        PlayerInventory inventory = player.getInventory();
        return isBoundPieceForSlot(inventory.getHelmet(), playerId, slot)
                || isBoundPieceForSlot(inventory.getChestplate(), playerId, slot)
                || isBoundPieceForSlot(inventory.getLeggings(), playerId, slot)
                || isBoundPieceForSlot(inventory.getBoots(), playerId, slot);
    }

    private void clearBoundArmorForSlot(PlayerInventory inventory, UUID playerId, int slot) {
        if (isBoundPieceForSlot(inventory.getHelmet(), playerId, slot)) {
            inventory.setHelmet(null);
        }
        if (isBoundPieceForSlot(inventory.getChestplate(), playerId, slot)) {
            inventory.setChestplate(null);
        }
        if (isBoundPieceForSlot(inventory.getLeggings(), playerId, slot)) {
            inventory.setLeggings(null);
        }
        if (isBoundPieceForSlot(inventory.getBoots(), playerId, slot)) {
            inventory.setBoots(null);
        }
    }

    private void clearOwnedBoundArmor(PlayerInventory inventory, UUID playerId) {
        if (bindingService.isBoundTo(inventory.getHelmet(), playerId)) {
            inventory.setHelmet(null);
        }
        if (bindingService.isBoundTo(inventory.getChestplate(), playerId)) {
            inventory.setChestplate(null);
        }
        if (bindingService.isBoundTo(inventory.getLeggings(), playerId)) {
            inventory.setLeggings(null);
        }
        if (bindingService.isBoundTo(inventory.getBoots(), playerId)) {
            inventory.setBoots(null);
        }
    }

    private boolean isBoundPieceForSlot(ItemStack itemStack, UUID playerId, int slot) {
        return bindingService.isBoundTo(itemStack, playerId) && bindingService.getBoundSlot(itemStack) == slot;
    }

    private <T> CompletableFuture<T> onPlayer(Player player, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            schedulerAdapter.runPlayer(player, () -> {
                try {
                    future.complete(supplier.get());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private CompletableFuture<WardrobeResult> mutateSetPiece(
            UUID playerId,
            int slot,
            String piece,
            ItemStack replacement,
            String successKey,
            boolean autoUnequipIfEquipped
    ) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.ERROR_PLAYER_OFFLINE, "error.player-offline", Map.of()));
        }
        if (!PermissionNodes.has(player, "use")) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_NO_PERMISSION, "error.no-permission", Map.of()));
        }
        int maxSlots = slotLimitService.getMaxSlots(player);
        if (slot < 1 || slot > maxSlots) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_SLOT_LOCKED, "error.slot-locked", Map.of("slot", String.valueOf(slot), "max", String.valueOf(maxSlots))));
        }
        return onPlayer(player, () -> hasAnyBoundArmorForSlot(player, playerId, slot)).thenCompose(boundSlotEquipped -> {
            if (WardrobeSafetyDecisions.shouldBlockPieceMutation(boundSlotEquipped, autoUnequipIfEquipped)) {
                WardrobeResult result = new WardrobeResult(
                        ResultCode.DENIED_EVENT_CANCELLED,
                        "error.cannot-edit-equipped-piece",
                        Map.of("slot", String.valueOf(slot), "piece", piece));
                auditBlocked("ARMOR_EDIT_DENIED", player, playerId, result, Map.of("slot", slot, "piece", piece));
                return CompletableFuture.completedFuture(result);
            }

            return getProfile(playerId).thenCompose(profile -> {
                WardrobeSet existing = profile.getSet(slot);
                CompletableFuture<PieceMutationContext> contextFuture;
                if (WardrobeSafetyDecisions.shouldAutoUnequipBeforeMutation(boundSlotEquipped, autoUnequipIfEquipped)) {
                    contextFuture = onPlayer(player, () -> {
                        ArmorRuntimeState previousState = captureArmorRuntimeState(player);
                        WardrobeSet liveSet = snapshotLiveSet(player, playerId, slot, existing);
                        clearBoundArmorForSlot(player.getInventory(), playerId, slot);
                        if (bindingService.getActiveSlot(playerId) == slot) {
                            bindingService.clearActiveSlot(playerId);
                        }
                        return new PieceMutationContext(previousState, liveSet, true);
                    });
                } else {
                    contextFuture = CompletableFuture.completedFuture(new PieceMutationContext(null, createBaseSet(slot, existing), false));
                }

                return contextFuture.thenCompose(context -> {
                    WardrobeSet baseSet = context.baseSet();
                    if (!baseSet.hasAnyArmorPiece() && replacement == null) {
                        return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_NOTHING_SAVED, "error.nothing-saved", Map.of("slot", String.valueOf(slot))));
                    }

                    WardrobeSet updated = applyPieceMutation(baseSet, piece, replacement);
                    CompletableFuture<Void> writeFuture = updated.hasAnyArmorPiece()
                            ? repository.saveSet(playerId, updated)
                            : repository.deleteSet(playerId, slot);

                    return writeFuture
                            .thenCompose(ignored -> WardrobeSafetyDecisions.shouldClearSelectedSlotAfterAutoUnequip(context.autoUnequipped())
                                    ? repository.setSelectedSlot(playerId, -1)
                                    : CompletableFuture.completedFuture(null))
                            .thenCompose(ignored -> refreshProfileCache(playerId))
                            .thenApply(ignored -> {
                                String action = replacement == null ? "ARMOR_TAKE" : "ARMOR_PLACE";
                                auditLogger.record(
                                        action,
                                        playerId,
                                        player.getName(),
                                        pieceMutationAuditDetails(slot, piece, replacement, context.autoUnequipped()));
                                return new WardrobeResult(ResultCode.SUCCESS, successKey, Map.of("slot", String.valueOf(slot), "piece", piece));
                            })
                            .exceptionallyCompose(ex -> {
                                auditLogger.error(
                                        replacement == null ? "ARMOR_TAKE_ERROR" : "ARMOR_PLACE_ERROR",
                                        playerId,
                                        player.getName(),
                                        ex,
                                        Map.of("slot", slot, "piece", piece));
                                return restorePlayerState(player, context.previousState())
                                        .thenApply(ignored -> storageErrorResult(ex));
                            });
                });
            });
        });
    }

    private CompletableFuture<Void> finalizeEquip(Player player, UUID playerId, EquipAttempt attempt) {
        if (attempt.equippedSet() == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.<Void>onPlayer(player, () -> {
            if (!PermissionNodes.has(player, "bypass.cooldown")) {
                cooldowns.put(playerId, System.currentTimeMillis() + pluginConfig.equipCooldownMillis());
            }
            Bukkit.getPluginManager().callEvent(new WardrobeEquippedEvent(player, attempt.equippedSet()));
            return null;
        }).exceptionally(ex -> (Void) null);
    }

    private CompletableFuture<Void> refreshProfileCache(UUID playerId) {
        return repository.loadProfile(playerId)
                .thenAccept(updated -> {
                    cache.put(updated);
                    slotLimitService.primeBonus(playerId, updated.bonusSlots());
                })
                .exceptionally(ex -> {
                    invalidateCache(playerId);
                    return null;
                });
    }

    private CompletableFuture<Void> restorePlayerState(Player player, ArmorRuntimeState previousState) {
        if (previousState == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.<Void>onPlayer(player, () -> {
            PlayerInventory inventory = player.getInventory();
            inventory.setHelmet(cloneItem(previousState.helmet()));
            inventory.setChestplate(cloneItem(previousState.chestplate()));
            inventory.setLeggings(cloneItem(previousState.leggings()));
            inventory.setBoots(cloneItem(previousState.boots()));
            if (previousState.activeSlot() > 0) {
                bindingService.setActiveSlot(player.getUniqueId(), previousState.activeSlot());
            } else {
                bindingService.clearActiveSlot(player.getUniqueId());
            }
            return null;
        }).exceptionally(ex -> (Void) null);
    }

    private ArmorRuntimeState captureArmorRuntimeState(Player player) {
        PlayerInventory inventory = player.getInventory();
        return new ArmorRuntimeState(
                cloneItem(inventory.getHelmet()),
                cloneItem(inventory.getChestplate()),
                cloneItem(inventory.getLeggings()),
                cloneItem(inventory.getBoots()),
                bindingService.resolveActiveSlot(player));
    }

    private WardrobeSet createBaseSet(int slot, WardrobeSet existing) {
        String name = existing == null || existing.name().isBlank() ? "Slot " + slot : existing.name();
        boolean favorite = existing != null && existing.favorite();
        return new WardrobeSet(
                slot,
                name,
                favorite,
                existing == null ? null : existing.helmet(),
                existing == null ? null : existing.chestplate(),
                existing == null ? null : existing.leggings(),
                existing == null ? null : existing.boots());
    }

    private WardrobeSet snapshotLiveSet(Player player, UUID playerId, int slot, WardrobeSet existing) {
        PlayerInventory inventory = player.getInventory();
        return new WardrobeSet(
                slot,
                existing == null || existing.name().isBlank() ? "Slot " + slot : existing.name(),
                existing != null && existing.favorite(),
                livePieceOrStored(inventory.getHelmet(), playerId, slot, existing == null ? null : existing.helmet()),
                livePieceOrStored(inventory.getChestplate(), playerId, slot, existing == null ? null : existing.chestplate()),
                livePieceOrStored(inventory.getLeggings(), playerId, slot, existing == null ? null : existing.leggings()),
                livePieceOrStored(inventory.getBoots(), playerId, slot, existing == null ? null : existing.boots()));
    }

    private ItemStack livePieceOrStored(ItemStack equippedPiece, UUID playerId, int slot, ItemStack storedPiece) {
        if (bindingService.isBoundTo(equippedPiece, playerId) && bindingService.getBoundSlot(equippedPiece) == slot) {
            return bindingService.unbindCopy(equippedPiece);
        }
        return storedPiece;
    }

    private WardrobeSet applyPieceMutation(WardrobeSet baseSet, String piece, ItemStack replacement) {
        ItemStack helmet = baseSet.helmet();
        ItemStack chestplate = baseSet.chestplate();
        ItemStack leggings = baseSet.leggings();
        ItemStack boots = baseSet.boots();

        switch (piece) {
            case "helmet" -> helmet = replacement == null ? null : replacement.clone();
            case "chestplate" -> chestplate = replacement == null ? null : replacement.clone();
            case "leggings" -> leggings = replacement == null ? null : replacement.clone();
            case "boots" -> boots = replacement == null ? null : replacement.clone();
            default -> throw new IllegalArgumentException("Unsupported armor piece: " + piece);
        }

        return new WardrobeSet(baseSet.slot(), baseSet.name(), baseSet.favorite(), helmet, chestplate, leggings, boots);
    }

    private ItemStack cloneItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        return itemStack.clone();
    }

    private void auditBlocked(String action, Player player, UUID playerId, WardrobeResult result, Map<String, ?> details) {
        if (!auditLogger.logBlockedActions()) {
            return;
        }
        Map<String, Object> audit = new HashMap<>();
        if (details != null) {
            audit.putAll(details);
        }
        audit.put("code", result.code());
        audit.put("message", result.messageKey());
        auditLogger.record(action, playerId, player.getName(), audit);
    }

    private Map<String, Object> equipAuditDetails(int requestedSlot, EquipAttempt attempt) {
        Map<String, Object> details = new HashMap<>();
        details.put("requestedSlot", requestedSlot);
        details.put("selectedSlot", attempt.selectedSlot());
        details.put("code", attempt.result().code());
        if (attempt.equippedSet() != null) {
            details.putAll(setAuditDetails(attempt.equippedSet().slot(), attempt.equippedSet()));
        }
        return details;
    }

    private Map<String, Object> pieceMutationAuditDetails(int slot, String piece, ItemStack replacement, boolean autoUnequipped) {
        Map<String, Object> details = new HashMap<>();
        details.put("slot", slot);
        details.put("piece", piece);
        details.put("autoUnequipped", autoUnequipped);
        if (auditLogger.includeItemSummaries()) {
            details.put("item", itemSummary(replacement));
        }
        return details;
    }

    private Map<String, Object> setAuditDetails(int slot, WardrobeSet set) {
        Map<String, Object> details = new HashMap<>();
        details.put("slot", slot);
        details.put("pieces", set == null ? 0 : pieceCount(set));
        if (set != null && auditLogger.includeItemSummaries()) {
            details.put("helmet", itemSummary(set.helmet()));
            details.put("chestplate", itemSummary(set.chestplate()));
            details.put("leggings", itemSummary(set.leggings()));
            details.put("boots", itemSummary(set.boots()));
        }
        return details;
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

    private String itemSummary(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "empty";
        }
        return itemStack.getType().name() + "x" + itemStack.getAmount();
    }

    private WardrobeResult storageErrorResult(Throwable throwable) {
        return new WardrobeResult(ResultCode.ERROR_STORAGE, "error.storage", Map.of("reason", resolveFailureMessage(throwable)));
    }

    private String resolveFailureMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        if (current == null) {
            return "unknown";
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private boolean isValidPieceType(String piece) {
        return "helmet".equals(piece) || "chestplate".equals(piece) || "leggings".equals(piece) || "boots".equals(piece);
    }

    private boolean isMatchingArmorPiece(Material material, String targetPiece) {
        return ArmorPieceMatcher.matchesWardrobePiece(targetPiece, material);
    }

    private record SaveCapture(boolean cancelled, WardrobeSet wardrobeSet) {

        static SaveCapture cancelledCapture() {
            return new SaveCapture(true, null);
        }

        static SaveCapture of(WardrobeSet set) {
            return new SaveCapture(false, set);
        }
    }

    private record EquipAttempt(
            WardrobeResult result,
            ArmorRuntimeState previousState,
            WardrobeSet equippedSet,
            int selectedSlot
    ) {
        static EquipAttempt denied(WardrobeResult result) {
            return new EquipAttempt(result, null, null, -1);
        }

        static EquipAttempt mutated(WardrobeResult result, ArmorRuntimeState previousState, WardrobeSet equippedSet, int selectedSlot) {
            return new EquipAttempt(result, previousState, equippedSet, selectedSlot);
        }
    }

    private record PieceMutationContext(
            ArmorRuntimeState previousState,
            WardrobeSet baseSet,
            boolean autoUnequipped
    ) {
    }

    private record ArmorRuntimeState(
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots,
            int activeSlot
    ) {
    }
}
