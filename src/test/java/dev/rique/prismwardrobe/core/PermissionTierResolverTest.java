package dev.rique.prismwardrobe.core;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class PermissionTierResolverTest {

    @Test
    void resolvesHighestTierPermission() {
        Player player = Mockito.mock(Player.class);
        PermissionAttachmentInfo tierSix = new PermissionAttachmentInfo(player, "prismwardrobe.slots.6", null, true);
        PermissionAttachmentInfo tierNine = new PermissionAttachmentInfo(player, "prismwardrobe.slots.9", null, true);
        PermissionAttachmentInfo unrelated = new PermissionAttachmentInfo(player, "example.permission", null, true);

        when(player.getEffectivePermissions()).thenReturn(Set.of(tierSix, tierNine, unrelated));

        PermissionTierResolver resolver = new PermissionTierResolver();
        int result = resolver.resolve(player, 3);
        assertEquals(9, result);
    }

    @Test
    void fallsBackToDefaultWhenNoTierPermission() {
        Player player = Mockito.mock(Player.class);
        when(player.getEffectivePermissions()).thenReturn(Set.of());

        PermissionTierResolver resolver = new PermissionTierResolver();
        int result = resolver.resolve(player, 3);
        assertEquals(3, result);
    }
}

