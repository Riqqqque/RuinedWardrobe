package dev.rique.prismwardrobe.api.event;

import dev.rique.prismwardrobe.api.model.WardrobeSet;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class WardrobePreEquipEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final WardrobeSet wardrobeSet;
    private boolean cancelled;

    public WardrobePreEquipEvent(Player player, WardrobeSet wardrobeSet) {
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
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

