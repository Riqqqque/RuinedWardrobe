package dev.rique.ruinedwardrobe.api;

import dev.rique.ruinedwardrobe.api.model.RestrictionDecision;
import org.bukkit.entity.Player;

public interface RestrictionService {

    RestrictionDecision canEquip(Player player);
}

