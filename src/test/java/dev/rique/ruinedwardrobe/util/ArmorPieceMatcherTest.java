package dev.rique.ruinedwardrobe.util;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmorPieceMatcherTest {

    @Test
    void acceptsPlayerAndMobHeadsAsHelmetPieces() {
        assertTrue(ArmorPieceMatcher.matchesWardrobePiece("helmet", Material.PLAYER_HEAD));
        assertTrue(ArmorPieceMatcher.matchesWardrobePiece("helmet", Material.CREEPER_HEAD));
        assertTrue(ArmorPieceMatcher.matchesWardrobePiece("helmet", Material.DRAGON_HEAD));
        assertTrue(ArmorPieceMatcher.matchesWardrobePiece("helmet", Material.PIGLIN_HEAD));
        assertTrue(ArmorPieceMatcher.matchesWardrobePiece("helmet", Material.SKELETON_SKULL));
        assertTrue(ArmorPieceMatcher.matchesWardrobePiece("helmet", Material.WITHER_SKELETON_SKULL));
        assertTrue(ArmorPieceMatcher.matchesWardrobePiece("helmet", Material.ZOMBIE_HEAD));
    }
}
