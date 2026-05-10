package dev.rique.ruinedwardrobe.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ConfigManager {

    private static final int CONFIG_VERSION = 6;
    private static final int GUI_CONFIG_VERSION = 2;
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JavaPlugin plugin;
    private PluginConfig pluginConfig;
    private GuiConfig guiConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        YamlConfiguration configYaml = loadVersionedConfig("config.yml", CONFIG_VERSION);
        pluginConfig = PluginConfig.from(configYaml);

        YamlConfiguration guiYaml = loadVersionedConfig("gui.yml", GUI_CONFIG_VERSION);
        guiConfig = GuiConfig.from(guiYaml);
    }

    public void reload() {
        load();
    }

    public PluginConfig pluginConfig() {
        return pluginConfig;
    }

    public GuiConfig guiConfig() {
        return guiConfig;
    }

    private YamlConfiguration loadVersionedConfig(String resourceName, int expectedVersion) {
        File file = new File(plugin.getDataFolder(), resourceName);
        ensureResourceExists(resourceName, file);

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        int currentVersion = configuration.getInt("config-version", -1);
        if (currentVersion != expectedVersion) {
            backupAndReplace(resourceName, file, currentVersion, expectedVersion);
            configuration = YamlConfiguration.loadConfiguration(file);
        }

        int reloadedVersion = configuration.getInt("config-version", -1);
        if (reloadedVersion != expectedVersion) {
            throw new IllegalStateException(resourceName + " has unsupported config-version " + reloadedVersion
                    + " after regeneration (expected " + expectedVersion + ").");
        }
        return configuration;
    }

    private void ensureResourceExists(String resourceName, File targetFile) {
        if (targetFile.exists()) {
            return;
        }
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create plugin data directory: " + parent.getAbsolutePath());
        }
        plugin.saveResource(resourceName, false);
    }

    private void backupAndReplace(String resourceName, File targetFile, int currentVersion, int expectedVersion) {
        try {
            File backupDirectory = new File(plugin.getDataFolder(), "backups");
            if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
                throw new IOException("Failed to create backup directory " + backupDirectory.getAbsolutePath());
            }

            String timestamp = LocalDateTime.now().format(BACKUP_STAMP);
            File backupFile = new File(backupDirectory, resourceName + ".v" + currentVersion + "." + timestamp + ".bak");
            Files.copy(targetFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.delete(targetFile.toPath());
            plugin.saveResource(resourceName, false);
            plugin.getLogger().warning(resourceName + " had config-version " + currentVersion
                    + " and was replaced with version " + expectedVersion
                    + ". The previous file was backed up to " + backupFile.getAbsolutePath());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to replace outdated " + resourceName + ": " + ex.getMessage(), ex);
        }
    }
}
