package dev.rique.fluxwardrobe.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PluginConfigTest {

    @Test
    void parsesExpectedDefaults() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("wardrobe.default-slots", 3);
        configuration.set("wardrobe.max-slots-cap", 54);
        configuration.set("wardrobe.equip-cooldown-seconds", 4);
        configuration.set("messages.format-mode", "BOTH");
        configuration.set("storage.type", "SQLITE");

        PluginConfig pluginConfig = PluginConfig.from(configuration);

        assertEquals(3, pluginConfig.defaultSlots());
        assertEquals(54, pluginConfig.maxSlotsCap());
        assertEquals(4000L, pluginConfig.equipCooldownMillis());
        assertEquals(MessageFormatMode.BOTH, pluginConfig.messageFormatMode());
        assertEquals(DatabaseType.SQLITE, pluginConfig.storageSettings().type());
        assertFalse(pluginConfig.antiDupeSettings().strictContainerLockEnabled());
    }

    @Test
    void allowsDisablingStrictContainerLock() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("anti-dupe.strict-container-lock", false);

        PluginConfig pluginConfig = PluginConfig.from(configuration);

        assertFalse(pluginConfig.antiDupeSettings().strictContainerLockEnabled());
    }

    @Test
    void sanitizesBlankStorageStringsAndClampsPoolIdle() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("storage.sqlite.file", "   ");
        configuration.set("storage.mysql.host", "   ");
        configuration.set("storage.mysql.database", "");
        configuration.set("storage.mysql.username", "  wardrobe  ");
        configuration.set("storage.mysql.params", "   ");
        configuration.set("storage.pool.max-size", 3);
        configuration.set("storage.pool.min-idle", 20);

        PluginConfig pluginConfig = PluginConfig.from(configuration);

        assertEquals("data/wardrobe.db", pluginConfig.storageSettings().sqliteFile());
        assertEquals("127.0.0.1", pluginConfig.storageSettings().mysqlHost());
        assertEquals("fluxwardrobe", pluginConfig.storageSettings().mysqlDatabase());
        assertEquals("wardrobe", pluginConfig.storageSettings().mysqlUsername());
        assertEquals("useUnicode=true&characterEncoding=utf8&useSSL=false", pluginConfig.storageSettings().mysqlParams());
        assertEquals(3, pluginConfig.storageSettings().minIdle());
    }
}
