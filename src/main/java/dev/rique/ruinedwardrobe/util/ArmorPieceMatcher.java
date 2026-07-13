package dev.rique.ruinedwardrobe.util;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.EquipmentSlot;

import java.util.EnumSet;
import java.util.Set;

public final class ArmorPieceMatcher {

    private static final Set<Material> SKULL_ITEMS = EnumSet.of(
            Material.PLAYER_HEAD,
            Material.CREEPER_HEAD,
            Material.DRAGON_HEAD,
            Material.PIGLIN_HEAD,
            Material.SKELETON_SKULL,
            Material.WITHER_SKELETON_SKULL,
            Material.ZOMBIE_HEAD);

    private ArmorPieceMatcher() {
    }

    public static boolean matchesWardrobePiece(String piece, Material material) {
        if (piece == null || isAir(material)) {
            return false;
        }
        return switch (piece) {
            case "helmet" -> isHeadArmor(material);
            case "chestplate" -> isChestArmor(material);
            case "leggings" -> isLegArmor(material);
            case "boots" -> isFootArmor(material);
            default -> false;
        };
    }

    public static String resolvePlayerEquipmentPiece(Material material) {
        if (isAir(material)) {
            return null;
        }
        EquipmentSlot slot = material.getEquipmentSlot();
        if (slot == null) {
            return null;
        }
        return switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> null;
        };
    }

    private static boolean isHeadArmor(Material material) {
        return SKULL_ITEMS.contains(material)
                || Tag.ITEMS_HEAD_ARMOR.isTagged(material)
                || Tag.ITEMS_SKULLS.isTagged(material);
    }

    private static boolean isChestArmor(Material material) {
        // Keep explicit ELYTRA support even though modern tags usually include it.
        return Tag.ITEMS_CHEST_ARMOR.isTagged(material) || material == Material.ELYTRA;
    }

    private static boolean isLegArmor(Material material) {
        return Tag.ITEMS_LEG_ARMOR.isTagged(material);
    }

    private static boolean isFootArmor(Material material) {
        return Tag.ITEMS_FOOT_ARMOR.isTagged(material);
    }

    private static boolean isAir(Material material) {
        return material == null
                || material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR;
    }
}
