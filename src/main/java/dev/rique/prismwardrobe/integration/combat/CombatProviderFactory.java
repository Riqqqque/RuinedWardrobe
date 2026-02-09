package dev.rique.prismwardrobe.integration.combat;

import dev.rique.prismwardrobe.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class CombatProviderFactory {

    private CombatProviderFactory() {
    }

    public static CombatTagProvider create(PluginConfig pluginConfig) {
        if (!pluginConfig.integrationSettings().combatEnabled()) {
            return new NoOpCombatTagProvider();
        }
        Plugin combatLogX = Bukkit.getPluginManager().getPlugin("CombatLogX");
        if (combatLogX != null && combatLogX.isEnabled()) {
            return new CombatLogXTagProvider();
        }
        return new NoOpCombatTagProvider();
    }
}

