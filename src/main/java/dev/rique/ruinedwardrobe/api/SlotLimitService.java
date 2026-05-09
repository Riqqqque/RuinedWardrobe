package dev.rique.ruinedwardrobe.api;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SlotLimitService {

    int getMaxSlots(Player player);

    CompletableFuture<Integer> getBonusSlots(UUID playerId);

    CompletableFuture<Void> setBonusSlots(UUID playerId, int amount);
}

