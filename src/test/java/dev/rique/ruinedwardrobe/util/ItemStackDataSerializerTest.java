package dev.rique.ruinedwardrobe.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemStackDataSerializerTest {

    @Test
    void returnsNullForBlankPayloads() {
        ItemStackDataSerializer serializer = new ItemStackDataSerializer();
        assertNull(serializer.deserialize(null));
        assertNull(serializer.deserialize(""));
    }

    @Test
    void throwsOnUnsupportedPayload() {
        ItemStackDataSerializer serializer = new ItemStackDataSerializer();
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize("{invalid payload"));
    }

    @Test
    void serializeReturnsNullForNullItems() {
        ItemStackDataSerializer serializer = new ItemStackDataSerializer();
        assertNull(serializer.serialize(null));
    }

    @Test
    void serializeUsesYamlPrefixForNonAirItems() {
        ItemStackDataSerializer serializer = new ItemStackDataSerializer();
        ItemStack itemStack = mock(ItemStack.class);
        Material material = mock(Material.class);

        when(material.isAir()).thenReturn(false);
        when(itemStack.getType()).thenReturn(material);
        when(itemStack.serialize()).thenReturn(Map.of(
                "v", 1,
                "type", "DIAMOND_HELMET",
                "amount", 1));

        String payload = serializer.serialize(itemStack);

        assertTrue(payload.startsWith("yaml:"));
        assertTrue(payload.contains("item:"));
    }

    @Test
    void exposesCurrentStorageFormatId() {
        ItemStackDataSerializer serializer = new ItemStackDataSerializer();
        assertEquals("yaml-v1", serializer.storageFormatId());
    }

    @Test
    void throwsOnInvalidBinaryPayload() {
        ItemStackDataSerializer serializer = new ItemStackDataSerializer();
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize("b64:not-base64!"));
    }
}
