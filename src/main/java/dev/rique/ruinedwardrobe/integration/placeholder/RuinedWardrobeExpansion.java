package dev.rique.ruinedwardrobe.integration.placeholder;

import dev.rique.ruinedwardrobe.api.model.WardrobeProfile;
import dev.rique.ruinedwardrobe.core.SlotLimitServiceImpl;
import dev.rique.ruinedwardrobe.core.WardrobeServiceImpl;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
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
        WardrobeProfile profile = wardrobeService.cachedProfile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            return "";
        }
        if (params.equalsIgnoreCase("slots_used")) {
            return String.valueOf(profile.sets().size());
        }
        if (params.equalsIgnoreCase("slots_max")) {
            return String.valueOf(slotLimitService.getMaxSlots(player));
        }
        if (params.equalsIgnoreCase("cooldown_remaining")) {
            long ms = wardrobeService.getRemainingCooldownMillis(player.getUniqueId());
            return String.valueOf(Math.max(0, (ms + 999L) / 1000L));
        }
        if (params.equalsIgnoreCase("selected_slot")) {
            return String.valueOf(profile.selectedSlot());
        }
        if (params.toLowerCase(Locale.ROOT).startsWith("set_name_")) {
            String slotRaw = params.substring("set_name_".length());
            try {
                int slot = Integer.parseInt(slotRaw);
                var set = profile.getSet(slot);
                return set == null ? "" : set.name();
            } catch (NumberFormatException ignored) {
                return "";
            }
        }
        return null;
    }
}
