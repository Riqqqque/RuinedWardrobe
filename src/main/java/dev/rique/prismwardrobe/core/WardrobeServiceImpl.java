package dev.rique.prismwardrobe.core;

import dev.rique.prismwardrobe.api.WardrobeService;
import dev.rique.prismwardrobe.api.event.WardrobeDeletedEvent;
import dev.rique.prismwardrobe.api.event.WardrobeEquippedEvent;
import dev.rique.prismwardrobe.api.event.WardrobePreEquipEvent;
import dev.rique.prismwardrobe.api.event.WardrobePreSaveEvent;
import dev.rique.prismwardrobe.api.event.WardrobeSavedEvent;
import dev.rique.prismwardrobe.api.model.RestrictionDecision;
import dev.rique.prismwardrobe.api.model.ResultCode;
import dev.rique.prismwardrobe.api.model.WardrobeProfile;
import dev.rique.prismwardrobe.api.model.WardrobeResult;
import dev.rique.prismwardrobe.api.model.WardrobeSet;
import dev.rique.prismwardrobe.cache.WardrobeCache;
import dev.rique.prismwardrobe.config.PluginConfig;
import dev.rique.prismwardrobe.scheduler.SchedulerAdapter;
import dev.rique.prismwardrobe.storage.WardrobeRepository;
import dev.rique.prismwardrobe.util.ArmorPieceMatcher;
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
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public WardrobeServiceImpl(
            SchedulerAdapter schedulerAdapter,
            WardrobeRepository repository,
            WardrobeCache cache,
            SlotLimitServiceImpl slotLimitService,
            RestrictionServiceImpl restrictionService,
            WardrobeArmorBindingService bindingService,
            PluginConfig pluginConfig
    ) {
        this.schedulerAdapter = schedulerAdapter;
        this.repository = repository;
        this.cache = cache;
        this.slotLimitService = slotLimitService;
        this.restrictionService = restrictionService;
        this.bindingService = bindingService;
        this.pluginConfig = pluginConfig;
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
        if (!player.hasPermission("prismwardrobe.use")) {
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
                    .thenCompose(ignored -> repository.loadProfile(playerId))
                    .thenApply(updated -> {
                        cache.put(updated);
                        slotLimitService.primeBonus(playerId, updated.bonusSlots());
                        onPlayer(player, () -> {
                            Bukkit.getPluginManager().callEvent(new WardrobeSavedEvent(player, capture.wardrobeSet()));
                            return null;
                        });
                        return new WardrobeResult(ResultCode.SUCCESS_SAVED, "success.saved", Map.of("slot", String.valueOf(slot), "name", capture.wardrobeSet().name()));
                    })
                    .exceptionally(ex -> new WardrobeResult(ResultCode.ERROR_STORAGE, "error.storage", Map.of("reason", ex.getMessage())));
        });
    }

    @Override
    public CompletableFuture<WardrobeResult> equipSet(UUID playerId, int slot) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.ERROR_PLAYER_OFFLINE, "error.player-offline", Map.of()));
        }
        if (!player.hasPermission("prismwardrobe.use")) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_NO_PERMISSION, "error.no-permission", Map.of()));
        }

        int maxSlots = slotLimitService.getMaxSlots(player);
        if (slot < 1 || slot > maxSlots) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_SLOT_LOCKED, "error.slot-locked", Map.of("slot", String.valueOf(slot), "max", String.valueOf(maxSlots))));
        }

        return getProfile(playerId).thenCompose(profile -> {
            WardrobeSet set = profile.getSet(slot);
            boolean toggleOff = profile.selectedSlot() == slot && hasAnyBoundArmorForSlot(player, playerId, slot);

            if (!toggleOff && (set == null || !set.hasAnyArmorPiece())) {
                return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_NOTHING_SAVED, "error.nothing-saved", Map.of("slot", String.valueOf(slot))));
            }
            return onPlayer(player, () -> {
                if (toggleOff) {
                    clearBoundArmorForSlot(player.getInventory(), playerId, slot);
                    bindingService.clearActiveSlot(playerId);
                    String name = set == null || set.name().isBlank() ? "Slot " + slot : set.name();
                    return new WardrobeResult(ResultCode.SUCCESS, "success.unequipped",
                            Map.of("slot", String.valueOf(slot), "name", name));
                }

                if (!player.hasPermission("prismwardrobe.bypass.cooldown")) {
                    long remaining = getRemainingCooldownMillis(playerId);
                    if (remaining > 0) {
                        long seconds = Math.max(1L, (remaining + 999) / 1000);
                        return new WardrobeResult(ResultCode.DENIED_COOLDOWN, "error.cooldown", Map.of("seconds", String.valueOf(seconds)));
                    }
                }

                if (!player.hasPermission("prismwardrobe.bypass.restrictions")) {
                    RestrictionDecision decision = restrictionService.canEquip(player);
                    if (!decision.allowed()) {
                        return new WardrobeResult(decision.code(), decision.messageKey(), decision.placeholders());
                    }
                }

                if (!player.hasPermission("prismwardrobe.bypass.emptycheck")) {
                    Map<String, String> blockedSlot = firstNonEmptyAffectedSlot(player, set);
                    if (!blockedSlot.isEmpty()) {
                        return new WardrobeResult(ResultCode.DENIED_EMPTY_ARMOR_REQUIRED, "error.armor-slot-not-empty", blockedSlot);
                    }
                }

                WardrobePreEquipEvent preEquipEvent = new WardrobePreEquipEvent(player, set);
                Bukkit.getPluginManager().callEvent(preEquipEvent);
                if (preEquipEvent.isCancelled()) {
                    return new WardrobeResult(ResultCode.DENIED_EVENT_CANCELLED, "error.event-cancelled", Map.of());
                }

                PlayerInventory inventory = player.getInventory();
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
                if (!player.hasPermission("prismwardrobe.bypass.cooldown")) {
                    cooldowns.put(playerId, System.currentTimeMillis() + pluginConfig.equipCooldownMillis());
                }
                Bukkit.getPluginManager().callEvent(new WardrobeEquippedEvent(player, set));
                return new WardrobeResult(ResultCode.SUCCESS_EQUIPPED, "success.equipped", Map.of("slot", String.valueOf(slot), "name", set.name()));
            }).thenCompose(result -> {
                if (!result.isSuccess()) {
                    return CompletableFuture.completedFuture(result);
                }
                int selectedSlot = toggleOff ? -1 : slot;
                return repository.setSelectedSlot(playerId, selectedSlot)
                        .thenCompose(ignored -> repository.loadProfile(playerId))
                        .thenApply(updated -> {
                            cache.put(updated);
                            slotLimitService.primeBonus(playerId, updated.bonusSlots());
                            return result;
                        })
                        .exceptionally(ex -> new WardrobeResult(ResultCode.ERROR_STORAGE, "error.storage", Map.of("reason", ex.getMessage())));
            });
        });
    }

    @Override
    public CompletableFuture<WardrobeResult> deleteSet(UUID playerId, int slot) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.ERROR_PLAYER_OFFLINE, "error.player-offline", Map.of()));
        }
        int maxSlots = slotLimitService.getMaxSlots(player);
        if (slot < 1 || slot > maxSlots) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_INVALID_SLOT, "error.invalid-slot", Map.of("slot", String.valueOf(slot))));
        }
        return getProfile(playerId).thenCompose(profile -> {
            if (profile.getSet(slot) == null) {
                return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_NOTHING_SAVED, "error.nothing-saved", Map.of("slot", String.valueOf(slot))));
            }
            if (bindingService.getActiveSlot(playerId) == slot && bindingService.hasAnyBoundArmor(player)) {
                return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_EVENT_CANCELLED, "error.cannot-delete-equipped", Map.of("slot", String.valueOf(slot))));
            }
            return repository.deleteSet(playerId, slot)
                    .thenCompose(ignored -> repository.loadProfile(playerId))
                    .thenCompose(updated -> {
                        cache.put(updated);
                        slotLimitService.primeBonus(playerId, updated.bonusSlots());
                        return onPlayer(player, () -> {
                            Bukkit.getPluginManager().callEvent(new WardrobeDeletedEvent(player, slot));
                            return new WardrobeResult(ResultCode.SUCCESS_DELETED, "success.deleted", Map.of("slot", String.valueOf(slot)));
                        });
                    })
                    .exceptionally(ex -> new WardrobeResult(ResultCode.ERROR_STORAGE, "error.storage", Map.of("reason", ex.getMessage())));
        });
    }

    @Override
    public CompletableFuture<WardrobeResult> renameSet(UUID playerId, int slot, String newName) {
        if (newName == null || newName.isBlank()) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_INVALID_SLOT, "error.invalid-name", Map.of()));
        }
        String trimmedName = newName.trim();
        if (trimmedName.length() > 48) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_INVALID_SLOT, "error.name-too-long", Map.of("max", "48")));
        }
        return repository.renameSet(playerId, slot, trimmedName)
                .thenCompose(ignored -> repository.loadProfile(playerId))
                .thenApply(updated -> {
                    cache.put(updated);
                    slotLimitService.primeBonus(playerId, updated.bonusSlots());
                    return new WardrobeResult(ResultCode.SUCCESS_RENAMED, "success.renamed", Map.of("slot", String.valueOf(slot), "name", trimmedName));
                })
                .exceptionally(ex -> new WardrobeResult(ResultCode.ERROR_STORAGE, "error.storage", Map.of("reason", ex.getMessage())));
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
        long expiresAt = cooldowns.getOrDefault(playerId, 0L);
        long now = System.currentTimeMillis();
        return Math.max(0L, expiresAt - now);
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

    private boolean isBoundPieceForSlot(ItemStack itemStack, UUID playerId, int slot) {
        return bindingService.isBoundTo(itemStack, playerId) && bindingService.getBoundSlot(itemStack) == slot;
    }

    private <T> CompletableFuture<T> onPlayer(Player player, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        schedulerAdapter.runPlayer(player, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
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
        if (!player.hasPermission("prismwardrobe.use")) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_NO_PERMISSION, "error.no-permission", Map.of()));
        }
        int maxSlots = slotLimitService.getMaxSlots(player);
        if (slot < 1 || slot > maxSlots) {
            return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_SLOT_LOCKED, "error.slot-locked", Map.of("slot", String.valueOf(slot), "max", String.valueOf(maxSlots))));
        }
        boolean boundSlotEquipped = hasAnyBoundArmorForSlot(player, playerId, slot);
        if (boundSlotEquipped && !autoUnequipIfEquipped) {
            return CompletableFuture.completedFuture(new WardrobeResult(
                    ResultCode.DENIED_EVENT_CANCELLED,
                    "error.cannot-edit-equipped-piece",
                    Map.of("slot", String.valueOf(slot), "piece", piece)));
        }

        return getProfile(playerId).thenCompose(profile -> {
            CompletableFuture<Boolean> unequipFuture;
            if (boundSlotEquipped && autoUnequipIfEquipped) {
                unequipFuture = onPlayer(player, () -> {
                    clearBoundArmorForSlot(player.getInventory(), playerId, slot);
                    if (bindingService.getActiveSlot(playerId) == slot) {
                        bindingService.clearActiveSlot(playerId);
                    }
                    return true;
                });
            } else {
                unequipFuture = CompletableFuture.completedFuture(false);
            }

            return unequipFuture.thenCompose(autoUnequipped -> {
            WardrobeSet existing = profile.getSet(slot);
            if (existing == null && replacement == null) {
                return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_NOTHING_SAVED, "error.nothing-saved", Map.of("slot", String.valueOf(slot))));
            }

            String name = existing == null || existing.name().isBlank() ? "Slot " + slot : existing.name();
            boolean favorite = existing != null && existing.favorite();

            ItemStack helmet = existing == null ? null : existing.helmet();
            ItemStack chestplate = existing == null ? null : existing.chestplate();
            ItemStack leggings = existing == null ? null : existing.leggings();
            ItemStack boots = existing == null ? null : existing.boots();

            switch (piece) {
                case "helmet" -> helmet = replacement == null ? null : replacement.clone();
                case "chestplate" -> chestplate = replacement == null ? null : replacement.clone();
                case "leggings" -> leggings = replacement == null ? null : replacement.clone();
                case "boots" -> boots = replacement == null ? null : replacement.clone();
                default -> {
                    return CompletableFuture.completedFuture(new WardrobeResult(ResultCode.DENIED_INVALID_SLOT, "error.wrong-armor-type", Map.of()));
                }
            }

            WardrobeSet updated = new WardrobeSet(slot, name, favorite, helmet, chestplate, leggings, boots);
            CompletableFuture<Void> writeFuture = updated.hasAnyArmorPiece()
                    ? repository.saveSet(playerId, updated)
                    : repository.deleteSet(playerId, slot);

            return writeFuture
                    .thenCompose(ignored -> autoUnequipped ? repository.setSelectedSlot(playerId, -1) : CompletableFuture.completedFuture(null))
                    .thenCompose(ignored -> repository.loadProfile(playerId))
                    .thenApply(refreshed -> {
                        cache.put(refreshed);
                        slotLimitService.primeBonus(playerId, refreshed.bonusSlots());
                        return new WardrobeResult(ResultCode.SUCCESS, successKey, Map.of("slot", String.valueOf(slot), "piece", piece));
                    })
                    .exceptionally(ex -> new WardrobeResult(ResultCode.ERROR_STORAGE, "error.storage", Map.of("reason", ex.getMessage())));
            });
        });
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
}
