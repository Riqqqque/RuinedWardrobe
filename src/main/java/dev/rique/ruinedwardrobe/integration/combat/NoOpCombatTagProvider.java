package dev.rique.ruinedwardrobe.integration.combat;

import org.bukkit.entity.Player;

public final class NoOpCombatTagProvider implements CombatTagProvider {

    @Override
    public String name() {
        return "none";
    }

    @Override
    public boolean isInCombat(Player player) {
        return false;
    }
}

