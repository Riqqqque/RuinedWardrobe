package dev.rique.prismwardrobe.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemStackDataSerializerTest {

    @Test
    void returnsNullForBlankPayloads() throws Exception {
        ItemStackDataSerializer serializer = new ItemStackDataSerializer();
        assertNull(serializer.fromJson(null));
        assertNull(serializer.fromJson(""));
    }

    @Test
    void throwsOnUnsupportedLegacyPayload() {
        ItemStackDataSerializer serializer = new ItemStackDataSerializer();
        assertThrows(Exception.class, () -> serializer.fromJson("{invalid json"));
    }

    @Test
    void toJsonReturnsNullForNullItems() {
        ItemStackDataSerializer serializer = new ItemStackDataSerializer();
        assertNull(serializer.toJson(null));
    }

    @Test
    void throwsOnInvalidBinaryPayload() {
        ItemStackDataSerializer serializer = new ItemStackDataSerializer();
        assertThrows(Exception.class, () -> serializer.fromJson("b64:not-base64!"));
    }
}
