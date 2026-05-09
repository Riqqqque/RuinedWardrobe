package dev.rique.ruinedwardrobe.api;

public interface WardrobeApi {

    WardrobeService wardrobeService();

    RestrictionService restrictionService();

    SlotLimitService slotLimitService();
}

