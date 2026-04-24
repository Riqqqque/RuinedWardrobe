package dev.rique.fluxwardrobe.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class WardrobeSlotsChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final int oldBonusSlots;
    private final int newBonusSlots;

    public WardrobeSlotsChangedEvent(UUID playerId, int oldBonusSlots, int newBonusSlots) {
        this.playerId = playerId;
        this.oldBonusSlots = oldBonusSlots;
        this.newBonusSlots = newBonusSlots;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getOldBonusSlots() {
        return oldBonusSlots;
    }

    public int getNewBonusSlots() {
        return newBonusSlots;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

