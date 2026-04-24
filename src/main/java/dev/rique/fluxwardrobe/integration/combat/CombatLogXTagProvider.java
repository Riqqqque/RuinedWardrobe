package dev.rique.fluxwardrobe.integration.combat;

import org.bukkit.entity.Player;

public final class CombatLogXTagProvider implements CombatTagProvider {

    @Override
    public String name() {
        return "CombatLogX";
    }

    @Override
    public boolean isInCombat(Player player) {
        return player.hasMetadata("CombatLogX_Tagged") || player.hasMetadata("combatlogx_tagged");
    }
}

