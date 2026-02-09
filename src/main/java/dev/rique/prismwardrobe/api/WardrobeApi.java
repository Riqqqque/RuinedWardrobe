package dev.rique.prismwardrobe.api;

public interface WardrobeApi {

    WardrobeService wardrobeService();

    RestrictionService restrictionService();

    SlotLimitService slotLimitService();
}

