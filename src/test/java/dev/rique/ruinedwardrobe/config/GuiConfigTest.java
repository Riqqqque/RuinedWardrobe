package dev.rique.ruinedwardrobe.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiConfigTest {

    @Test
    void invalidCompactLayoutFallsBackToSixDistinctRows() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("layout.rows", 2);
        configuration.set("layout.armor-layout.helmet-row", 0);
        configuration.set("layout.armor-layout.chestplate-row", 0);
        configuration.set("layout.armor-layout.leggings-row", 0);
        configuration.set("layout.armor-layout.boots-row", 0);
        configuration.set("layout.equip-buttons.row", 0);

        GuiConfig config = GuiConfig.from(configuration);

        assertEquals(6, config.rows());
        assertEquals(Set.of(0, 1, 2, 3), Set.of(
                config.helmetRow(),
                config.chestplateRow(),
                config.leggingsRow(),
                config.bootsRow()));
        assertEquals(4, config.equipButtonsRow());
    }
}
