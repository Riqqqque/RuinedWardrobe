package dev.rique.fluxwardrobe.api;

import dev.rique.fluxwardrobe.api.model.RestrictionDecision;
import org.bukkit.entity.Player;

public interface RestrictionService {

    RestrictionDecision canEquip(Player player);
}

