package dev.rique.fluxwardrobe.listener;

import dev.rique.fluxwardrobe.core.WardrobeServiceImpl;
import dev.rique.fluxwardrobe.storage.WardrobeRepository;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerSessionListener implements Listener {

    private final WardrobeServiceImpl wardrobeService;
    private final WardrobeRepository repository;

    public PlayerSessionListener(WardrobeServiceImpl wardrobeService, WardrobeRepository repository) {
        this.wardrobeService = wardrobeService;
        this.repository = repository;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        repository.touchPlayer(event.getPlayer().getUniqueId());
        wardrobeService.getProfile(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        wardrobeService.invalidateCache(event.getPlayer().getUniqueId());
    }
}

