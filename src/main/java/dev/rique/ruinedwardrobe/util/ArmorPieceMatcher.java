package dev.rique.ruinedwardrobe.util;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.EquipmentSlot;

public final class ArmorPieceMatcher {

    private ArmorPieceMatcher() {
    }

    public static boolean matchesWardrobePiece(String piece, Material material) {
        if (piece == null || material == null || material.isAir()) {
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
        if (material == null || material.isAir()) {
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
        return Tag.ITEMS_HEAD_ARMOR.isTagged(material);
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
}
