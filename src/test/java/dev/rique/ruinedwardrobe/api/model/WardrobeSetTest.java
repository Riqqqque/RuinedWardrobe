package dev.rique.ruinedwardrobe.api.model;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WardrobeSetTest {

    @Test
    void armorAccessorsReturnDefensiveCopies() {
        ItemStack helmet = mock(ItemStack.class);
        ItemStack storedHelmet = mock(ItemStack.class);
        ItemStack firstRead = mock(ItemStack.class);
        ItemStack secondRead = mock(ItemStack.class);
        when(helmet.clone()).thenReturn(storedHelmet);
        when(storedHelmet.clone()).thenReturn(firstRead, secondRead);

        WardrobeSet set = new WardrobeSet(1, "Main", false, helmet, null, null, null);

        assertSame(firstRead, set.helmet());
        assertSame(secondRead, set.helmet());
    }
}
