package dev.rique.fluxwardrobe.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for the Hypixel Skyblock-style wardrobe GUI.
 * 
 * Layout: 6 rows x 9 columns
 * - Rows 0-3: Armor columns (9 wardrobe slots, each showing
 * helmet/chest/leggings/boots vertically)
 * - Row 4: Equip buttons (gray dye = unequipped, black dye = equipped)
 * - Row 5: Navigation
 */
public final class GuiConfig {

    private static final int COLUMNS_PER_PAGE = 9;

    private final String mainTitleKey;
    private final int rows;
    private final List<Integer> slotDisplayIndices;
    private final int previousPageSlot;
    private final int nextPageSlot;
    private final int closeSlot;

    // Armor layout rows
    private final int helmetRow;
    private final int chestplateRow;
    private final int leggingsRow;
    private final int bootsRow;

    // Equip buttons row
    private final int equipButtonsRow;
    private final List<Integer> equipButtonSlots;

    private final List<Integer> controlFillerSlots;

    // Templates
    private final GuiItemTemplate emptySlotTemplate;
    private final GuiItemTemplate savedSlotTemplate;
    private final GuiItemTemplate lockedSlotTemplate;
    private final GuiItemTemplate cooldownSlotTemplate;
    private final GuiItemTemplate equipButtonUnequippedTemplate;
    private final GuiItemTemplate equipButtonEquippedTemplate;
    private final GuiItemTemplate controlFillerTemplate;

    private GuiConfig(Builder builder) {
        this.mainTitleKey = builder.mainTitleKey;
        this.rows = builder.rows;
        this.slotDisplayIndices = Collections.unmodifiableList(new ArrayList<>(builder.slotDisplayIndices));
        this.previousPageSlot = builder.previousPageSlot;
        this.nextPageSlot = builder.nextPageSlot;
        this.closeSlot = builder.closeSlot;
        this.helmetRow = builder.helmetRow;
        this.chestplateRow = builder.chestplateRow;
        this.leggingsRow = builder.leggingsRow;
        this.bootsRow = builder.bootsRow;
        this.equipButtonsRow = builder.equipButtonsRow;
        this.equipButtonSlots = Collections.unmodifiableList(new ArrayList<>(builder.equipButtonSlots));
        this.controlFillerSlots = Collections.unmodifiableList(new ArrayList<>(builder.controlFillerSlots));
        this.emptySlotTemplate = builder.emptySlotTemplate;
        this.savedSlotTemplate = builder.savedSlotTemplate;
        this.lockedSlotTemplate = builder.lockedSlotTemplate;
        this.cooldownSlotTemplate = builder.cooldownSlotTemplate;
        this.equipButtonUnequippedTemplate = builder.equipButtonUnequippedTemplate;
        this.equipButtonEquippedTemplate = builder.equipButtonEquippedTemplate;
        this.controlFillerTemplate = builder.controlFillerTemplate;
    }

    public static GuiConfig from(FileConfiguration config) {
        Builder builder = new Builder();

        builder.mainTitleKey = config.getString("titles.main", "gui.main-title");
        builder.rows = Math.max(1, Math.min(6, config.getInt("layout.rows", 6)));
        int maxIndexExclusive = builder.rows * 9;

        // Slot display indices (which columns are wardrobe slots)
        List<Integer> slotDisplayIndices = config.getIntegerList("layout.slot-display-indices");
        if (slotDisplayIndices.isEmpty()) {
            slotDisplayIndices = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8);
        }
        builder.slotDisplayIndices = fallbackIfEmpty(
                sanitizeSlots(slotDisplayIndices, COLUMNS_PER_PAGE),
                List.of(0, 1, 2, 3, 4, 5, 6, 7, 8));

        // Armor layout rows
        ConfigurationSection armorLayout = config.getConfigurationSection("layout.armor-layout");
        if (armorLayout != null) {
            builder.helmetRow = clampRow(armorLayout.getInt("helmet-row", 0), builder.rows);
            builder.chestplateRow = clampRow(armorLayout.getInt("chestplate-row", 1), builder.rows);
            builder.leggingsRow = clampRow(armorLayout.getInt("leggings-row", 2), builder.rows);
            builder.bootsRow = clampRow(armorLayout.getInt("boots-row", 3), builder.rows);
        } else {
            builder.helmetRow = 0;
            builder.chestplateRow = 1;
            builder.leggingsRow = 2;
            builder.bootsRow = 3;
        }

