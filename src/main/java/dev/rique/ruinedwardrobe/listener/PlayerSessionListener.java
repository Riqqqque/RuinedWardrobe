package dev.rique.ruinedwardrobe.listener;

import dev.rique.ruinedwardrobe.config.PluginConfig;
import dev.rique.ruinedwardrobe.core.WardrobeServiceImpl;
import dev.rique.ruinedwardrobe.storage.WardrobeRepository;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerSessionListener implements Listener {

    private final WardrobeServiceImpl wardrobeService;
    private final WardrobeRepository repository;
    private final PluginConfig.SessionSettings sessionSettings;

    public PlayerSessionListener(
            WardrobeServiceImpl wardrobeService,
            WardrobeRepository repository,
            PluginConfig.SessionSettings sessionSettings) {
        this.wardrobeService = wardrobeService;
        this.repository = repository;
        this.sessionSettings = sessionSettings;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (sessionSettings.touchPlayerRowOnJoin()) {
            repository.touchPlayer(event.getPlayer().getUniqueId());
        }
        if (sessionSettings.preloadProfileOnJoin()) {
            wardrobeService.getProfile(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        wardrobeService.invalidateCache(event.getPlayer().getUniqueId());
    }
}
