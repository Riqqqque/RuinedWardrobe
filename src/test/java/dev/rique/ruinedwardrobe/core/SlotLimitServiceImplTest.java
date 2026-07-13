package dev.rique.ruinedwardrobe.core;

import dev.rique.ruinedwardrobe.config.PluginConfig;
import dev.rique.ruinedwardrobe.storage.WardrobeRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlotLimitServiceImplTest {

    @Test
    void staleBonusLoadCannotOverwriteNewerAdminUpdate() {
        UUID playerId = UUID.randomUUID();
        WardrobeRepository repository = mock(WardrobeRepository.class);
        PluginConfig config = mock(PluginConfig.class);
        CompletableFuture<Integer> staleLoad = new CompletableFuture<>();

        when(config.cacheSettings()).thenReturn(new PluginConfig.CacheSettings(1000, 60));
        when(repository.getBonusSlots(playerId)).thenReturn(staleLoad);
        when(repository.setBonusSlots(playerId, 5)).thenReturn(CompletableFuture.completedFuture(0));

        SlotLimitServiceImpl service = new SlotLimitServiceImpl(
                repository, config, mock(PermissionTierResolver.class));
        CompletableFuture<Integer> firstRead = service.getBonusSlots(playerId);

        service.setBonusSlots(playerId, 5).join();
        staleLoad.complete(0);

        assertEquals(5, firstRead.join());
        assertEquals(5, service.getBonusSlots(playerId).join());
    }
}
