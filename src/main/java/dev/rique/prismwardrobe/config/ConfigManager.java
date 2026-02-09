package dev.rique.prismwardrobe.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class ConfigManager {

    private final JavaPlugin plugin;
    private PluginConfig pluginConfig;
    private GuiConfig guiConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        pluginConfig = PluginConfig.from(plugin.getConfig());

        File guiFile = new File(plugin.getDataFolder(), "gui.yml");
        if (!guiFile.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        guiConfig = GuiConfig.from(YamlConfiguration.loadConfiguration(guiFile));
    }

    public void reload() {
        plugin.reloadConfig();
        load();
    }

    public PluginConfig pluginConfig() {
        return pluginConfig;
    }

    public GuiConfig guiConfig() {
        return guiConfig;
    }
}

