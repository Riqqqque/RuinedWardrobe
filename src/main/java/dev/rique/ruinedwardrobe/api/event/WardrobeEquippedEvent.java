package dev.rique.ruinedwardrobe.api.event;

import dev.rique.ruinedwardrobe.api.model.WardrobeSet;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class WardrobeEquippedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final WardrobeSet wardrobeSet;

    public WardrobeEquippedEvent(Player player, WardrobeSet wardrobeSet) {
        this.player = player;
        this.wardrobeSet = wardrobeSet;
    }

    public Player getPlayer() {
        return player;
    }

    public WardrobeSet getWardrobeSet() {
        return wardrobeSet;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

