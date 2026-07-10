package dev.rique.ruinedwardrobe.integration.placeholder;

import dev.rique.ruinedwardrobe.api.model.WardrobeProfile;
import dev.rique.ruinedwardrobe.api.model.WardrobeSet;
import dev.rique.ruinedwardrobe.core.SlotLimitServiceImpl;
import dev.rique.ruinedwardrobe.core.WardrobeServiceImpl;
import dev.rique.ruinedwardrobe.gui.WardrobeMenuHolder;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class RuinedWardrobeExpansion extends PlaceholderExpansion {

    private static final String IDENTIFIER = "ruinedwardrobe";

    private final JavaPlugin plugin;
    private final WardrobeServiceImpl wardrobeService;
    private final SlotLimitServiceImpl slotLimitService;

    public RuinedWardrobeExpansion(
            JavaPlugin plugin,
            WardrobeServiceImpl wardrobeService,
            SlotLimitServiceImpl slotLimitService) {
        this.plugin = plugin;
        this.wardrobeService = wardrobeService;
        this.slotLimitService = slotLimitService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public @NotNull String getAuthor() {
        return "Rique";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        String normalized = params.toLowerCase(Locale.ROOT);
        if (normalized.equals("open") || normalized.equals("wardrobe_open")) {
            return Boolean.toString(player.getOpenInventory().getTopInventory().getHolder() instanceof WardrobeMenuHolder);
        }
        if (normalized.equals("slots_max") || normalized.equals("slots_unlocked")) {
            return String.valueOf(slotLimitService.getMaxSlots(player));
        }
        if (normalized.equals("cooldown_remaining")) {
            long ms = wardrobeService.getRemainingCooldownMillis(player.getUniqueId());
            return String.valueOf(Math.max(0, (ms + 999L) / 1000L));
        }
        if (normalized.equals("cooldown_ready")) {
            return Boolean.toString(wardrobeService.getRemainingCooldownMillis(player.getUniqueId()) <= 0);
        }
        WardrobeProfile profile = wardrobeService.cachedProfile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            return "";
        }
        if (normalized.equals("slots_used")) {
            return String.valueOf(profile.sets().size());
        }
        if (normalized.equals("slots_free")) {
            return String.valueOf(Math.max(0, slotLimitService.getMaxSlots(player) - profile.sets().size()));
        }
        if (normalized.equals("selected_slot")) {
            return String.valueOf(profile.selectedSlot());
        }
        if (normalized.equals("equipped")) {
            return Boolean.toString(profile.selectedSlot() > 0 && profile.getSet(profile.selectedSlot()) != null);
        }
        if (normalized.equals("equipped_slot")) {
            return profile.selectedSlot() > 0 && profile.getSet(profile.selectedSlot()) != null
                    ? String.valueOf(profile.selectedSlot())
                    : "";
        }
        if (normalized.equals("equipped_name")) {
            WardrobeSet equipped = profile.selectedSlot() > 0 ? profile.getSet(profile.selectedSlot()) : null;
            return equipped == null ? "" : equipped.name();
        }
        if (normalized.startsWith("set_name_")) {
            WardrobeSet set = setFromSlot(profile, normalized.substring("set_name_".length()));
            return set == null ? "" : set.name();
        }
        if (normalized.startsWith("set_exists_")) {
            return Boolean.toString(setFromSlot(profile, normalized.substring("set_exists_".length())) != null);
        }
        if (normalized.startsWith("set_piece_count_")) {
            WardrobeSet set = setFromSlot(profile, normalized.substring("set_piece_count_".length()));
            return set == null ? "0" : String.valueOf(pieceCount(set));
        }
        if (normalized.startsWith("set_piece_")) {
            return setPiece(profile, normalized.substring("set_piece_".length()));
        }
        return null;
    }

    private WardrobeSet setFromSlot(WardrobeProfile profile, String slotRaw) {
        try {
            return profile.getSet(Integer.parseInt(slotRaw));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String setPiece(WardrobeProfile profile, String raw) {
        int separator = raw.indexOf('_');
        if (separator <= 0 || separator >= raw.length() - 1) {
            return "";
        }
        WardrobeSet set = setFromSlot(profile, raw.substring(0, separator));
        if (set == null) {
            return "";
        }
        ItemStack item = switch (raw.substring(separator + 1)) {
            case "helmet" -> set.helmet();
            case "chestplate" -> set.chestplate();
            case "leggings" -> set.leggings();
            case "boots" -> set.boots();
            default -> null;
        };
        return item == null || isAir(item.getType()) ? "" : item.getType().name().toLowerCase(Locale.ROOT);
    }

    private int pieceCount(WardrobeSet set) {
        int count = 0;
        count += hasPiece(set.helmet()) ? 1 : 0;
        count += hasPiece(set.chestplate()) ? 1 : 0;
        count += hasPiece(set.leggings()) ? 1 : 0;
        count += hasPiece(set.boots()) ? 1 : 0;
        return count;
    }

    private boolean hasPiece(ItemStack item) {
        return item != null && !isAir(item.getType());
    }

    private boolean isAir(Material material) {
        return material == null
                || material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR;
    }
}
