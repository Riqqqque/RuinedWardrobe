package dev.rique.prismwardrobe.core;

import dev.rique.prismwardrobe.api.SlotLimitService;
import dev.rique.prismwardrobe.config.PluginConfig;
import dev.rique.prismwardrobe.storage.WardrobeRepository;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class SlotLimitServiceImpl implements SlotLimitService {

    private final WardrobeRepository repository;
    private final PluginConfig pluginConfig;
    private final PermissionTierResolver tierResolver;
    private final Map<UUID, Integer> bonusSlotsCache = new ConcurrentHashMap<>();

    public SlotLimitServiceImpl(WardrobeRepository repository, PluginConfig pluginConfig, PermissionTierResolver tierResolver) {
        this.repository = repository;
        this.pluginConfig = pluginConfig;
        this.tierResolver = tierResolver;
    }

    @Override
    public int getMaxSlots(Player player) {
        int tier = tierResolver.resolve(player, pluginConfig.defaultSlots());
        int bonus = bonusSlotsCache.getOrDefault(player.getUniqueId(), 0);
        return Math.min(pluginConfig.maxSlotsCap(), Math.max(pluginConfig.defaultSlots(), tier) + Math.max(0, bonus));
    }

    @Override
    public CompletableFuture<Integer> getBonusSlots(UUID playerId) {
        Integer cached = bonusSlotsCache.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return repository.getBonusSlots(playerId).thenApply(value -> {
            bonusSlotsCache.put(playerId, value);
            return value;
        });
    }

    @Override
    public CompletableFuture<Void> setBonusSlots(UUID playerId, int amount) {
        return repository.setBonusSlots(playerId, amount).thenAccept(old -> bonusSlotsCache.put(playerId, Math.max(0, amount)));
    }

    public void primeBonus(UUID playerId, int amount) {
        bonusSlotsCache.put(playerId, Math.max(0, amount));
    }

    public void invalidate(UUID playerId) {
        bonusSlotsCache.remove(playerId);
    }
}

