package dev.rique.ruinedwardrobe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardrobeSafetyDecisionsTest {

    @Test
    void blocksMutationWhenEquippedAndAutoUnequipDisabled() {
        assertTrue(WardrobeSafetyDecisions.shouldBlockPieceMutation(true, false));
        assertFalse(WardrobeSafetyDecisions.shouldBlockPieceMutation(true, true));
        assertFalse(WardrobeSafetyDecisions.shouldBlockPieceMutation(false, false));
    }

    @Test
    void autoUnequipOnlyWhenEquippedAndEnabled() {
        assertTrue(WardrobeSafetyDecisions.shouldAutoUnequipBeforeMutation(true, true));
        assertFalse(WardrobeSafetyDecisions.shouldAutoUnequipBeforeMutation(true, false));
        assertFalse(WardrobeSafetyDecisions.shouldAutoUnequipBeforeMutation(false, true));
    }

    @Test
    void clearsSelectedAfterAutoUnequipOnlyIfHappened() {
        assertTrue(WardrobeSafetyDecisions.shouldClearSelectedSlotAfterAutoUnequip(true));
        assertFalse(WardrobeSafetyDecisions.shouldClearSelectedSlotAfterAutoUnequip(false));
    }

    @Test
    void clearsSelectedAfterSyncWhenActiveSetFullyConsumed() {
        assertTrue(WardrobeSafetyDecisions.shouldClearSelectedSlotAfterSync(true, 8, 8));
        assertFalse(WardrobeSafetyDecisions.shouldClearSelectedSlotAfterSync(false, 8, 8));
        assertFalse(WardrobeSafetyDecisions.shouldClearSelectedSlotAfterSync(true, 8, 2));
    }

    @Test
    void refreshesCacheOnlyForNewerRemoteVersions() {
        assertTrue(WardrobeSafetyDecisions.shouldRefreshFromRemote(10, 9));
        assertFalse(WardrobeSafetyDecisions.shouldRefreshFromRemote(9, 9));
        assertFalse(WardrobeSafetyDecisions.shouldRefreshFromRemote(8, 9));
    }
}
