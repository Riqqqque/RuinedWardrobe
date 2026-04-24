package dev.rique.fluxwardrobe.core;

public final class WardrobeSafetyDecisions {

    private WardrobeSafetyDecisions() {
    }

    public static boolean shouldBlockPieceMutation(boolean slotEquipped, boolean autoUnequipIfEquipped) {
        return slotEquipped && !autoUnequipIfEquipped;
    }

    public static boolean shouldAutoUnequipBeforeMutation(boolean slotEquipped, boolean autoUnequipIfEquipped) {
        return slotEquipped && autoUnequipIfEquipped;
    }

    public static boolean shouldClearSelectedSlotAfterAutoUnequip(boolean autoUnequipped) {
        return autoUnequipped;
    }

    public static boolean shouldClearSelectedSlotAfterSync(boolean noArmorRemaining, int selectedSlot, int activeSlot) {
        return noArmorRemaining && selectedSlot == activeSlot;
    }

    public static boolean shouldRefreshFromRemote(long remoteVersion, long localVersion) {
        return remoteVersion > localVersion;
    }
}
