package dev.rique.ruinedwardrobe.integration.vault;

import dev.rique.ruinedwardrobe.config.PluginConfig;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class VaultHook {

    private final JavaPlugin plugin;
    private final PluginConfig pluginConfig;
    private Economy economy;

    public VaultHook(JavaPlugin plugin, PluginConfig pluginConfig) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
    }

    public void initialize() {
        if (!pluginConfig.integrationSettings().vaultEnabled()) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            return;
        }
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration != null) {
            economy = registration.getProvider();
            plugin.getLogger().info("Vault hooked with provider: " + economy.getName());
        }
    }

    public boolean isAvailable() {
        return economy != null;
    }

    public Optional<Economy> economy() {
        return Optional.ofNullable(economy);
    }
}

