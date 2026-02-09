package dev.rique.prismwardrobe.api;

import dev.rique.prismwardrobe.api.model.RestrictionDecision;
import org.bukkit.entity.Player;

public interface RestrictionService {

    RestrictionDecision canEquip(Player player);
}

