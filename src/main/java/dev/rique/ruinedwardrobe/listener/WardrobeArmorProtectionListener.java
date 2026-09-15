package dev.rique.ruinedwardrobe.listener;

import dev.rique.ruinedwardrobe.core.WardrobeAuditLogger;
import dev.rique.ruinedwardrobe.core.WardrobeArmorBindingService;
import dev.rique.ruinedwardrobe.core.WardrobeArmorSyncService;
import dev.rique.ruinedwardrobe.gui.WardrobeMenuHolder;
import dev.rique.ruinedwardrobe.lang.MessageService;
import dev.rique.ruinedwardrobe.scheduler.SchedulerAdapter;
import dev.rique.ruinedwardrobe.util.ArmorPieceMatcher;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class WardrobeArmorProtectionListener implements Listener {

    private static final long MESSAGE_COOLDOWN_MS = 300L;

    private final WardrobeArmorBindingService bindingService;
    private final WardrobeArmorSyncService armorSyncService;
    private final WardrobeAuditLogger auditLogger;
    private final MessageService messageService;
    private final SchedulerAdapter schedulerAdapter;
    private final boolean strictContainerLockEnabled;
    private final Map<UUID, Long> lastLockMessageAt = new ConcurrentHashMap<>();

    public WardrobeArmorProtectionListener(
            WardrobeArmorBindingService bindingService,
            WardrobeArmorSyncService armorSyncService,
            WardrobeAuditLogger auditLogger,
            MessageService messageService,
            SchedulerAdapter schedulerAdapter,
            boolean strictContainerLockEnabled
    ) {
        this.bindingService = bindingService;
        this.armorSyncService = armorSyncService;
        this.auditLogger = auditLogger;
        this.messageService = messageService;
        this.schedulerAdapter = schedulerAdapter;
        this.strictContainerLockEnabled = strictContainerLockEnabled;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!strictContainerLockEnabled) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!bindingService.hasAnyBoundArmor(player)) {
            return;
        }
        if (!isBlockedContainer(event.getView().getTopInventory().getHolder(), event.getView().getTopInventory().getType())) {
            return;
        }
        event.setCancelled(true);
        sendRateLimited(player, "error.bound-armor-container-locked");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean wardrobeView = event.getView().getTopInventory().getHolder() instanceof WardrobeMenuHolder;
        if (wardrobeView && event.getClickedInventory() != player.getInventory()) {
            // Wardrobe GUI top inventory has dedicated handlers.
            return;
        }
        if (strictContainerLockEnabled
                && bindingService.hasAnyBoundArmor(player)
                && isBlockedContainer(event.getView().getTopInventory().getHolder(), event.getView().getTopInventory().getType())) {
            event.setCancelled(true);
            sendRateLimited(player, "error.bound-armor-container-locked");
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE) {
            if (touchesBoundClickItem(player, event)) {
                rejectBoundCreativeAction(player, event);
            }
            return;
        }

        if (shouldLockEquippedArmorClick(player, event)) {
            event.setCancelled(true);
            sendRateLimited(player, "error.bound-armor-locked");
            return;
        }
        normalizeDetachedClickItems(player, event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (touchesBoundClickItem(player, event)) {
            rejectBoundCreativeAction(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (strictContainerLockEnabled
                && bindingService.hasAnyBoundArmor(player)
                && isBlockedContainer(event.getView().getTopInventory().getHolder(), event.getView().getTopInventory().getType())) {
            event.setCancelled(true);
            sendRateLimited(player, "error.bound-armor-container-locked");
            return;
        }
        if (bindingService.isBound(event.getOldCursor())) {
            event.setCancelled(true);
            if (player.getGameMode() == GameMode.CREATIVE) {
                sendRateLimited(player, "error.bound-armor-locked");
                scheduleReconciliation(player, "creative-drag");
                return;
            }
            if (bindingService.hasCanonicalArmorCopy(player, event.getOldCursor())) {
                event.setCursor(null);
                auditDuplicateRemoval(player, "drag-cursor", 1);
                resyncInventory(player);
                return;
            }
            trackDetachedSource(player, event.getOldCursor());
            event.setCursor(bindingService.unbindCopy(event.getOldCursor()));
            requestArmorSync(player);
            return;
        }
        if (touchesLockedArmorSlot(player, event)) {
            event.setCancelled(true);
            sendRateLimited(player, "error.bound-armor-locked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (bindingService.isBound(dropped)) {
            if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
                rejectBoundCreativeAction(event.getPlayer(), event);
                return;
            }
            if (bindingService.hasCanonicalArmorCopy(event.getPlayer(), dropped)) {
                event.setCancelled(true);
                scheduleReconciliation(event.getPlayer(), "duplicate-drop");
                return;
            }
            trackDetachedSource(event.getPlayer(), dropped);
            event.getItemDrop().setItemStack(bindingService.unbindCopy(dropped));
            requestArmorSync(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        boolean boundMainHand = bindingService.isBound(event.getMainHandItem());
        boolean boundOffHand = bindingService.isBound(event.getOffHandItem());
        if (!boundMainHand && !boundOffHand) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            rejectBoundCreativeAction(player, event);
            return;
        }
        if (bindingService.hasCanonicalArmorCopy(player, event.getMainHandItem())
                || bindingService.hasCanonicalArmorCopy(player, event.getOffHandItem())) {
            event.setCancelled(true);
            scheduleReconciliation(player, "duplicate-hand-swap");
            return;
        }
        boolean changed = false;
        if (boundMainHand) {
            trackDetachedSource(player, event.getMainHandItem());
            event.setMainHandItem(bindingService.unbindCopy(event.getMainHandItem()));
            changed = true;
        }
        if (boundOffHand) {
            trackDetachedSource(player, event.getOffHandItem());
            event.setOffHandItem(bindingService.unbindCopy(event.getOffHandItem()));
            changed = true;
        }
        if (changed) {
            requestArmorSync(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispenseArmor(BlockDispenseArmorEvent event) {
        if (!(event.getTargetEntity() instanceof Player player)) {
            return;
        }
        ItemStack protectedPiece = protectedPieceForIncomingArmor(player, event.getItem());
        if (!bindingService.isBound(protectedPiece)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(PlayerItemDamageEvent event) {
        if (bindingService.isBound(event.getItem())) {
            armorSyncService.requestSync(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMend(PlayerItemMendEvent event) {
        if (bindingService.isBound(event.getItem())) {
            armorSyncService.requestSync(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(PlayerItemBreakEvent event) {
        if (bindingService.isBound(event.getBrokenItem())) {
            armorSyncService.requestSync(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        armorSyncService.handleDeath(player, event.getKeepInventory(), event.getDrops())
                .exceptionally(ex -> {
                    auditLogger.error(
                            "DEATH_SYNC_ERROR",
                            player.getUniqueId(),
                            player.getName(),
                            ex,
                            Map.of("keepInventory", event.getKeepInventory()));
                    return null;
                });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        armorSyncService.finishDeath(player.getUniqueId());
        reconcileBoundItems(player, "join");
        bindingService.resolveActiveSlot(player);
        if (armorSyncService.hasTrackedArmor(player)) {
            armorSyncService.requestSync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (armorSyncService.hasTrackedArmor(player)) {
            armorSyncService.syncNowFromPlayerEvent(player).exceptionally(ex -> {
                auditLogger.error(
                        "QUIT_SYNC_ERROR",
                        player.getUniqueId(),
                        player.getName(),
                        ex,
                        Map.of());
                return null;
            });
        }
        armorSyncService.finishDeath(player.getUniqueId());
        bindingService.clearActiveSlot(player.getUniqueId());
        lastLockMessageAt.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        armorSyncService.finishDeath(event.getPlayer().getUniqueId());
    }

    private boolean shouldLockEquippedArmorClick(Player player, InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.ARMOR) {
            return false;
        }
        return bindingService.isBoundTo(event.getCurrentItem(), player.getUniqueId());
    }

    private void normalizeDetachedClickItems(Player player, InventoryClickEvent event) {
        int duplicatesRemoved = 0;
        boolean detachedUnbound = false;
        if (bindingService.isBound(event.getCurrentItem())) {
            if (bindingService.hasCanonicalArmorCopy(player, event.getCurrentItem())) {
                event.setCurrentItem(null);
                duplicatesRemoved++;
            } else {
                trackDetachedSource(player, event.getCurrentItem());
                event.setCurrentItem(bindingService.unbindCopy(event.getCurrentItem()));
                detachedUnbound = true;
            }
        }
        if (bindingService.isBound(event.getCursor())) {
            if (bindingService.hasCanonicalArmorCopy(player, event.getCursor())) {
                event.getView().setCursor(null);
                duplicatesRemoved++;
            } else {
                trackDetachedSource(player, event.getCursor());
                event.getView().setCursor(bindingService.unbindCopy(event.getCursor()));
                detachedUnbound = true;
            }
        }
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            if (bindingService.isBound(hotbar)) {
                if (bindingService.hasCanonicalArmorCopy(player, hotbar)) {
                    player.getInventory().setItem(event.getHotbarButton(), null);
                    duplicatesRemoved++;
                } else {
                    trackDetachedSource(player, hotbar);
                    player.getInventory().setItem(event.getHotbarButton(), bindingService.unbindCopy(hotbar));
                    detachedUnbound = true;
                }
            }
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (bindingService.isBound(offHand)) {
                if (bindingService.hasCanonicalArmorCopy(player, offHand)) {
                    player.getInventory().setItemInOffHand(null);
                    duplicatesRemoved++;
                } else {
                    trackDetachedSource(player, offHand);
                    player.getInventory().setItemInOffHand(bindingService.unbindCopy(offHand));
                    detachedUnbound = true;
                }
            }
        }
        if (duplicatesRemoved > 0) {
            event.setCancelled(true);
            auditDuplicateRemoval(player, "inventory-click", duplicatesRemoved);
            resyncInventory(player);
        }
        if (detachedUnbound) {
            requestArmorSync(player);
        }
    }

    private boolean touchesLockedArmorSlot(Player player, InventoryDragEvent event) {
        for (int rawSlot : event.getRawSlots()) {
            if (event.getView().getSlotType(rawSlot) != InventoryType.SlotType.ARMOR) {
                continue;
            }
            if (bindingService.isBoundTo(event.getView().getItem(rawSlot), player.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private boolean touchesBoundClickItem(Player player, InventoryClickEvent event) {
        if (bindingService.isBound(event.getCurrentItem()) || bindingService.isBound(event.getCursor())) {
            return true;
        }
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
                && bindingService.isBound(player.getInventory().getItem(event.getHotbarButton()))) {
            return true;
        }
        return event.getClick() == ClickType.SWAP_OFFHAND
                && bindingService.isBound(player.getInventory().getItemInOffHand());
    }

    private void rejectBoundCreativeAction(Player player, Cancellable event) {
        event.setCancelled(true);
        sendRateLimited(player, "error.bound-armor-locked");
        scheduleReconciliation(player, "creative-action");
    }

    private void resyncInventory(Player player) {
        try {
            schedulerAdapter.runPlayer(player, player::updateInventory, () -> {
            });
        } catch (IllegalStateException ignored) {
            // The player disconnected before the authoritative state could be resent.
        }
    }

    private void scheduleReconciliation(Player player, String source) {
        try {
            schedulerAdapter.runPlayer(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                reconcileBoundItems(player, source);
                player.updateInventory();
            }, () -> {
            });
        } catch (IllegalStateException ignored) {
            // The player disconnected before the authoritative state could be reconciled.
        }
    }

    private void reconcileBoundItems(Player player, String source) {
        WardrobeArmorBindingService.SanitizationResult result = bindingService.sanitizeBoundItems(player);
        if (!result.changed()) {
            return;
        }
        auditLogger.record(
                "BOUND_ARMOR_RECONCILED",
                player.getUniqueId(),
                player.getName(),
                Map.of(
                        "source", source,
                        "foreignRemoved", result.foreignRemoved(),
                        "duplicatesRemoved", result.duplicatesRemoved(),
                        "detachedUnbound", result.detachedUnbound()));
        if (result.detachedUnbound() > 0) {
            requestArmorSync(player);
        }
    }

    private void auditDuplicateRemoval(Player player, String source, int count) {
        auditLogger.record(
                "BOUND_ARMOR_DUPLICATE_REMOVED",
                player.getUniqueId(),
                player.getName(),
                Map.of("source", source, "removedItems", count));
    }

    private void trackDetachedSource(Player player, ItemStack itemStack) {
        if (!bindingService.isBoundTo(itemStack, player.getUniqueId())) {
            return;
        }
        int slot = bindingService.getBoundSlot(itemStack);
        if (slot > 0) {
            bindingService.setActiveSlot(player.getUniqueId(), slot);
        }
    }

    private void requestArmorSync(Player player) {
        CompletableFuture<Void> future = armorSyncService.requestSync(player);
        if (future == null) {
            return;
        }
        future.exceptionally(ex -> {
            auditLogger.error(
                    "DETACHED_BOUND_ARMOR_SYNC_ERROR",
                    player.getUniqueId(),
                    player.getName(),
                    ex,
                    Map.of("source", "protection-listener"));
            return null;
        });
    }

    private ItemStack protectedPieceForIncomingArmor(Player player, ItemStack incoming) {
        if (incoming == null) {
            return null;
        }
        String piece = ArmorPieceMatcher.resolvePlayerEquipmentPiece(incoming.getType());
        if (piece == null) {
            return null;
        }
        return switch (piece) {
            case "helmet" -> player.getInventory().getHelmet();
            case "chestplate" -> player.getInventory().getChestplate();
            case "leggings" -> player.getInventory().getLeggings();
            case "boots" -> player.getInventory().getBoots();
            default -> null;
        };
    }

    private boolean isBlockedContainer(org.bukkit.inventory.InventoryHolder holder, InventoryType type) {
        if (holder instanceof WardrobeMenuHolder) {
            return false;
        }
        return switch (type) {
            case CRAFTING, CREATIVE, PLAYER -> false;
            default -> true;
        };
    }

    private void sendRateLimited(Player player, String key) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        long last = lastLockMessageAt.getOrDefault(playerId, 0L);
        if (now - last < MESSAGE_COOLDOWN_MS) {
            return;
        }
        lastLockMessageAt.put(playerId, now);
        messageService.send(player, key);
    }
}
