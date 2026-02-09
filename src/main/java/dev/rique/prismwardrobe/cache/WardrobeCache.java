package dev.rique.prismwardrobe.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import dev.rique.prismwardrobe.api.model.WardrobeProfile;
import dev.rique.prismwardrobe.config.PluginConfig;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class WardrobeCache {

    private final Cache<UUID, WardrobeProfile> cache;

    public WardrobeCache(PluginConfig.CacheSettings settings) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(settings.maxSize())
                .expireAfterAccess(settings.expireAfterSeconds(), TimeUnit.SECONDS)
                .recordStats()
                .build();
    }

    public Optional<WardrobeProfile> get(UUID playerId) {
        return Optional.ofNullable(cache.getIfPresent(playerId));
    }

    public void put(WardrobeProfile profile) {
        cache.put(profile.playerId(), profile);
    }

    public void invalidate(UUID playerId) {
        cache.invalidate(playerId);
    }

    public long size() {
        return cache.estimatedSize();
    }

    public CacheStats stats() {
        return cache.stats();
    }
}

