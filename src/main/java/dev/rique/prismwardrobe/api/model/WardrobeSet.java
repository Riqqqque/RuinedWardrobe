package dev.rique.prismwardrobe.api.model;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public record WardrobeSet(
        int slot,
        String name,
        boolean favorite,
        ItemStack helmet,
        ItemStack chestplate,
        ItemStack leggings,
        ItemStack boots
) {
    public WardrobeSet {
        name = Objects.requireNonNullElse(name, "");
        helmet = helmet == null ? null : helmet.clone();
        chestplate = chestplate == null ? null : chestplate.clone();
        leggings = leggings == null ? null : leggings.clone();
        boots = boots == null ? null : boots.clone();
    }

    public boolean hasAnyArmorPiece() {
        return hasPiece(helmet) || hasPiece(chestplate) || hasPiece(leggings) || hasPiece(boots);
    }

    private boolean hasPiece(ItemStack piece) {
        return piece != null && !piece.getType().isAir();
    }
}
