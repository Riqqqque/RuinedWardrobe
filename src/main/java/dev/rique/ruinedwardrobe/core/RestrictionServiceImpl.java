package dev.rique.ruinedwardrobe.core;

import dev.rique.ruinedwardrobe.api.RestrictionService;
import dev.rique.ruinedwardrobe.api.model.RestrictionDecision;
import dev.rique.ruinedwardrobe.api.model.ResultCode;
import dev.rique.ruinedwardrobe.config.PluginConfig;
import dev.rique.ruinedwardrobe.integration.combat.CombatTagProvider;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RestrictionServiceImpl implements RestrictionService {

    private final PluginConfig pluginConfig;
    private final CombatTagProvider combatTagProvider;
    private final boolean placeholderApiAvailable;

    public RestrictionServiceImpl(PluginConfig pluginConfig, CombatTagProvider combatTagProvider) {
        this.pluginConfig = pluginConfig;
        this.combatTagProvider = combatTagProvider;
        this.placeholderApiAvailable = pluginConfig.integrationSettings().placeholderApiEnabled()
                && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    @Override
    public RestrictionDecision canEquip(Player player) {
        PluginConfig.RestrictionSettings restrictions = pluginConfig.restrictionSettings();
        if (!restrictions.enabled()) {
            return RestrictionDecision.allow();
        }

        String world = player.getWorld().getName().toLowerCase(Locale.ROOT);
        Set<String> allowed = restrictions.allowedWorlds();
        if (!allowed.isEmpty() && !allowed.contains(world)) {
            return new RestrictionDecision(
                    false,
                    ResultCode.DENIED_RESTRICTION_WORLD,
                    "restriction.world-not-allowed",
                    Map.of("world", player.getWorld().getName())
            );
        }
        if (restrictions.blockedWorlds().contains(world)) {
            return new RestrictionDecision(
                    false,
                    ResultCode.DENIED_RESTRICTION_WORLD,
                    "restriction.world-blocked",
                    Map.of("world", player.getWorld().getName())
            );
        }

        if (restrictions.blockedGamemodes().contains(player.getGameMode())) {
            return new RestrictionDecision(
                    false,
                    ResultCode.DENIED_RESTRICTION_GAMEMODE,
                    "restriction.gamemode-blocked",
                    Map.of("gamemode", player.getGameMode().name())
            );
        }

        if (restrictions.combatCheckEnabled() && combatTagProvider.isInCombat(player)) {
            return new RestrictionDecision(
                    false,
                    ResultCode.DENIED_RESTRICTION_COMBAT,
                    "restriction.in-combat",
                    Map.of("provider", combatTagProvider.name())
            );
        }

        if (placeholderApiAvailable) {
            for (PluginConfig.PlaceholderRule rule : restrictions.placeholderRules()) {
                String value = PlaceholderAPI.setPlaceholders(player, rule.placeholder());
                if (value == null) {
                    continue;
                }
                if (rule.disallowValues().contains(value.toLowerCase(Locale.ROOT))) {
                    return new RestrictionDecision(
                            false,
                            ResultCode.DENIED_RESTRICTION_PLACEHOLDER,
                            rule.reasonKey(),
                            Map.of("placeholder", rule.placeholder(), "value", value)
                    );
                }
            }
        }

        return RestrictionDecision.allow();
    }
}

