package dev.rique.ruinedwardrobe.integration.placeholder;

import dev.rique.ruinedwardrobe.api.model.WardrobeProfile;
import dev.rique.ruinedwardrobe.api.model.WardrobeSet;
import dev.rique.ruinedwardrobe.core.SlotLimitServiceImpl;
import dev.rique.ruinedwardrobe.core.WardrobeServiceImpl;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuinedWardrobeExpansionTest {

    @Test
    void profilePlaceholdersExposeEquippedSetAndPieces() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        WardrobeServiceImpl wardrobeService = mock(WardrobeServiceImpl.class);
        SlotLimitServiceImpl slotLimitService = mock(SlotLimitServiceImpl.class);
        RuinedWardrobeExpansion expansion = new RuinedWardrobeExpansion(
                mock(JavaPlugin.class),
                wardrobeService,
                slotLimitService);
        WardrobeSet set = new WardrobeSet(
                2,
                "Casual",
                false,
                item(Material.DIAMOND_HELMET),
                null,
                item(Material.NETHERITE_LEGGINGS),
                null);
        WardrobeProfile profile = new WardrobeProfile(playerId, 0, 2, 1L, Map.of(2, set));

        when(player.getUniqueId()).thenReturn(playerId);
        when(slotLimitService.getMaxSlots(player)).thenReturn(5);
        when(wardrobeService.getRemainingCooldownMillis(playerId)).thenReturn(0L);
        when(wardrobeService.cachedProfile(playerId)).thenReturn(Optional.of(profile));

        assertEquals("1", expansion.onPlaceholderRequest(player, "slots_used"));
        assertEquals("5", expansion.onPlaceholderRequest(player, "slots_unlocked"));
        assertEquals("4", expansion.onPlaceholderRequest(player, "slots_free"));
        assertEquals("true", expansion.onPlaceholderRequest(player, "cooldown_ready"));
        assertEquals("true", expansion.onPlaceholderRequest(player, "equipped"));
        assertEquals("2", expansion.onPlaceholderRequest(player, "equipped_slot"));
        assertEquals("Casual", expansion.onPlaceholderRequest(player, "equipped_name"));
        assertEquals("true", expansion.onPlaceholderRequest(player, "set_exists_2"));
        assertEquals("2", expansion.onPlaceholderRequest(player, "set_piece_count_2"));
        assertEquals("diamond_helmet", expansion.onPlaceholderRequest(player, "set_piece_2_helmet"));
        assertEquals("", expansion.onPlaceholderRequest(player, "set_piece_2_chestplate"));
    }

    @Test
    void nonProfilePlaceholdersWorkBeforeProfileIsCached() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        WardrobeServiceImpl wardrobeService = mock(WardrobeServiceImpl.class);
        SlotLimitServiceImpl slotLimitService = mock(SlotLimitServiceImpl.class);
        RuinedWardrobeExpansion expansion = new RuinedWardrobeExpansion(
                mock(JavaPlugin.class),
                wardrobeService,
                slotLimitService);

        when(player.getUniqueId()).thenReturn(playerId);
        when(slotLimitService.getMaxSlots(player)).thenReturn(7);
        when(wardrobeService.getRemainingCooldownMillis(playerId)).thenReturn(1250L);
        when(wardrobeService.cachedProfile(playerId)).thenReturn(Optional.empty());

        assertEquals("7", expansion.onPlaceholderRequest(player, "slots_max"));
        assertEquals("2", expansion.onPlaceholderRequest(player, "cooldown_remaining"));
        assertEquals("false", expansion.onPlaceholderRequest(player, "cooldown_ready"));
        assertEquals("", expansion.onPlaceholderRequest(player, "set_name_1"));
    }

    private ItemStack item(Material material) {
        ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.getType()).thenReturn(material);
        when(itemStack.clone()).thenReturn(itemStack);
        return itemStack;
    }
}
