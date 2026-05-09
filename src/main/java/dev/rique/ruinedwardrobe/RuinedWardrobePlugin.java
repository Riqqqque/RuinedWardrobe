package dev.rique.ruinedwardrobe;

import dev.rique.ruinedwardrobe.api.WardrobeApi;
import dev.rique.ruinedwardrobe.command.WardrobeCommand;
import dev.rique.ruinedwardrobe.config.ConfigManager;
import dev.rique.ruinedwardrobe.config.DatabaseType;
import dev.rique.ruinedwardrobe.config.PluginConfig;
import dev.rique.ruinedwardrobe.core.HealthMonitor;
import dev.rique.ruinedwardrobe.core.WardrobeAuditLogger;
import dev.rique.ruinedwardrobe.core.WardrobeArmorBindingService;
import dev.rique.ruinedwardrobe.core.WardrobeArmorSyncService;
import dev.rique.ruinedwardrobe.core.PermissionTierResolver;
import dev.rique.ruinedwardrobe.core.RestrictionServiceImpl;
import dev.rique.ruinedwardrobe.core.SlotLimitServiceImpl;
import dev.rique.ruinedwardrobe.core.VersionSyncService;
import dev.rique.ruinedwardrobe.core.WardrobeApiImpl;
import dev.rique.ruinedwardrobe.core.WardrobeServiceImpl;
import dev.rique.ruinedwardrobe.gui.WardrobeGuiController;
import dev.rique.ruinedwardrobe.gui.WardrobeGuiListener;
import dev.rique.ruinedwardrobe.integration.combat.CombatProviderFactory;
import dev.rique.ruinedwardrobe.integration.combat.CombatTagProvider;
import dev.rique.ruinedwardrobe.integration.placeholder.RuinedWardrobeExpansion;
import dev.rique.ruinedwardrobe.integration.vault.VaultHook;
import dev.rique.ruinedwardrobe.lang.LanguageRegistry;
import dev.rique.ruinedwardrobe.lang.MessageService;
import dev.rique.ruinedwardrobe.listener.PlayerSessionListener;
import dev.rique.ruinedwardrobe.listener.WardrobeArmorProtectionListener;
import dev.rique.ruinedwardrobe.scheduler.SchedulerAdapter;
import dev.rique.ruinedwardrobe.scheduler.SchedulerFactory;
import dev.rique.ruinedwardrobe.storage.DatabaseManager;
import dev.rique.ruinedwardrobe.storage.MigrationService;
import dev.rique.ruinedwardrobe.storage.SqlWardrobeRepository;
import dev.rique.ruinedwardrobe.storage.WardrobeRepository;
import dev.rique.ruinedwardrobe.util.ItemStackDataSerializer;
import dev.rique.ruinedwardrobe.cache.WardrobeCache;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class RuinedWardrobePlugin extends JavaPlugin {

    private ConfigManager configManager;
    private PluginConfig pluginConfig;
    private LanguageRegistry languageRegistry;
    private MessageService messageService;
    private SchedulerAdapter schedulerAdapter;
    private DatabaseManager databaseManager;
    private WardrobeRepository repository;
    private WardrobeCache cache;
    private SlotLimitServiceImpl slotLimitService;
    private RestrictionServiceImpl restrictionService;
    private WardrobeServiceImpl wardrobeService;
    private WardrobeAuditLogger auditLogger;
    private WardrobeArmorBindingService armorBindingService;
    private WardrobeArmorSyncService armorSyncService;
    private WardrobeGuiController guiController;
    private VersionSyncService versionSyncService;
    private HealthMonitor healthMonitor;
    private MigrationService migrationService;
    private RuinedWardrobeExpansion placeholderExpansion;
    private VaultHook vaultHook;

    @Override
    public void onEnable() {
        try {
            bootstrap();
            getLogger().info("RuinedWardrobe enabled successfully.");
        } catch (Exception ex) {
            getLogger().severe("Failed to enable RuinedWardrobe: " + ex.getMessage());
            ex.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        shutdown();
    }

    private void bootstrap() {
        configManager = new ConfigManager(this);
        configManager.load();
        pluginConfig = configManager.pluginConfig();

        validateConfiguration(pluginConfig);

        languageRegistry = new LanguageRegistry(this);
        languageRegistry.load(pluginConfig.language());
        messageService = new MessageService(
                languageRegistry,
                pluginConfig.messageFormatMode(),
                pluginConfig.integrationSettings().placeholderApiEnabled()
        );

        schedulerAdapter = SchedulerFactory.create(this);
        auditLogger = new WardrobeAuditLogger(this, pluginConfig.auditSettings());
        auditLogger.start();

        databaseManager = new DatabaseManager(pluginConfig);
        databaseManager.initialize(getDataFolder());

        repository = new SqlWardrobeRepository(
                this,
                databaseManager,
                new ItemStackDataSerializer(),
                auditLogger
        );
        repository.initializeSchema().join();

        cache = new WardrobeCache(pluginConfig.cacheSettings());
        slotLimitService = new SlotLimitServiceImpl(repository, pluginConfig, new PermissionTierResolver());
        CombatTagProvider combatTagProvider = CombatProviderFactory.create(pluginConfig);
        restrictionService = new RestrictionServiceImpl(pluginConfig, combatTagProvider);
        armorBindingService = new WardrobeArmorBindingService(this);
        wardrobeService = new WardrobeServiceImpl(
                schedulerAdapter,
                repository,
                cache,
                slotLimitService,
                restrictionService,
                armorBindingService,
                pluginConfig,
                auditLogger
        );
        armorSyncService = new WardrobeArmorSyncService(
                this,
                schedulerAdapter,
                repository,
                wardrobeService,
                armorBindingService,
                pluginConfig.armorSyncSettings(),
                pluginConfig.deathSettings(),
                auditLogger);

        guiController = new WardrobeGuiController(
                schedulerAdapter,
                configManager.guiConfig(),
                pluginConfig,
                messageService,
                wardrobeService,
                slotLimitService,
                armorBindingService
        );
        migrationService = new MigrationService(this, pluginConfig, repository);
        versionSyncService = new VersionSyncService(this, schedulerAdapter, repository, wardrobeService, pluginConfig.syncSettings());
        healthMonitor = new HealthMonitor(this, schedulerAdapter, cache, databaseManager, versionSyncService, pluginConfig.healthLogSeconds());

        registerApi();
        registerListeners();
        registerCommands();
        initializeIntegrations(pluginConfig);

        if (pluginConfig.storageSettings().type() == DatabaseType.MYSQL) {
            versionSyncService.start();
        }
        if (pluginConfig.healthSettings().enabled()) {
            healthMonitor.start();
        }
        armorSyncService.start();

        getLogger().info("Capabilities: " + Map.of(
                "storage", pluginConfig.storageSettings().type(),
                "placeholderapi", pluginConfig.integrationSettings().placeholderApiEnabled() && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"),
                "vault", pluginConfig.integrationSettings().vaultEnabled() && Bukkit.getPluginManager().isPluginEnabled("Vault"),
                "combat-provider", combatTagProvider.name()
        ));
    }

    public void reloadPluginRuntime() {
        shutdown();
        HandlerList.unregisterAll(this);
        Bukkit.getServicesManager().unregisterAll(this);
        bootstrap();
    }

    private void initializeIntegrations(PluginConfig pluginConfig) {
        vaultHook = new VaultHook(this, pluginConfig);
        vaultHook.initialize();

        if (pluginConfig.integrationSettings().placeholderApiEnabled() && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderExpansion = new RuinedWardrobeExpansion(this, wardrobeService, slotLimitService);
            placeholderExpansion.register();
        }

        if (pluginConfig.metricsSettings().enabled() && pluginConfig.metricsSettings().pluginId() > 0) {
            new Metrics(this, pluginConfig.metricsSettings().pluginId());
        }
    }

    private void registerApi() {
        WardrobeApi api = new WardrobeApiImpl(wardrobeService, restrictionService, slotLimitService);
        Bukkit.getServicesManager().register(WardrobeApi.class, api, this, ServicePriority.Normal);
    }

    private void registerCommands() {
        PluginCommand command = getCommand("wardrobe");
        if (command == null) {
            throw new IllegalStateException("Command 'wardrobe' missing from plugin.yml");
        }
        WardrobeCommand wardrobeCommand = new WardrobeCommand(
                this,
                languageRegistry,
                messageService,
                wardrobeService,
                slotLimitService,
                guiController,
                migrationService,
                databaseManager,
                cache,
                versionSyncService,
                pluginConfig,
                schedulerAdapter,
                this::reloadPluginRuntime
        );
        command.setExecutor(wardrobeCommand);
        command.setTabCompleter(wardrobeCommand);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new WardrobeGuiListener(guiController), this);
        Bukkit.getPluginManager().registerEvents(new PlayerSessionListener(
                wardrobeService,
                repository,
                pluginConfig.sessionSettings()), this);
        Bukkit.getPluginManager().registerEvents(
                new WardrobeArmorProtectionListener(
                        armorBindingService,
                        armorSyncService,
                        auditLogger,
                        messageService,
                        configManager.pluginConfig().antiDupeSettings().strictContainerLockEnabled()),
                this);
    }

    private void validateConfiguration(PluginConfig pluginConfig) {
        if (pluginConfig.defaultSlots() > pluginConfig.maxSlotsCap()) {
            throw new IllegalStateException("wardrobe.default-slots must be <= wardrobe.max-slots-cap");
        }
        for (int slot : configManager.guiConfig().slotDisplayIndices()) {
            if (slot < 0 || slot >= 9) {
                getLogger().warning("gui.yml has out-of-bounds slot-display-index " + slot + ". Valid column indexes are 0 through 8.");
            }
        }
    }

    private void shutdownRuntimeOnly() {
        if (healthMonitor != null) {
            healthMonitor.stop();
        }
        if (versionSyncService != null) {
            versionSyncService.stop();
        }
        if (armorSyncService != null) {
            armorSyncService.stop();
        }
        if (placeholderExpansion != null) {
            try {
                placeholderExpansion.unregister();
            } catch (Exception ignored) {
            }
            placeholderExpansion = null;
        }
    }

    private void shutdown() {
        shutdownRuntimeOnly();
        if (armorSyncService != null) {
            try {
                armorSyncService.flushOnlinePlayers()
                        .orTimeout(pluginConfig.armorSyncSettings().shutdownFlushTimeoutSeconds(), TimeUnit.SECONDS)
                        .join();
            } catch (Exception ex) {
                getLogger().warning("Failed to flush wardrobe state during shutdown: " + ex.getMessage());
            }
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        if (schedulerAdapter != null) {
            schedulerAdapter.shutdown();
        }
        if (auditLogger != null) {
            auditLogger.stop();
        }
    }
}
