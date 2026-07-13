package dev.rique.ruinedwardrobe.core;

import dev.rique.ruinedwardrobe.util.ArmorPieceMatcher;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WardrobeArmorBindingServiceTest {

    @Test
    void detachedLastPieceKeepsItsSourceSlotForPersistence() {
        UUID owner = UUID.randomUUID();
        WardrobeArmorBindingService service = service();
        BoundItem detached = boundItem(owner, 4, "helmet");
        PlayerInventory inventory = mock(PlayerInventory.class);
        Player player = player(owner, inventory);
        ItemStack[] storage = {detached.bound()};

        service.setActiveSlot(owner, 4);
        when(inventory.getStorageContents()).thenReturn(storage);

        WardrobeArmorBindingService.SanitizationResult result = service.sanitizeBoundItems(player);

        assertEquals(1, result.detachedUnbound());
        assertEquals(4, service.getActiveSlot(owner));
        assertSame(detached.unbound(), storage[0]);
        verify(inventory).setStorageContents(storage);
    }

    @Test
    void sameOwnerCopyOutsideArmorIsDeletedWhenCanonicalPieceExists() {
        UUID owner = UUID.randomUUID();
        WardrobeArmorBindingService service = service();
        BoundItem canonical = boundItem(owner, 4, "helmet");
        BoundItem duplicate = boundItem(owner, 4, "helmet");
        PlayerInventory inventory = mock(PlayerInventory.class);
        Player player = player(owner, inventory);
        ItemStack[] storage = {duplicate.bound()};

        when(inventory.getHelmet()).thenReturn(canonical.bound());
        when(inventory.getStorageContents()).thenReturn(storage);

        try (MockedStatic<ArmorPieceMatcher> matcher = mockStatic(ArmorPieceMatcher.class)) {
            matcher.when(() -> ArmorPieceMatcher.matchesWardrobePiece("helmet", canonical.bound().getType()))
                    .thenReturn(true);

            WardrobeArmorBindingService.SanitizationResult result = service.sanitizeBoundItems(player);

            assertEquals(1, result.duplicatesRemoved());
            assertEquals(4, service.getActiveSlot(owner));
            assertNull(storage[0]);
            verify(inventory).setStorageContents(storage);
        }
    }

    private WardrobeArmorBindingService service() {
        PluginMeta pluginMeta = mock(PluginMeta.class);
        when(pluginMeta.getName()).thenReturn("RuinedWardrobe");
        JavaPlugin plugin = mock(JavaPlugin.class, invocation -> switch (invocation.getMethod().getName()) {
            case "namespace" -> "ruinedwardrobe";
            case "getName" -> "RuinedWardrobe";
            case "getPluginMeta" -> pluginMeta;
            default -> Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        return new WardrobeArmorBindingService(plugin);
    }

    private Player player(UUID owner, PlayerInventory inventory) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(owner);
        when(player.getInventory()).thenReturn(inventory);
        return player;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private BoundItem boundItem(UUID owner, int slot, String piece) {
        ItemStack bound = mock(ItemStack.class);
        ItemStack unbound = mock(ItemStack.class);
        ItemMeta boundMeta = mock(ItemMeta.class);
        ItemMeta unboundMeta = mock(ItemMeta.class);
        PersistentDataContainer boundPdc = mock(PersistentDataContainer.class);
        PersistentDataContainer unboundPdc = mock(PersistentDataContainer.class);

        Material material = mock(Material.class);
        when(material.isAir()).thenReturn(false);
        when(bound.getType()).thenReturn(material);
        when(bound.getItemMeta()).thenReturn(boundMeta);
        when(boundMeta.getPersistentDataContainer()).thenReturn(boundPdc);
        when(bound.clone()).thenReturn(unbound);
        when(unbound.getType()).thenReturn(material);
        when(unbound.getItemMeta()).thenReturn(unboundMeta);
        when(unboundMeta.getPersistentDataContainer()).thenReturn(unboundPdc);

        when(boundPdc.has(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                .thenAnswer(invocation -> "bound_owner".equals(invocation.<NamespacedKey>getArgument(0).getKey()));
        when(boundPdc.has(any(NamespacedKey.class), eq(PersistentDataType.INTEGER)))
                .thenAnswer(invocation -> "bound_slot".equals(invocation.<NamespacedKey>getArgument(0).getKey()));
        when(boundPdc.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                .thenAnswer(invocation -> switch (invocation.<NamespacedKey>getArgument(0).getKey()) {
                    case "bound_owner" -> owner.toString();
                    case "bound_piece" -> piece;
                    default -> null;
                });
        when(boundPdc.get(any(NamespacedKey.class), eq(PersistentDataType.INTEGER))).thenReturn(slot);

        return new BoundItem(bound, unbound);
    }

    private record BoundItem(ItemStack bound, ItemStack unbound) {
    }
}
