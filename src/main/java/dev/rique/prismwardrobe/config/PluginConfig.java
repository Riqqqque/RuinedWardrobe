package dev.rique.prismwardrobe.config;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PluginConfig {

    private final int defaultSlots;
    private final int maxSlotsCap;
    private final int maxPages;
    private final long equipCooldownMillis;
    private final boolean debug;
    private final String language;
    private final MessageFormatMode messageFormatMode;
    private final StorageSettings storageSettings;
    private final CacheSettings cacheSettings;
    private final SyncSettings syncSettings;
    private final RestrictionSettings restrictionSettings;
    private final MetricsSettings metricsSettings;
    private final IntegrationSettings integrationSettings;
    private final GuiBehaviorSettings guiBehaviorSettings;
    private final AntiDupeSettings antiDupeSettings;
    private final DbExecutionSettings dbExecutionSettings;
    private final long healthLogSeconds;

    public PluginConfig(
            int defaultSlots,
            int maxSlotsCap,
            int maxPages,
            long equipCooldownMillis,
            boolean debug,
            String language,
            MessageFormatMode messageFormatMode,
            StorageSettings storageSettings,
            CacheSettings cacheSettings,
            SyncSettings syncSettings,
            RestrictionSettings restrictionSettings,
            MetricsSettings metricsSettings,
            IntegrationSettings integrationSettings,
            GuiBehaviorSettings guiBehaviorSettings,
            AntiDupeSettings antiDupeSettings,
            DbExecutionSettings dbExecutionSettings,
            long healthLogSeconds) {
        this.defaultSlots = defaultSlots;
        this.maxSlotsCap = maxSlotsCap;
        this.maxPages = maxPages;
        this.equipCooldownMillis = equipCooldownMillis;
        this.debug = debug;
        this.language = language;
        this.messageFormatMode = messageFormatMode;
        this.storageSettings = storageSettings;
        this.cacheSettings = cacheSettings;
        this.syncSettings = syncSettings;
        this.restrictionSettings = restrictionSettings;
        this.metricsSettings = metricsSettings;
        this.integrationSettings = integrationSettings;
        this.guiBehaviorSettings = guiBehaviorSettings;
        this.antiDupeSettings = antiDupeSettings;
        this.dbExecutionSettings = dbExecutionSettings;
        this.healthLogSeconds = healthLogSeconds;
    }

    public static PluginConfig from(FileConfiguration config) {
        int defaultSlots = Math.max(1, config.getInt("wardrobe.default-slots", 8));
        int maxSlotsCap = Math.max(defaultSlots, config.getInt("wardrobe.max-slots-cap", 54));
        int maxPages = Math.max(1, Math.min(10, config.getInt("wardrobe.max-pages", 2)));
        long equipCooldownMillis = Math.max(0, config.getLong("wardrobe.equip-cooldown-seconds", 3)) * 1000L;
        boolean debug = config.getBoolean("debug.enabled", false);
        String language = config.getString("language.active", "en_US");
        MessageFormatMode formatMode = MessageFormatMode.parse(config.getString("messages.format-mode", "BOTH"));

        ConfigurationSection storage = config.getConfigurationSection("storage");
        DatabaseType databaseType = storage == null ? DatabaseType.SQLITE
                : DatabaseType.parse(storage.getString("type", "SQLITE"));
        String sqlitePath = storage == null ? "data/wardrobe.db" : storage.getString("sqlite.file", "data/wardrobe.db");
        String mysqlHost = storage == null ? "127.0.0.1" : storage.getString("mysql.host", "127.0.0.1");
        int mysqlPort = storage == null ? 3306 : storage.getInt("mysql.port", 3306);
        String mysqlDatabase = storage == null ? "prismwardrobe" : storage.getString("mysql.database", "prismwardrobe");
        String mysqlUser = storage == null ? "root" : storage.getString("mysql.username", "root");
        String mysqlPassword = storage == null ? "" : storage.getString("mysql.password", "");
        String mysqlParams = storage == null ? "useUnicode=true&characterEncoding=utf8&useSSL=false"
                : storage.getString("mysql.params", "useUnicode=true&characterEncoding=utf8&useSSL=false");
        int maxPoolSize = storage == null ? 10 : Math.max(2, storage.getInt("pool.max-size", 10));
        int minIdle = storage == null ? 2 : Math.max(1, storage.getInt("pool.min-idle", 2));
        long connectionTimeout = storage == null ? 10000L
                : Math.max(1000L, storage.getLong("pool.connection-timeout-ms", 10000L));
        StorageSettings storageSettings = new StorageSettings(databaseType, sqlitePath, mysqlHost, mysqlPort,
                mysqlDatabase, mysqlUser, mysqlPassword, mysqlParams, maxPoolSize, minIdle, connectionTimeout);

        ConfigurationSection cache = config.getConfigurationSection("performance.cache");
        CacheSettings cacheSettings = new CacheSettings(
                cache == null ? 100_000 : Math.max(1000L, cache.getLong("max-size", 100_000L)),
                cache == null ? 600L : Math.max(60L, cache.getLong("expire-after-seconds", 600L)));

        ConfigurationSection sync = config.getConfigurationSection("performance.sync");
        SyncSettings syncSettings = new SyncSettings(
                sync == null ? 5L : Math.max(1L, sync.getLong("poll-seconds", 5L)),
                sync == null ? 150 : Math.max(10, sync.getInt("batch-size", 150)));

        ConfigurationSection restrictions = config.getConfigurationSection("restrictions");
        RestrictionSettings restrictionSettings = parseRestrictionSettings(restrictions);

        ConfigurationSection metrics = config.getConfigurationSection("metrics");
        MetricsSettings metricsSettings = new MetricsSettings(
                metrics != null && metrics.getBoolean("enabled", false),
                metrics == null ? 0 : metrics.getInt("plugin-id", 0));

        ConfigurationSection integrations = config.getConfigurationSection("integrations");
        IntegrationSettings integrationSettings = new IntegrationSettings(
                integrations == null || integrations.getBoolean("placeholderapi.enabled", true),
                integrations == null || integrations.getBoolean("vault.enabled", true),
                integrations == null || integrations.getBoolean("combat.enabled", true));

        ConfigurationSection gui = config.getConfigurationSection("gui");
        GuiBehaviorSettings guiBehaviorSettings = new GuiBehaviorSettings(
                gui == null || gui.getBoolean("sounds.enabled", true),
                gui == null || gui.getBoolean("actionbar.enabled", true),
                gui != null && gui.getBoolean("titles.enabled", true),
                gui != null && gui.getBoolean("animations.enabled", true),
                gui == null ? 2 : Math.max(0, gui.getInt("animations.click-delay-ticks", 2)));

        ConfigurationSection antiDupe = config.getConfigurationSection("anti-dupe");
        AntiDupeSettings antiDupeSettings = new AntiDupeSettings(
                antiDupe != null && antiDupe.getBoolean("strict-container-lock", false));

        ConfigurationSection dbExecution = config.getConfigurationSection("performance.database");
        DbExecutionSettings dbExecutionSettings = new DbExecutionSettings(
                dbExecution == null ? 2048 : Math.max(128, dbExecution.getInt("write-queue-capacity", 2048)),
                dbExecution == null ? 3 : Math.max(0, dbExecution.getInt("write-retries", 3)),
                dbExecution == null ? 150L : Math.max(10L, dbExecution.getLong("retry-delay-ms", 150L)),
                dbExecution == null ? 4 : Math.max(1, dbExecution.getInt("worker-threads", 4)));

        long healthLogSeconds = Math.max(10L, config.getLong("performance.health.log-seconds", 120L));

        return new PluginConfig(
                defaultSlots,
                maxSlotsCap,
                maxPages,
                equipCooldownMillis,
                debug,
                language,
                formatMode,
                storageSettings,
                cacheSettings,
                syncSettings,
                restrictionSettings,
                metricsSettings,
                integrationSettings,
                guiBehaviorSettings,
                antiDupeSettings,
                dbExecutionSettings,
                healthLogSeconds);
    }

    private static RestrictionSettings parseRestrictionSettings(ConfigurationSection restrictions) {
        if (restrictions == null) {
            return RestrictionSettings.defaultSettings();
        }
        boolean enabled = restrictions.getBoolean("enabled", true);
        boolean combatCheck = restrictions.getBoolean("combat-check.enabled", true);
        Set<String> blockedWorlds = normalizeSet(restrictions.getStringList("blocked-worlds"));
        Set<String> allowedWorlds = normalizeSet(restrictions.getStringList("allowed-worlds"));
        Set<GameMode> blockedGamemodes = EnumSet.noneOf(GameMode.class);
        for (String mode : restrictions.getStringList("blocked-gamemodes")) {
            try {
                blockedGamemodes.add(GameMode.valueOf(mode.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        List<PlaceholderRule> placeholderRules = new ArrayList<>();
        ConfigurationSection placeholderSection = restrictions.getConfigurationSection("placeholder-rules");
        if (placeholderSection != null) {
            for (String key : placeholderSection.getKeys(false)) {
                ConfigurationSection entry = placeholderSection.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                String placeholder = entry.getString("placeholder", "");
                List<String> blockedValues = entry.getStringList("disallow-values");
                String reasonKey = entry.getString("reason-key", "restriction.placeholder");
                if (!placeholder.isBlank()) {
                    placeholderRules.add(new PlaceholderRule(placeholder, normalizeSet(blockedValues), reasonKey));
                }
            }
        }
        return new RestrictionSettings(enabled, combatCheck, blockedWorlds, allowedWorlds, blockedGamemodes,
                placeholderRules);
    }

    private static Set<String> normalizeSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new java.util.HashSet<>();
        for (String value : values) {
            normalized.add(value.toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    public int defaultSlots() {
        return defaultSlots;
    }

    public int maxSlotsCap() {
        return maxSlotsCap;
    }

    public int maxPages() {
        return maxPages;
    }

    public long equipCooldownMillis() {
        return equipCooldownMillis;
    }

    public boolean debug() {
        return debug;
    }

    public String language() {
        return language;
    }

    public MessageFormatMode messageFormatMode() {
        return messageFormatMode;
    }

    public StorageSettings storageSettings() {
        return storageSettings;
    }

    public CacheSettings cacheSettings() {
        return cacheSettings;
    }

    public SyncSettings syncSettings() {
        return syncSettings;
    }

    public RestrictionSettings restrictionSettings() {
        return restrictionSettings;
    }

    public MetricsSettings metricsSettings() {
        return metricsSettings;
    }

    public IntegrationSettings integrationSettings() {
        return integrationSettings;
    }

    public GuiBehaviorSettings guiBehaviorSettings() {
        return guiBehaviorSettings;
    }

    public AntiDupeSettings antiDupeSettings() {
        return antiDupeSettings;
    }

    public DbExecutionSettings dbExecutionSettings() {
        return dbExecutionSettings;
    }

    public PluginConfig withStorageType(DatabaseType databaseType) {
        StorageSettings storage = new StorageSettings(
                databaseType,
                storageSettings.sqliteFile(),
                storageSettings.mysqlHost(),
                storageSettings.mysqlPort(),
                storageSettings.mysqlDatabase(),
                storageSettings.mysqlUsername(),
                storageSettings.mysqlPassword(),
                storageSettings.mysqlParams(),
                storageSettings.maxPoolSize(),
                storageSettings.minIdle(),
                storageSettings.connectionTimeoutMs());
        return new PluginConfig(
                defaultSlots,
                maxSlotsCap,
                maxPages,
                equipCooldownMillis,
                debug,
                language,
                messageFormatMode,
                storage,
                cacheSettings,
                syncSettings,
                restrictionSettings,
                metricsSettings,
                integrationSettings,
                guiBehaviorSettings,
                antiDupeSettings,
                dbExecutionSettings,
                healthLogSeconds);
    }

    public long healthLogSeconds() {
        return healthLogSeconds;
    }

    public record StorageSettings(
            DatabaseType type,
            String sqliteFile,
            String mysqlHost,
            int mysqlPort,
            String mysqlDatabase,
            String mysqlUsername,
            String mysqlPassword,
            String mysqlParams,
            int maxPoolSize,
            int minIdle,
            long connectionTimeoutMs) {
    }

    public record CacheSettings(long maxSize, long expireAfterSeconds) {
    }

    public record SyncSettings(long pollSeconds, int batchSize) {
    }

    public record RestrictionSettings(
            boolean enabled,
            boolean combatCheckEnabled,
            Set<String> blockedWorlds,
            Set<String> allowedWorlds,
            Set<GameMode> blockedGamemodes,
            List<PlaceholderRule> placeholderRules) {
        public static RestrictionSettings defaultSettings() {
            return new RestrictionSettings(true, true, Collections.emptySet(), Collections.emptySet(),
                    EnumSet.noneOf(GameMode.class), Collections.emptyList());
        }
    }

    public record PlaceholderRule(String placeholder, Set<String> disallowValues, String reasonKey) {
    }

    public record MetricsSettings(boolean enabled, int pluginId) {
    }

    public record IntegrationSettings(boolean placeholderApiEnabled, boolean vaultEnabled, boolean combatEnabled) {
    }

    public record GuiBehaviorSettings(
            boolean soundsEnabled,
            boolean actionBarEnabled,
            boolean titlesEnabled,
            boolean animationsEnabled,
            int clickDelayTicks) {
    }

    public record AntiDupeSettings(boolean strictContainerLockEnabled) {
    }

    public record DbExecutionSettings(
            int writeQueueCapacity,
            int writeRetries,
            long retryDelayMs,
            int workerThreads) {
    }
}
