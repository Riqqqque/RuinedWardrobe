package dev.rique.prismwardrobe.listener;

import dev.rique.prismwardrobe.core.WardrobeArmorBindingService;
import dev.rique.prismwardrobe.core.WardrobeArmorSyncService;
import dev.rique.prismwardrobe.gui.WardrobeMenuHolder;
import dev.rique.prismwardrobe.lang.MessageService;
import dev.rique.prismwardrobe.util.ArmorPieceMatcher;
import org.bukkit.entity.Player;
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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WardrobeArmorProtectionListener implements Listener {

    private static final long MESSAGE_COOLDOWN_MS = 300L;

    private final WardrobeArmorBindingService bindingService;
    private final WardrobeArmorSyncService armorSyncService;
    private final MessageService messageService;
    private final boolean strictContainerLockEnabled;
    private final Map<UUID, Long> lastLockMessageAt = new ConcurrentHashMap<>();

    public WardrobeArmorProtectionListener(
            WardrobeArmorBindingService bindingService,
            WardrobeArmorSyncService armorSyncService,
            MessageService messageService,
            boolean strictContainerLockEnabled
    ) {
        this.bindingService = bindingService;
        this.armorSyncService = armorSyncService;
        this.messageService = messageService;
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

        if (isBoundInteraction(player, event.getCurrentItem(), event.getCursor(), event.getClick(), event.getHotbarButton())) {
            event.setCancelled(true);
            sendRateLimited(player, "error.bound-armor-locked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (bindingService.isBound(event.getCursor()) || bindingService.isBound(event.getCurrentItem())) {
            event.setCancelled(true);
            sendRateLimited(player, "error.bound-armor-locked");
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
            sendRateLimited(player, "error.bound-armor-locked");
            return;
        }
        boolean touchesArmorSlot = event.getRawSlots().stream()
                .anyMatch(slot -> event.getView().getSlotType(slot) == InventoryType.SlotType.ARMOR);
        if (touchesArmorSlot && bindingService.hasAnyBoundArmor(player)) {
            event.setCancelled(true);
            sendRateLimited(player, "error.bound-armor-locked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (bindingService.isBound(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            sendRateLimited(event.getPlayer(), "error.bound-armor-locked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (bindingService.isBound(event.getMainHandItem()) || bindingService.isBound(event.getOffHandItem())) {
            event.setCancelled(true);
            sendRateLimited(event.getPlayer(), "error.bound-armor-locked");
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(bindingService::isBound);
        bindingService.clearActiveSlot(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        bindingService.sanitizeForeignBoundItems(event.getPlayer());
        bindingService.resolveActiveSlot(event.getPlayer());
        armorSyncService.requestSync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        armorSyncService.requestSync(event.getPlayer());
        bindingService.clearActiveSlot(event.getPlayer().getUniqueId());
        lastLockMessageAt.remove(event.getPlayer().getUniqueId());
    }

    private boolean isBoundInteraction(Player player, org.bukkit.inventory.ItemStack current, org.bukkit.inventory.ItemStack cursor, ClickType clickType, int hotbarButton) {
        if (bindingService.isBound(current) || bindingService.isBound(cursor)) {
            return true;
        }
        if (clickType == ClickType.DOUBLE_CLICK && bindingService.hasAnyBoundArmor(player)) {
            return true;
        }
        if (clickType == ClickType.NUMBER_KEY && hotbarButton >= 0) {
            org.bukkit.inventory.ItemStack hotbar = player.getInventory().getItem(hotbarButton);
            if (bindingService.isBound(hotbar)) {
                return true;
            }
        }
        if (clickType == ClickType.SWAP_OFFHAND) {
            return bindingService.isBound(player.getInventory().getItemInOffHand());
        }
        return false;
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
