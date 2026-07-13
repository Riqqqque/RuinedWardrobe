package dev.rique.ruinedwardrobe.core;

import dev.rique.ruinedwardrobe.util.ArmorPieceMatcher;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WardrobeArmorBindingService {

    private final NamespacedKey ownerKey;
    private final NamespacedKey slotKey;
    private final NamespacedKey pieceKey;
    private final Map<UUID, Integer> activeSlotByPlayer = new ConcurrentHashMap<>();

    public WardrobeArmorBindingService(JavaPlugin plugin) {
        this.ownerKey = new NamespacedKey(plugin, "bound_owner");
        this.slotKey = new NamespacedKey(plugin, "bound_slot");
        this.pieceKey = new NamespacedKey(plugin, "bound_piece");
    }

    public ItemStack bindCopy(ItemStack itemStack, UUID owner, int slot, String piece) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        ItemStack copy = itemStack.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ownerKey, PersistentDataType.STRING, owner.toString());
        pdc.set(slotKey, PersistentDataType.INTEGER, slot);
        pdc.set(pieceKey, PersistentDataType.STRING, piece);
        copy.setItemMeta(meta);
        return copy;
    }

    public ItemStack unbindCopy(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        ItemStack copy = itemStack.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(ownerKey);
        pdc.remove(slotKey);
        pdc.remove(pieceKey);
        copy.setItemMeta(meta);
        return copy;
    }

    public boolean isBound(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(ownerKey, PersistentDataType.STRING) && pdc.has(slotKey, PersistentDataType.INTEGER);
    }

    public boolean isBoundTo(ItemStack itemStack, UUID owner) {
        if (!isBound(itemStack)) {
            return false;
        }
        String value = itemStack.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return owner.toString().equals(value);
    }

    public int getBoundSlot(ItemStack itemStack) {
        if (!isBound(itemStack)) {
            return -1;
        }
        Integer slot = itemStack.getItemMeta().getPersistentDataContainer().get(slotKey, PersistentDataType.INTEGER);
        return slot == null ? -1 : slot;
    }

    public String getBoundPiece(ItemStack itemStack) {
        if (!isBound(itemStack)) {
            return "";
        }
        String value = itemStack.getItemMeta().getPersistentDataContainer().get(pieceKey, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    public void setActiveSlot(UUID playerId, int slot) {
        if (slot < 1) {
            activeSlotByPlayer.remove(playerId);
            return;
        }
        activeSlotByPlayer.put(playerId, slot);
    }

    public int getActiveSlot(UUID playerId) {
        return activeSlotByPlayer.getOrDefault(playerId, -1);
    }

    public void clearActiveSlot(UUID playerId) {
        activeSlotByPlayer.remove(playerId);
    }

    public int resolveActiveSlot(Player player) {
        int tracked = getActiveSlot(player.getUniqueId());
        if (tracked > 0) {
            return tracked;
        }
        ItemStack[] armor = {
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()
        };
        for (ItemStack piece : armor) {
            if (isBoundTo(piece, player.getUniqueId())) {
                int slot = getBoundSlot(piece);
                if (slot > 0) {
                    setActiveSlot(player.getUniqueId(), slot);
                    return slot;
                }
            }
        }
        return -1;
    }

    public boolean hasCanonicalArmorCopy(Player player, ItemStack itemStack) {
        if (player == null || !isBoundTo(itemStack, player.getUniqueId())) {
            return false;
        }
        String piece = getBoundPiece(itemStack);
        ItemStack equipped = armorPiece(player.getInventory(), piece);
        return isBoundTo(equipped, player.getUniqueId())
                && getBoundSlot(equipped) == getBoundSlot(itemStack)
                && piece.equals(getBoundPiece(equipped));
    }

    public boolean hasAnyBoundArmor(Player player) {
        return isBound(player.getInventory().getHelmet())
                || isBound(player.getInventory().getChestplate())
                || isBound(player.getInventory().getLeggings())
                || isBound(player.getInventory().getBoots());
    }

    public SanitizationResult sanitizeBoundItems(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerInventory inventory = player.getInventory();
        MutableSanitization result = new MutableSanitization();

        int activeSlot = chooseActiveSlot(player);
        sanitizeArmorPiece(inventory, "helmet", inventory.getHelmet(), playerId, activeSlot, result);
        sanitizeArmorPiece(inventory, "chestplate", inventory.getChestplate(), playerId, activeSlot, result);
        sanitizeArmorPiece(inventory, "leggings", inventory.getLeggings(), playerId, activeSlot, result);
        sanitizeArmorPiece(inventory, "boots", inventory.getBoots(), playerId, activeSlot, result);

        ItemStack[] contents = inventory.getStorageContents();
        boolean storageChanged = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!isBound(item)) {
                continue;
            }
            contents[i] = sanitizeDetachedItem(player, item, activeSlot, result);
            storageChanged = true;
        }
        if (storageChanged) {
            inventory.setStorageContents(contents);
        }

        ItemStack offHand = inventory.getItemInOffHand();
        if (isBound(offHand)) {
            inventory.setItemInOffHand(sanitizeDetachedItem(player, offHand, activeSlot, result));
        }

        ItemStack cursor = player.getItemOnCursor();
        if (isBound(cursor)) {
            player.setItemOnCursor(sanitizeDetachedItem(player, cursor, activeSlot, result));
        }
        if (activeSlot > 0 && (hasOwnedArmorForSlot(player, activeSlot) || result.detachedUnbound > 0)) {
            setActiveSlot(playerId, activeSlot);
        } else {
            clearActiveSlot(playerId);
        }
        return result.toResult();
    }

    private int chooseActiveSlot(Player player) {
        int tracked = getActiveSlot(player.getUniqueId());
        if (tracked > 0 && hasValidArmorForSlot(player, tracked)) {
            return tracked;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] armor = {
                inventory.getHelmet(),
                inventory.getChestplate(),
                inventory.getLeggings(),
                inventory.getBoots()
        };
        String[] pieces = {"helmet", "chestplate", "leggings", "boots"};
        for (int i = 0; i < armor.length; i++) {
            if (isValidOwnedArmorPiece(armor[i], player.getUniqueId(), pieces[i])) {
                return getBoundSlot(armor[i]);
            }
        }
        if (tracked > 0 && hasOwnedBoundItemForSlot(player, tracked)) {
            return tracked;
        }
        for (ItemStack itemStack : armor) {
            int slot = ownedBoundSlot(itemStack, player.getUniqueId());
            if (slot > 0) {
                return slot;
            }
        }
        for (ItemStack itemStack : inventory.getStorageContents()) {
            int slot = ownedBoundSlot(itemStack, player.getUniqueId());
            if (slot > 0) {
                return slot;
            }
        }
        int offHandSlot = ownedBoundSlot(inventory.getItemInOffHand(), player.getUniqueId());
        if (offHandSlot > 0) {
            return offHandSlot;
        }
        int cursorSlot = ownedBoundSlot(player.getItemOnCursor(), player.getUniqueId());
        if (cursorSlot > 0) {
            return cursorSlot;
        }
        return -1;
    }

    private boolean hasOwnedBoundItemForSlot(Player player, int slot) {
        PlayerInventory inventory = player.getInventory();
        UUID owner = player.getUniqueId();
        if (ownedBoundSlot(inventory.getHelmet(), owner) == slot
                || ownedBoundSlot(inventory.getChestplate(), owner) == slot
                || ownedBoundSlot(inventory.getLeggings(), owner) == slot
                || ownedBoundSlot(inventory.getBoots(), owner) == slot) {
            return true;
        }
        for (ItemStack itemStack : inventory.getStorageContents()) {
            if (ownedBoundSlot(itemStack, owner) == slot) {
                return true;
            }
        }
        return ownedBoundSlot(inventory.getItemInOffHand(), owner) == slot
                || ownedBoundSlot(player.getItemOnCursor(), owner) == slot;
    }

    private int ownedBoundSlot(ItemStack itemStack, UUID owner) {
        return isBoundTo(itemStack, owner) ? getBoundSlot(itemStack) : -1;
    }

    private boolean hasValidArmorForSlot(Player player, int slot) {
        PlayerInventory inventory = player.getInventory();
        UUID playerId = player.getUniqueId();
        return isValidOwnedArmorPiece(inventory.getHelmet(), playerId, "helmet", slot)
                || isValidOwnedArmorPiece(inventory.getChestplate(), playerId, "chestplate", slot)
                || isValidOwnedArmorPiece(inventory.getLeggings(), playerId, "leggings", slot)
                || isValidOwnedArmorPiece(inventory.getBoots(), playerId, "boots", slot);
    }

    private boolean hasOwnedArmorForSlot(Player player, int slot) {
        PlayerInventory inventory = player.getInventory();
        UUID playerId = player.getUniqueId();
        return isBoundTo(inventory.getHelmet(), playerId) && getBoundSlot(inventory.getHelmet()) == slot
                || isBoundTo(inventory.getChestplate(), playerId) && getBoundSlot(inventory.getChestplate()) == slot
                || isBoundTo(inventory.getLeggings(), playerId) && getBoundSlot(inventory.getLeggings()) == slot
                || isBoundTo(inventory.getBoots(), playerId) && getBoundSlot(inventory.getBoots()) == slot;
    }

    private boolean isValidOwnedArmorPiece(ItemStack itemStack, UUID owner, String piece) {
        return isValidOwnedArmorPiece(itemStack, owner, piece, getBoundSlot(itemStack));
    }

    private boolean isValidOwnedArmorPiece(ItemStack itemStack, UUID owner, String piece, int slot) {
        return slot > 0
                && isBoundTo(itemStack, owner)
                && getBoundSlot(itemStack) == slot
                && piece.equals(getBoundPiece(itemStack))
                && ArmorPieceMatcher.matchesWardrobePiece(piece, itemStack.getType());
    }

    private void sanitizeArmorPiece(
            PlayerInventory inventory,
            String piece,
            ItemStack itemStack,
            UUID owner,
            int activeSlot,
            MutableSanitization result) {
        if (!isBound(itemStack)) {
            return;
        }
        if (!isBoundTo(itemStack, owner)) {
            setArmorPiece(inventory, piece, null);
            result.foreignRemoved++;
            return;
        }
        if (!piece.equals(getBoundPiece(itemStack))
                || getBoundSlot(itemStack) < 1
                || !ArmorPieceMatcher.matchesWardrobePiece(piece, itemStack.getType())) {
            setArmorPiece(inventory, piece, unbindCopy(itemStack));
            result.detachedUnbound++;
            return;
        }
        if (activeSlot > 0 && getBoundSlot(itemStack) != activeSlot) {
            setArmorPiece(inventory, piece, null);
            result.duplicatesRemoved++;
        }
    }

    private ItemStack sanitizeDetachedItem(
            Player player,
            ItemStack itemStack,
            int activeSlot,
            MutableSanitization result) {
        if (!isBoundTo(itemStack, player.getUniqueId())) {
            result.foreignRemoved++;
            return null;
        }
        if (hasCanonicalArmorCopy(player, itemStack)) {
            result.duplicatesRemoved++;
            return null;
        }
        if (activeSlot > 0 && getBoundSlot(itemStack) != activeSlot) {
            result.duplicatesRemoved++;
            return null;
        }
        result.detachedUnbound++;
        return unbindCopy(itemStack);
    }

    private ItemStack armorPiece(PlayerInventory inventory, String piece) {
        return switch (piece) {
            case "helmet" -> inventory.getHelmet();
            case "chestplate" -> inventory.getChestplate();
            case "leggings" -> inventory.getLeggings();
            case "boots" -> inventory.getBoots();
            default -> null;
        };
    }

    private void setArmorPiece(PlayerInventory inventory, String piece, ItemStack itemStack) {
        switch (piece) {
            case "helmet" -> inventory.setHelmet(itemStack);
            case "chestplate" -> inventory.setChestplate(itemStack);
            case "leggings" -> inventory.setLeggings(itemStack);
            case "boots" -> inventory.setBoots(itemStack);
            default -> throw new IllegalArgumentException("Unknown armor piece: " + piece);
        }
    }

    public record SanitizationResult(int foreignRemoved, int duplicatesRemoved, int detachedUnbound) {
        public boolean changed() {
            return foreignRemoved > 0 || duplicatesRemoved > 0 || detachedUnbound > 0;
        }

        public int removed() {
            return foreignRemoved + duplicatesRemoved;
        }
    }

    private static final class MutableSanitization {
        private int foreignRemoved;
        private int duplicatesRemoved;
        private int detachedUnbound;

        private SanitizationResult toResult() {
            return new SanitizationResult(foreignRemoved, duplicatesRemoved, detachedUnbound);
        }
    }
}
