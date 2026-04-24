package dev.rique.fluxwardrobe.api;

public interface WardrobeApi {

    WardrobeService wardrobeService();

    RestrictionService restrictionService();

    SlotLimitService slotLimitService();
}

