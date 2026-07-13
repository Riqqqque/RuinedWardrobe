package dev.rique.ruinedwardrobe.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.rique.ruinedwardrobe.api.SlotLimitService;
import dev.rique.ruinedwardrobe.config.PluginConfig;
import dev.rique.ruinedwardrobe.storage.WardrobeRepository;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class SlotLimitServiceImpl implements SlotLimitService {

    private final WardrobeRepository repository;
    private final PluginConfig pluginConfig;
    private final PermissionTierResolver tierResolver;
    private final Cache<UUID, Integer> bonusSlotsCache;
    private final Cache<UUID, Long> bonusGenerations;
    private final AtomicLong generationSequence = new AtomicLong();

    public SlotLimitServiceImpl(WardrobeRepository repository, PluginConfig pluginConfig, PermissionTierResolver tierResolver) {
        this.repository = repository;
        this.pluginConfig = pluginConfig;
        this.tierResolver = tierResolver;
        this.bonusSlotsCache = Caffeine.newBuilder()
                .maximumSize(pluginConfig.cacheSettings().maxSize())
                .expireAfterAccess(pluginConfig.cacheSettings().expireAfterSeconds(), TimeUnit.SECONDS)
                .build();
        this.bonusGenerations = Caffeine.newBuilder()
                .maximumSize(pluginConfig.cacheSettings().maxSize())
                .expireAfterAccess(pluginConfig.cacheSettings().expireAfterSeconds(), TimeUnit.SECONDS)
                .build();
    }

    @Override
    public int getMaxSlots(Player player) {
        int tier = tierResolver.resolve(player, pluginConfig.defaultSlots());
        Integer cachedBonus = bonusSlotsCache.getIfPresent(player.getUniqueId());
        int bonus = cachedBonus == null ? 0 : cachedBonus;
        long total = (long) Math.max(pluginConfig.defaultSlots(), tier) + Math.max(0, bonus);
        return (int) Math.min(pluginConfig.maxSlotsCap(), total);
    }

    @Override
    public CompletableFuture<Integer> getBonusSlots(UUID playerId) {
        Integer cached = bonusSlotsCache.getIfPresent(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        long generation = generation(playerId);
        return repository.getBonusSlots(playerId).thenCompose(value -> {
            if (generation(playerId) != generation) {
                return getBonusSlots(playerId);
            }
            bonusSlotsCache.put(playerId, value);
            return CompletableFuture.completedFuture(value);
        });
    }

    @Override
    public CompletableFuture<Void> setBonusSlots(UUID playerId, int amount) {
        return repository.setBonusSlots(playerId, amount).thenAccept(old -> {
            advanceGeneration(playerId);
            bonusSlotsCache.put(playerId, Math.max(0, amount));
        });
    }

    public void primeBonus(UUID playerId, int amount) {
        advanceGeneration(playerId);
        bonusSlotsCache.put(playerId, Math.max(0, amount));
    }

    public void invalidate(UUID playerId) {
        advanceGeneration(playerId);
        bonusSlotsCache.invalidate(playerId);
    }

    private long generation(UUID playerId) {
        return bonusGenerations.get(playerId, ignored -> generationSequence.incrementAndGet());
    }

    private void advanceGeneration(UUID playerId) {
        bonusGenerations.put(playerId, generationSequence.incrementAndGet());
    }
}

