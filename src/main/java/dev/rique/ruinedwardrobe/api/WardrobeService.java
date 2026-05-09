package dev.rique.ruinedwardrobe.api;

import dev.rique.ruinedwardrobe.api.model.WardrobeProfile;
import dev.rique.ruinedwardrobe.api.model.WardrobeResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface WardrobeService {

    CompletableFuture<WardrobeProfile> getProfile(UUID playerId);

    CompletableFuture<WardrobeResult> saveSet(UUID playerId, int slot);

    CompletableFuture<WardrobeResult> equipSet(UUID playerId, int slot);

    CompletableFuture<WardrobeResult> deleteSet(UUID playerId, int slot);

    CompletableFuture<WardrobeResult> renameSet(UUID playerId, int slot, String newName);
}
