package dev.rique.ruinedwardrobe.listener;

import dev.rique.ruinedwardrobe.config.PluginConfig;
import dev.rique.ruinedwardrobe.core.WardrobeAuditLogger;
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
    private final WardrobeAuditLogger auditLogger;

    public PlayerSessionListener(
            WardrobeServiceImpl wardrobeService,
            WardrobeRepository repository,
            PluginConfig.SessionSettings sessionSettings,
            WardrobeAuditLogger auditLogger) {
        this.wardrobeService = wardrobeService;
        this.repository = repository;
        this.sessionSettings = sessionSettings;
        this.auditLogger = auditLogger;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (sessionSettings.touchPlayerRowOnJoin()) {
            repository.touchPlayer(event.getPlayer().getUniqueId())
                    .exceptionally(ex -> {
                        auditLogger.error(
                                "JOIN_TOUCH_ERROR",
                                event.getPlayer().getUniqueId(),
                                event.getPlayer().getName(),
                                ex,
                                java.util.Map.of());
                        return null;
                    });
        }
        if (sessionSettings.preloadProfileOnJoin()) {
            wardrobeService.getProfile(event.getPlayer().getUniqueId())
                    .exceptionally(ex -> {
                        auditLogger.error(
                                "JOIN_PRELOAD_ERROR",
                                event.getPlayer().getUniqueId(),
                                event.getPlayer().getName(),
                                ex,
                                java.util.Map.of());
                        return null;
                    });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        wardrobeService.releaseSession(event.getPlayer().getUniqueId());
    }
}
