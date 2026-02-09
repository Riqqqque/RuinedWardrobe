package dev.rique.prismwardrobe.core;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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

    public boolean hasAnyBoundArmor(Player player) {
        return isBound(player.getInventory().getHelmet())
                || isBound(player.getInventory().getChestplate())
                || isBound(player.getInventory().getLeggings())
                || isBound(player.getInventory().getBoots());
    }

    public void sanitizeForeignBoundItems(Player player) {
        UUID playerId = player.getUniqueId();
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!isBound(item)) {
                continue;
            }
            if (isBoundTo(item, playerId)) {
                continue;
            }
            contents[i] = null;
            changed = true;
        }
        if (changed) {
            player.getInventory().setContents(contents);
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isBound(offHand) && !isBoundTo(offHand, playerId)) {
            player.getInventory().setItemInOffHand(null);
        }
    }
}

