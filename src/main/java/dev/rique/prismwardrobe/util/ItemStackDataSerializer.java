package dev.rique.prismwardrobe.util;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

public final class ItemStackDataSerializer {

    private static final String BINARY_PREFIX = "b64:";

    public String toJson(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        byte[] bytes = itemStack.serializeAsBytes();
        return BINARY_PREFIX + Base64.getEncoder().encodeToString(bytes);
    }

    public ItemStack fromJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        String normalized = payload.trim();

        if (!normalized.startsWith(BINARY_PREFIX)) {
            throw new IllegalArgumentException("Unsupported item payload format");
        }
        return decodeBinary(normalized.substring(BINARY_PREFIX.length()));
    }

    private ItemStack decodeBinary(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return ItemStack.deserializeBytes(bytes);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid binary item payload", ex);
        }
    }
}