        // Equip buttons row
        ConfigurationSection equipButtons = config.getConfigurationSection("layout.equip-buttons");
        if (equipButtons != null) {
            builder.equipButtonsRow = clampRow(equipButtons.getInt("row", 4), builder.rows);
            List<Integer> equipSlots = equipButtons.getIntegerList("slots");
            if (equipSlots.isEmpty()) {
                equipSlots = List.of(36, 37, 38, 39, 40, 41, 42, 43, 44);
            }
            builder.equipButtonSlots = fallbackIfEmpty(
                    sanitizeSlots(equipSlots, maxIndexExclusive),
                    List.of(36, 37, 38, 39, 40, 41, 42, 43, 44));
        } else {
            builder.equipButtonsRow = 4;
            builder.equipButtonSlots = List.of(36, 37, 38, 39, 40, 41, 42, 43, 44);
        }

        // Navigation slots
        builder.previousPageSlot = optionalSlot(config, "layout.navigation.previous-page-slot", 45, maxIndexExclusive);
        builder.nextPageSlot = optionalSlot(config, "layout.navigation.next-page-slot", 53, maxIndexExclusive);
        builder.closeSlot = optionalSlot(config, "layout.navigation.close-slot", 49, maxIndexExclusive);

        // Control filler slots
        List<Integer> controlSlots = config.getIntegerList("layout.controls.filler-slots");
        if (controlSlots.isEmpty()) {
            controlSlots = List.of(46, 47, 48, 50, 51, 52);
        }
        builder.controlFillerSlots = fallbackIfEmpty(
                sanitizeSlots(controlSlots, maxIndexExclusive),
                List.of(46, 47, 48, 50, 51, 52));

        // Templates
        ConfigurationSection templates = config.getConfigurationSection("templates");
        builder.emptySlotTemplate = template(templates, "empty-slot", Material.GREEN_STAINED_GLASS_PANE,
                "gui.slot-empty-name", List.of("gui.slot-empty-lore"), false, 0);
        builder.savedSlotTemplate = template(templates, "saved-slot", Material.PLAYER_HEAD, "gui.slot-saved-name",
                List.of("gui.slot-saved-lore"), false, 0);
        builder.lockedSlotTemplate = template(templates, "locked-slot", Material.BLACK_STAINED_GLASS_PANE, "gui.slot-locked-name",
                List.of("gui.slot-locked-lore"), false, 0);
        builder.cooldownSlotTemplate = template(templates, "cooldown-slot", Material.CLOCK, "gui.slot-cooldown-name",
                List.of("gui.slot-cooldown-lore"), false, 0);
        builder.equipButtonUnequippedTemplate = template(templates, "equip-button-unequipped", Material.GRAY_DYE,
                "gui.equip-button-name", List.of("gui.equip-button-lore"), false, 0);
        builder.equipButtonEquippedTemplate = template(templates, "equip-button-equipped", Material.BLACK_DYE,
                "gui.equip-button-equipped-name", List.of("gui.equip-button-equipped-lore"), true, 0);
        builder.controlFillerTemplate = template(templates, "control-filler", Material.BLACK_STAINED_GLASS_PANE,
                "gui.control-filler-name", List.of(), false, 0);

