package dev.rique.ruinedwardrobe.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertFalse(pluginConfig.sessionSettings().preloadProfileOnJoin());
        assertFalse(pluginConfig.sessionSettings().touchPlayerRowOnJoin());
        assertEquals(40L, pluginConfig.armorSyncSettings().scanIntervalTicks());
        assertEquals(500, pluginConfig.armorSyncSettings().scanBatchSize());
        assertEquals(10L, pluginConfig.armorSyncSettings().shutdownFlushTimeoutSeconds());
        assertFalse(pluginConfig.deathSettings().keepWardrobeOnDeath());
        assertEquals(120L, pluginConfig.healthLogSeconds());
        assertTrue(pluginConfig.auditSettings().enabled());
        assertFalse(pluginConfig.auditSettings().logSuccessfulSyncs());
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
        assertEquals("ruinedwardrobe", pluginConfig.storageSettings().mysqlDatabase());
        assertEquals("wardrobe", pluginConfig.storageSettings().mysqlUsername());
        assertEquals("useUnicode=true&characterEncoding=utf8&useSSL=false", pluginConfig.storageSettings().mysqlParams());
        assertEquals(3, pluginConfig.storageSettings().minIdle());
    }

    @Test
    void parsesPerformanceSettingsAndClampsUnsafeValues() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("performance.session.preload-profile-on-join", true);
        configuration.set("performance.session.touch-player-row-on-join", true);
        configuration.set("performance.armor-sync.enabled", false);
        configuration.set("performance.armor-sync.scan-interval-ticks", 1);
        configuration.set("performance.armor-sync.scan-batch-size", 1);
        configuration.set("performance.armor-sync.shutdown-flush-timeout-seconds", 0);
        configuration.set("performance.health.enabled", false);
        configuration.set("performance.health.log-seconds", 1);
        configuration.set("audit.enabled", false);
        configuration.set("audit.queue-size", 1);
        configuration.set("audit.log-successful-syncs", true);
        configuration.set("audit.log-blocked-actions", false);
        configuration.set("audit.include-item-summaries", false);

        PluginConfig pluginConfig = PluginConfig.from(configuration);

        assertTrue(pluginConfig.sessionSettings().preloadProfileOnJoin());
        assertTrue(pluginConfig.sessionSettings().touchPlayerRowOnJoin());
        assertFalse(pluginConfig.armorSyncSettings().enabled());
        assertEquals(20L, pluginConfig.armorSyncSettings().scanIntervalTicks());
        assertEquals(25, pluginConfig.armorSyncSettings().scanBatchSize());
        assertEquals(1L, pluginConfig.armorSyncSettings().shutdownFlushTimeoutSeconds());
        assertFalse(pluginConfig.healthSettings().enabled());
        assertEquals(10L, pluginConfig.healthLogSeconds());
        assertFalse(pluginConfig.auditSettings().enabled());
        assertEquals(256, pluginConfig.auditSettings().queueSize());
        assertTrue(pluginConfig.auditSettings().logSuccessfulSyncs());
        assertFalse(pluginConfig.auditSettings().logBlockedActions());
        assertFalse(pluginConfig.auditSettings().includeItemSummaries());
    }

    @Test
    void parsesDeathSettings() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("death.keep-wardrobe-on-death", true);

        PluginConfig pluginConfig = PluginConfig.from(configuration);

        assertTrue(pluginConfig.deathSettings().keepWardrobeOnDeath());
    }
}
