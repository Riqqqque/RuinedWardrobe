package dev.rique.fluxwardrobe.util;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.Locale;

public final class ItemStackDataSerializer {

    private static final String ROOT_KEY = "item";
    private static final String YAML_PREFIX = "yaml:";
    private static final String LEGACY_BINARY_PREFIX = "b64:";

    public String storageFormatId() {
        return "yaml-v1";
    }

    public String serialize(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(ROOT_KEY, itemStack);
        return YAML_PREFIX + yaml.saveToString();
    }

    public ItemStack deserialize(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }

        String normalized = payload.trim();
        if (normalized.startsWith(YAML_PREFIX)) {
            return decodeYaml(normalized.substring(YAML_PREFIX.length()));
        }
        if (normalized.startsWith(LEGACY_BINARY_PREFIX)) {
            return decodeLegacyBinary(normalized.substring(LEGACY_BINARY_PREFIX.length()));
        }
        if (looksLikeYaml(normalized)) {
            return decodeYaml(normalized);
        }
        throw new IllegalArgumentException("Unsupported item payload format");
    }

    private ItemStack decodeYaml(String yamlPayload) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(yamlPayload);
            ItemStack itemStack = yaml.getItemStack(ROOT_KEY);
            if (itemStack == null || itemStack.getType().isAir()) {
                throw new IllegalArgumentException("YAML item payload did not contain a valid item");
            }
            return itemStack;
        } catch (InvalidConfigurationException ex) {
            throw new IllegalArgumentException("Invalid YAML item payload", ex);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid YAML item data", ex);
        }
    }

    private ItemStack decodeLegacyBinary(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            ItemStack itemStack = ItemStack.deserializeBytes(bytes);
            if (itemStack == null || itemStack.getType().isAir()) {
                throw new IllegalArgumentException("Binary item payload did not contain a valid item");
            }
            return itemStack;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid legacy binary item payload", ex);
        }
    }

    private boolean looksLikeYaml(String payload) {
        String normalized = payload.toLowerCase(Locale.ROOT);
        return normalized.startsWith(ROOT_KEY + ":")
                || normalized.startsWith("'item':")
                || normalized.startsWith("\"item\":");
    }
}
