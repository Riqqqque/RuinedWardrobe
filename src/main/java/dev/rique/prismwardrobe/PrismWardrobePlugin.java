package dev.rique.prismwardrobe;

import dev.rique.prismwardrobe.api.WardrobeApi;
import dev.rique.prismwardrobe.command.WardrobeCommand;
import dev.rique.prismwardrobe.config.ConfigManager;
import dev.rique.prismwardrobe.config.PluginConfig;
import dev.rique.prismwardrobe.core.HealthMonitor;
import dev.rique.prismwardrobe.core.WardrobeArmorBindingService;
import dev.rique.prismwardrobe.core.WardrobeArmorSyncService;
import dev.rique.prismwardrobe.core.PermissionTierResolver;
import dev.rique.prismwardrobe.core.RestrictionServiceImpl;
import dev.rique.prismwardrobe.core.SlotLimitServiceImpl;
import dev.rique.prismwardrobe.core.VersionSyncService;
import dev.rique.prismwardrobe.core.WardrobeApiImpl;
import dev.rique.prismwardrobe.core.WardrobeServiceImpl;
import dev.rique.prismwardrobe.gui.WardrobeGuiController;
import dev.rique.prismwardrobe.gui.WardrobeGuiListener;
import dev.rique.prismwardrobe.integration.combat.CombatProviderFactory;
import dev.rique.prismwardrobe.integration.combat.CombatTagProvider;
import dev.rique.prismwardrobe.integration.placeholder.PrismWardrobeExpansion;
import dev.rique.prismwardrobe.integration.vault.VaultHook;
import dev.rique.prismwardrobe.lang.LanguageRegistry;
import dev.rique.prismwardrobe.lang.MessageService;
import dev.rique.prismwardrobe.listener.PlayerSessionListener;
import dev.rique.prismwardrobe.listener.WardrobeArmorProtectionListener;
import dev.rique.prismwardrobe.scheduler.SchedulerAdapter;
import dev.rique.prismwardrobe.scheduler.SchedulerFactory;
import dev.rique.prismwardrobe.storage.DatabaseManager;
import dev.rique.prismwardrobe.storage.MigrationService;
import dev.rique.prismwardrobe.storage.SqlWardrobeRepository;
import dev.rique.prismwardrobe.storage.WardrobeRepository;
import dev.rique.prismwardrobe.util.ItemStackDataSerializer;
import dev.rique.prismwardrobe.cache.WardrobeCache;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class PrismWardrobePlugin extends JavaPlugin {

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
    private WardrobeArmorBindingService armorBindingService;
    private WardrobeArmorSyncService armorSyncService;
    private WardrobeGuiController guiController;
    private VersionSyncService versionSyncService;
    private HealthMonitor healthMonitor;
    private MigrationService migrationService;
    private PrismWardrobeExpansion placeholderExpansion;
    private VaultHook vaultHook;

    @Override
    public void onEnable() {
        try {
            bootstrap();
            getLogger().info("PrismWardrobe enabled successfully.");
        } catch (Exception ex) {
            getLogger().severe("Failed to enable PrismWardrobe: " + ex.getMessage());
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
        databaseManager = new DatabaseManager(pluginConfig);
        databaseManager.initialize(getDataFolder());

        repository = new SqlWardrobeRepository(
                this,
                databaseManager,
                new ItemStackDataSerializer()
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
                pluginConfig
        );
        armorSyncService = new WardrobeArmorSyncService(this, schedulerAdapter, repository, wardrobeService, armorBindingService);

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

        versionSyncService.start();
        healthMonitor.start();
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
            placeholderExpansion = new PrismWardrobeExpansion(this, wardrobeService, slotLimitService);
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
        Bukkit.getPluginManager().registerEvents(new PlayerSessionListener(wardrobeService, repository), this);
        Bukkit.getPluginManager().registerEvents(
                new WardrobeArmorProtectionListener(
                        armorBindingService,
                        armorSyncService,
                        messageService,
                        configManager.pluginConfig().antiDupeSettings().strictContainerLockEnabled()),
                this);
    }

    private void validateConfiguration(PluginConfig pluginConfig) {
        if (pluginConfig.defaultSlots() > pluginConfig.maxSlotsCap()) {
            throw new IllegalStateException("wardrobe.default-slots must be <= wardrobe.max-slots-cap");
        }
        for (int slot : configManager.guiConfig().slotDisplayIndices()) {
            if (slot < 0 || slot >= configManager.guiConfig().rows() * 9) {
                getLogger().warning("gui.yml has out-of-bounds slot-display-index " + slot + " for rows=" + configManager.guiConfig().rows() + ". This slot will be ignored.");
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
        if (schedulerAdapter != null) {
            schedulerAdapter.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
    }
}
