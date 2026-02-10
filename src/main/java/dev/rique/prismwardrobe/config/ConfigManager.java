package dev.rique.prismwardrobe.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class ConfigManager {

    private static final int CONFIG_VERSION = 1;
    private static final int GUI_CONFIG_VERSION = 1;

    private final JavaPlugin plugin;
    private PluginConfig pluginConfig;
    private GuiConfig guiConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        validateVersion(plugin.getConfig().getInt("config-version", -1), CONFIG_VERSION, "config.yml");
        pluginConfig = PluginConfig.from(plugin.getConfig());

        File guiFile = new File(plugin.getDataFolder(), "gui.yml");
        if (!guiFile.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        YamlConfiguration guiYaml = YamlConfiguration.loadConfiguration(guiFile);
        validateVersion(guiYaml.getInt("config-version", -1), GUI_CONFIG_VERSION, "gui.yml");
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

    private void validateVersion(int currentVersion, int expectedVersion, String fileName) {
        if (currentVersion == expectedVersion) {
            return;
        }
        throw new IllegalStateException(fileName + " has unsupported config-version " + currentVersion
                + " (expected " + expectedVersion + "). Regenerate this file from the latest plugin release.");
    }
}
