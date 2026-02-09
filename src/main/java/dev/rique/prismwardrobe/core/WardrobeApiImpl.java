package dev.rique.prismwardrobe.core;

import dev.rique.prismwardrobe.api.RestrictionService;
import dev.rique.prismwardrobe.api.SlotLimitService;
import dev.rique.prismwardrobe.api.WardrobeApi;
import dev.rique.prismwardrobe.api.WardrobeService;

public final class WardrobeApiImpl implements WardrobeApi {

    private final WardrobeService wardrobeService;
    private final RestrictionService restrictionService;
    private final SlotLimitService slotLimitService;

    public WardrobeApiImpl(WardrobeService wardrobeService, RestrictionService restrictionService, SlotLimitService slotLimitService) {
        this.wardrobeService = wardrobeService;
        this.restrictionService = restrictionService;
        this.slotLimitService = slotLimitService;
    }

    @Override
    public WardrobeService wardrobeService() {
        return wardrobeService;
    }

    @Override
    public RestrictionService restrictionService() {
        return restrictionService;
    }

    @Override
    public SlotLimitService slotLimitService() {
        return slotLimitService;
    }
}

