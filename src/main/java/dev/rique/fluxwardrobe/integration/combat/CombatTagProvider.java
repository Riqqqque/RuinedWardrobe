package dev.rique.fluxwardrobe.integration.combat;

import org.bukkit.entity.Player;

public interface CombatTagProvider {

    String name();

    boolean isInCombat(Player player);
}

