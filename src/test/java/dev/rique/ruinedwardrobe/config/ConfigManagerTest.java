package dev.rique.ruinedwardrobe.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigManagerTest {

    @Test
    void configUpgradePreservesUserValuesWithoutRestoringOldSchemaVersion() {
        YamlConfiguration previous = new YamlConfiguration();
        previous.set("config-version", 5);
        previous.set("storage.type", "MYSQL");
        previous.set("storage.mysql.password", "server-secret");
        previous.set("restrictions.placeholder-rules.custom.disallow-values", List.of("blocked"));

        YamlConfiguration upgraded = new YamlConfiguration();
        upgraded.set("config-version", 6);
        upgraded.set("storage.type", "SQLITE");
        upgraded.set("storage.mysql.password", "change_me");

        ConfigManager.preserveUserValues(previous, upgraded);

        assertEquals(6, upgraded.getInt("config-version"));
        assertEquals("MYSQL", upgraded.getString("storage.type"));
        assertEquals("server-secret", upgraded.getString("storage.mysql.password"));
        assertEquals(
                List.of("blocked"),
                upgraded.getStringList("restrictions.placeholder-rules.custom.disallow-values"));
    }
}
