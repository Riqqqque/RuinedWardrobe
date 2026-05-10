package dev.rique.ruinedwardrobe.api.model;

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
        helmet = cloneItem(helmet);
        chestplate = cloneItem(chestplate);
        leggings = cloneItem(leggings);
        boots = cloneItem(boots);
    }

    @Override
    public ItemStack helmet() {
        return cloneItem(helmet);
    }

    @Override
    public ItemStack chestplate() {
        return cloneItem(chestplate);
    }

    @Override
    public ItemStack leggings() {
        return cloneItem(leggings);
    }

    @Override
    public ItemStack boots() {
        return cloneItem(boots);
    }

    public boolean hasAnyArmorPiece() {
        return hasPiece(helmet) || hasPiece(chestplate) || hasPiece(leggings) || hasPiece(boots);
    }

    private boolean hasPiece(ItemStack piece) {
        return piece != null && !piece.getType().isAir();
    }

    private static ItemStack cloneItem(ItemStack itemStack) {
        return itemStack == null ? null : itemStack.clone();
    }
}