        return builder.build();
    }

    private static List<Integer> sanitizeSlots(List<Integer> slots, int maxIndexExclusive) {
        return slots.stream()
                .filter(index -> index >= 0 && index < maxIndexExclusive)
                .distinct()
                .toList();
    }

    private static List<Integer> fallbackIfEmpty(List<Integer> values, List<Integer> fallback) {
        return values.isEmpty() ? fallback : values;
    }

    private static int clampRow(int row, int maxRows) {
        return Math.max(0, Math.min(maxRows - 1, row));
    }

    private static int optionalSlot(FileConfiguration config, String path, int defaultValue, int maxIndexExclusive) {
        int candidate = config.isSet(path) ? config.getInt(path) : defaultValue;
        if (candidate < 0) {
            return -1;
        }
        return Math.max(0, Math.min(maxIndexExclusive - 1, candidate));
    }

    private static GuiItemTemplate template(
            ConfigurationSection templates,
            String key,
            Material defaultMaterial,
            String defaultNameKey,
            List<String> defaultLoreKeys,
            boolean defaultGlow,
            int defaultModelData) {
        if (templates == null) {
            return new GuiItemTemplate(defaultMaterial, defaultNameKey, defaultLoreKeys, defaultGlow, defaultModelData);
        }
        ConfigurationSection section = templates.getConfigurationSection(key);
        if (section == null) {
            return new GuiItemTemplate(defaultMaterial, defaultNameKey, defaultLoreKeys, defaultGlow, defaultModelData);
        }
        Material material = Material.matchMaterial(section.getString("material", defaultMaterial.name()));
        if (material == null) {
            material = defaultMaterial;
        }
        if ("locked-slot".equals(key) && material == Material.BARRIER) {
            // Avoid visual confusion with the center close button barrier.
            material = Material.BLACK_STAINED_GLASS_PANE;
        }
        String nameKey = section.getString("name-key", defaultNameKey);
        List<String> loreKeys = section.getStringList("lore-keys");
        if (loreKeys.isEmpty()) {
            loreKeys = defaultLoreKeys;
        }
        boolean glow = section.getBoolean("glow", defaultGlow);
        int modelData = section.getInt("model-data", defaultModelData);
        return new GuiItemTemplate(material, nameKey, loreKeys, glow, modelData);
    }

    // Getters
    public String mainTitleKey() {
        return mainTitleKey;
    }

    public int rows() {
        return rows;
    }

    public List<Integer> slotDisplayIndices() {
        return slotDisplayIndices;
    }

    public int previousPageSlot() {
        return previousPageSlot;
    }

    public int nextPageSlot() {
        return nextPageSlot;
    }

    public int closeSlot() {
        return closeSlot;
    }

    public int helmetRow() {
        return helmetRow;
    }

    public int chestplateRow() {
        return chestplateRow;
    }

    public int leggingsRow() {
        return leggingsRow;
    }

    public int bootsRow() {
        return bootsRow;
    }

    public int equipButtonsRow() {
        return equipButtonsRow;
    }

    public List<Integer> equipButtonSlots() {
        return equipButtonSlots;
    }

    public List<Integer> controlFillerSlots() {
        return controlFillerSlots;
    }

    public GuiItemTemplate emptySlotTemplate() {
        return emptySlotTemplate;
    }

    public GuiItemTemplate savedSlotTemplate() {
        return savedSlotTemplate;
    }

    public GuiItemTemplate lockedSlotTemplate() {
        return lockedSlotTemplate;
    }

    public GuiItemTemplate cooldownSlotTemplate() {
        return cooldownSlotTemplate;
    }

    public GuiItemTemplate equipButtonUnequippedTemplate() {
        return equipButtonUnequippedTemplate;
    }

    public GuiItemTemplate equipButtonEquippedTemplate() {
        return equipButtonEquippedTemplate;
    }

    public GuiItemTemplate controlFillerTemplate() {
        return controlFillerTemplate;
    }

    /**
     * Calculate the GUI inventory slot for a given column and row.
     */
    public int getSlotForColumn(int column, int row) {
        return row * 9 + column;
    }

    /**
     * Get the equip button slot for a given column index.
     */
    public int getEquipButtonSlot(int columnIndex) {
        if (columnIndex < 0 || columnIndex >= equipButtonSlots.size()) {
            return -1;
        }
        return equipButtonSlots.get(columnIndex);
    }

    public record GuiItemTemplate(
            Material material,
            String nameKey,
            List<String> loreKeys,
            boolean glow,
            int modelData) {
    }

    private static class Builder {
        String mainTitleKey = "gui.main-title";
        int rows = 6;
        List<Integer> slotDisplayIndices = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8);
        int previousPageSlot = 45;
        int nextPageSlot = 53;
        int closeSlot = 49;
        int helmetRow = 0;
        int chestplateRow = 1;
        int leggingsRow = 2;
        int bootsRow = 3;
        int equipButtonsRow = 4;
        List<Integer> equipButtonSlots = List.of(36, 37, 38, 39, 40, 41, 42, 43, 44);
        List<Integer> controlFillerSlots = List.of(46, 47, 48, 50, 51, 52);
        GuiItemTemplate emptySlotTemplate;
        GuiItemTemplate savedSlotTemplate;
        GuiItemTemplate lockedSlotTemplate;
        GuiItemTemplate cooldownSlotTemplate;
        GuiItemTemplate equipButtonUnequippedTemplate;
        GuiItemTemplate equipButtonEquippedTemplate;
        GuiItemTemplate controlFillerTemplate;

        GuiConfig build() {
            return new GuiConfig(this);
        }
    }
}
