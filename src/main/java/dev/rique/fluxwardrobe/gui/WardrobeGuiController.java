package dev.rique.fluxwardrobe.gui;

import dev.rique.fluxwardrobe.api.model.WardrobeProfile;
import dev.rique.fluxwardrobe.api.model.WardrobeResult;
import dev.rique.fluxwardrobe.api.model.WardrobeSet;
import dev.rique.fluxwardrobe.config.GuiConfig;
import dev.rique.fluxwardrobe.config.PluginConfig;
import dev.rique.fluxwardrobe.core.PermissionNodes;
import dev.rique.fluxwardrobe.core.SlotLimitServiceImpl;
import dev.rique.fluxwardrobe.core.WardrobeArmorBindingService;
import dev.rique.fluxwardrobe.core.WardrobeServiceImpl;
import dev.rique.fluxwardrobe.lang.MessageService;
import dev.rique.fluxwardrobe.scheduler.SchedulerAdapter;
import dev.rique.fluxwardrobe.util.ArmorPieceMatcher;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hypixel Skyblock-style wardrobe GUI controller with equip buttons and
 * drag-drop armor.
 * 
 * Layout: 9 columns x 4 rows of armor, equip buttons in row 4, navigation in
 * row 5.
 * Players can click armor to take it, drag/click armor from their inventory
 * into piece slots, or click equip buttons.
 */
public final class WardrobeGuiController {

    private final SchedulerAdapter schedulerAdapter;
    private final GuiConfig guiConfig;
    private final PluginConfig pluginConfig;
    private final MessageService messageService;
    private final WardrobeServiceImpl wardrobeService;
    private final SlotLimitServiceImpl slotLimitService;
    private final WardrobeArmorBindingService bindingService;
    private final Set<UUID> pendingActions = ConcurrentHashMap.newKeySet();

    public WardrobeGuiController(
            SchedulerAdapter schedulerAdapter,
            GuiConfig guiConfig,
            PluginConfig pluginConfig,
            MessageService messageService,
            WardrobeServiceImpl wardrobeService,
            SlotLimitServiceImpl slotLimitService,
            WardrobeArmorBindingService bindingService) {
        this.schedulerAdapter = schedulerAdapter;
        this.guiConfig = guiConfig;
        this.pluginConfig = pluginConfig;
        this.messageService = messageService;
        this.wardrobeService = wardrobeService;
        this.slotLimitService = slotLimitService;
        this.bindingService = bindingService;
    }

    public void openMain(Player viewer, UUID targetId, int page) {
        wardrobeService.getProfile(targetId).thenAccept(
                profile -> schedulerAdapter.runPlayer(viewer, () -> openMainSync(viewer, targetId, profile, page)))
                .exceptionally(ex -> {
                    schedulerAdapter.runPlayer(viewer,
                            () -> messageService.send(viewer, "error.storage", Map.of("reason", ex.getMessage())));
                    return null;
                });
    }

    public void handleMainClick(Player viewer, WardrobeMenuHolder holder, InventoryClickEvent event) {
        int clickedSlot = event.getRawSlot();
        ClickType clickType = event.getClick();
        GuiSession session = holder.session();

        // Navigation buttons
        if (slotMatches(guiConfig.previousPageSlot(), clickedSlot)) {
            int newPage = Math.max(0, session.page() - 1);
            openMain(viewer, session.targetId(), newPage);
            return;
        }
        if (slotMatches(guiConfig.nextPageSlot(), clickedSlot)) {
            int actualMaxPages = Math.max(1, pluginConfig.maxPages());
            if (session.page() + 1 < actualMaxPages) {
                openMain(viewer, session.targetId(), session.page() + 1);
            }
            return;
        }
        if (slotMatches(guiConfig.closeSlot(), clickedSlot)) {
            viewer.closeInventory();
            return;
        }

        // Check equip buttons (row 4)
        if (guiConfig.equipButtonSlots().contains(clickedSlot)) {
            int wardrobeSlot = session.resolveWardrobeSlot(clickedSlot);
            if (wardrobeSlot > 0) {
                executeAndRefresh(viewer, session, wardrobeService.equipSet(session.targetId(), wardrobeSlot));
            }
            return;
        }

        // Check armor area clicks
        ArmorPiece piece = resolveArmorPieceFromSlot(clickedSlot);
        int wardrobeSlot = resolveWardrobeSlotFromClick(clickedSlot, session);
        if (wardrobeSlot < 1 || piece == null) {
            return;
        }

        // Place armor piece from cursor into the clicked wardrobe piece slot.
        if (!isAir(viewer.getItemOnCursor())) {
            handleArmorPlace(viewer, session, wardrobeSlot, piece, viewer.getItemOnCursor());
            return;
        }

        // Left-click on saved armor = take piece to inventory.
        if (clickType.isLeftClick()) {
            handleArmorPickup(viewer, session, wardrobeSlot, piece);
        }
    }

    /**
     * Handle cursor drag into a single wardrobe armor slot.
     */
    public void handleMainDrag(Player viewer, WardrobeMenuHolder holder, int rawSlot, ItemStack draggedItem) {
        ArmorPiece piece = resolveArmorPieceFromSlot(rawSlot);
        if (piece == null) {
            return;
        }
        if (draggedItem == null || draggedItem.getType().isAir()) {
            return;
        }
        GuiSession session = holder.session();
        int wardrobeSlot = resolveWardrobeSlotFromClick(rawSlot, session);
        if (wardrobeSlot < 1) {
            return;
        }
        handleArmorPlace(viewer, session, wardrobeSlot, piece, draggedItem);
    }

    private void handleArmorPlace(Player viewer, GuiSession session, int wardrobeSlot, ArmorPiece piece,
            ItemStack cursorItem) {
        if (piece == null || cursorItem == null || cursorItem.getType().isAir()) {
            return;
        }
        if (!beginAction(viewer)) {
            return;
        }
        if (bindingService.isBound(cursorItem)) {
            messageService.send(viewer, "error.bound-armor-locked");
            endAction(viewer);
            return;
        }
        if (!piece.matches(cursorItem.getType())) {
            messageService.send(viewer, "error.wrong-armor-type");
            endAction(viewer);
            return;
        }

        ItemStack consumed = consumeSingleCursorItem(viewer);
        if (consumed == null) {
            endAction(viewer);
            return;
        }

        wardrobeService.setArmorPiece(session.targetId(), wardrobeSlot, piece.key(), consumed)
                .thenAccept(result -> schedulerAdapter.runPlayer(viewer, () -> {
                    if (!result.isSuccess()) {
                        restoreCursorItem(viewer, consumed);
                    }
                    applyFeedback(viewer, result);
                    endAction(viewer);
                    openMain(viewer, session.targetId(), session.page());
                }))
                .exceptionally(ex -> {
                    schedulerAdapter.runPlayer(viewer, () -> {
                        restoreCursorItem(viewer, consumed);
                        messageService.send(viewer, "error.storage", Map.of("reason", ex.getMessage()));
                        endAction(viewer);
                        openMain(viewer, session.targetId(), session.page());
                    });
                    return null;
                });
    }

    private void handleArmorPickup(Player viewer, GuiSession session, int wardrobeSlot, ArmorPiece piece) {
        if (piece == null) {
            return;
        }
        if (!beginAction(viewer)) {
            return;
        }
        wardrobeService.getProfile(session.targetId()).thenAccept(profile -> schedulerAdapter.runPlayer(viewer, () -> {
            WardrobeSet set = profile.getSet(wardrobeSlot);
            if (set == null) {
                endAction(viewer);
                return;
            }

            ItemStack armorPiece = piece.read(set);

            if (armorPiece == null || armorPiece.getType().isAir()) {
                endAction(viewer);
                return;
            }

            // Check if player has room in inventory
            if (viewer.getInventory().firstEmpty() == -1) {
                messageService.send(viewer, "error.inventory-full", Map.of());
                endAction(viewer);
                return;
            }

            ItemStack reward = resolvePickupReward(viewer, session.targetId(), wardrobeSlot, piece, armorPiece);
            wardrobeService.clearArmorPiece(session.targetId(), wardrobeSlot, piece.key())
                    .thenAccept(result -> schedulerAdapter.runPlayer(viewer, () -> {
                        if (result.isSuccess()) {
                            viewer.getInventory().addItem(reward);
                        }
                        applyFeedback(viewer, result);
                        endAction(viewer);
                        openMain(viewer, session.targetId(), session.page());
                    }))
                    .exceptionally(ex -> {
                        schedulerAdapter.runPlayer(viewer, () -> {
                            messageService.send(viewer, "error.storage", Map.of("reason", ex.getMessage()));
                            endAction(viewer);
                        });
                        return null;
                    });
        })).exceptionally(ex -> {
            schedulerAdapter.runPlayer(viewer, () -> {
                messageService.send(viewer, "error.storage", Map.of("reason", ex.getMessage()));
                endAction(viewer);
            });
            return null;
        });
    }

    /**
     * Resolve which wardrobe slot was clicked based on the GUI slot.
     */
    private int resolveWardrobeSlotFromClick(int clickedSlot, GuiSession session) {
        int row = clickedSlot / 9;
        int column = clickedSlot % 9;

        // Only armor rows are clickable
        if (row != guiConfig.helmetRow() && row != guiConfig.chestplateRow()
                && row != guiConfig.leggingsRow() && row != guiConfig.bootsRow()) {
            return -1;
        }

        // Check if this column is a valid slot display
        if (!guiConfig.slotDisplayIndices().contains(column)) {
            return -1;
        }

        return session.resolveWardrobeSlot(clickedSlot);
    }

    private void openMainSync(Player viewer, UUID targetId, WardrobeProfile profile, int requestedPage) {
        Player targetPlayer = viewer.getUniqueId().equals(targetId) ? viewer : Bukkit.getPlayer(targetId);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            messageService.send(viewer, "error.player-offline");
            return;
        }

        int maxSlots = slotLimitService.getMaxSlots(targetPlayer);
        int slotsPerPage = slotsPerPage();

        int actualMaxPages = Math.max(1, pluginConfig.maxPages());
        int page = Math.max(0, Math.min(requestedPage, actualMaxPages - 1));

        Map<String, String> titlePlaceholders = new HashMap<>();
        titlePlaceholders.put("page", String.valueOf(page + 1));
        titlePlaceholders.put("max_page", String.valueOf(actualMaxPages));

        GuiSession session = new GuiSession(viewer.getUniqueId(), targetId, page);
        Inventory inventory = Bukkit.createInventory(
                new WardrobeMenuHolder(session),
                guiConfig.rows() * 9,
                messageService.component(viewer, guiConfig.mainTitleKey(), titlePlaceholders));

        // Render control filler in navigation row
        ItemStack controlFiller = buildTemplate(viewer, guiConfig.controlFillerTemplate(), Map.of());
        for (int slot : guiConfig.controlFillerSlots()) {
            setIfConfigured(inventory, slot, controlFiller);
        }
        for (int slot : guiConfig.equipButtonSlots()) {
            setIfConfigured(inventory, slot, controlFiller);
        }

        long cooldownMillis = wardrobeService.getRemainingCooldownMillis(targetId);
        boolean bypassCooldown = PermissionNodes.has(targetPlayer, "bypass.cooldown");

        // Render armor columns and equip buttons
        List<Integer> displayIndices = guiConfig.slotDisplayIndices();
        for (int i = 0; i < displayIndices.size(); i++) {
            int column = displayIndices.get(i);
            int wardrobeSlot = page * slotsPerPage + i + 1;

            int helmetSlot = guiConfig.getSlotForColumn(column, guiConfig.helmetRow());
            int chestSlot = guiConfig.getSlotForColumn(column, guiConfig.chestplateRow());
            int leggingsSlot = guiConfig.getSlotForColumn(column, guiConfig.leggingsRow());
            int bootsSlot = guiConfig.getSlotForColumn(column, guiConfig.bootsRow());
            int equipButtonSlot = guiConfig.getEquipButtonSlot(i);

            // Bind all interactive slots to the same wardrobe set index.
            session.bindSlot(helmetSlot, wardrobeSlot);
            session.bindSlot(chestSlot, wardrobeSlot);
            session.bindSlot(leggingsSlot, wardrobeSlot);
            session.bindSlot(bootsSlot, wardrobeSlot);
            if (equipButtonSlot >= 0) {
                session.bindSlot(equipButtonSlot, wardrobeSlot);
            }

            boolean isEquipped = profile.selectedSlot() == wardrobeSlot;

            if (wardrobeSlot > maxSlots) {
                renderLockedColumn(inventory, viewer, column, wardrobeSlot, maxSlots);
                setIfConfigured(inventory, equipButtonSlot, buildTemplate(viewer, guiConfig.lockedSlotTemplate(),
                        Map.of("slot", String.valueOf(wardrobeSlot), "max", String.valueOf(maxSlots))));
                continue;
            }

            WardrobeSet wardrobeSet = profile.getSet(wardrobeSlot);
            Map<String, String> placeholders = Map.of("slot", String.valueOf(wardrobeSlot));

            if (wardrobeSet == null) {
                renderEmptyColumn(inventory, viewer, column, wardrobeSlot);
                setIfConfigured(inventory, equipButtonSlot,
                        buildTemplate(viewer, guiConfig.equipButtonUnequippedTemplate(), placeholders));
            } else if (cooldownMillis > 0 && !bypassCooldown) {
                renderCooldownColumn(inventory, viewer, column, wardrobeSet, cooldownMillis);
                setIfConfigured(inventory, equipButtonSlot, buildTemplate(viewer, guiConfig.cooldownSlotTemplate(),
                        Map.of("slot", String.valueOf(wardrobeSlot), "seconds",
                                String.valueOf(Math.max(1L, (cooldownMillis + 999) / 1000)))));
            } else {
                renderSavedColumn(inventory, viewer, column, wardrobeSet, isEquipped);
                GuiConfig.GuiItemTemplate equipTemplate = isEquipped
                        ? guiConfig.equipButtonEquippedTemplate()
                        : guiConfig.equipButtonUnequippedTemplate();
                setIfConfigured(inventory, equipButtonSlot, buildTemplate(viewer, equipTemplate, placeholders));
            }
        }

        // Navigation buttons
        Map<String, String> navPlaceholders = Map.of("page", String.valueOf(page + 1), "max_page",
                String.valueOf(actualMaxPages));

        setIfConfigured(inventory, guiConfig.previousPageSlot(),
                nav(Material.ARROW, viewer, "gui.nav-previous-name", List.of("gui.nav-previous-lore"),
                        navPlaceholders));

        setIfConfigured(inventory, guiConfig.nextPageSlot(),
                nav(Material.ARROW, viewer, "gui.nav-next-name", List.of("gui.nav-next-lore"), navPlaceholders));

        if (guiConfig.closeSlot() >= 0) {
            setIfConfigured(inventory, guiConfig.closeSlot(),
                    nav(Material.BARRIER, viewer, "gui.nav-center-name", List.of("gui.nav-center-lore"), Map.of()));
        }

        viewer.openInventory(inventory);
    }

    private void renderEmptyColumn(Inventory inventory, Player viewer, int column, int wardrobeSlot) {
        Map<String, String> placeholders = Map.of("slot", String.valueOf(wardrobeSlot));
        ItemStack empty = buildTemplate(viewer, guiConfig.emptySlotTemplate(), placeholders);

        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.helmetRow()), empty);
        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.chestplateRow()), empty);
        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.leggingsRow()), empty);
        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.bootsRow()), empty);
    }

    private void renderLockedColumn(Inventory inventory, Player viewer, int column, int wardrobeSlot, int maxSlots) {
        Map<String, String> placeholders = Map.of("slot", String.valueOf(wardrobeSlot), "max",
                String.valueOf(maxSlots));
        ItemStack locked = buildTemplate(viewer, guiConfig.lockedSlotTemplate(), placeholders);

        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.helmetRow()), locked);
        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.chestplateRow()), locked);
        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.leggingsRow()), locked);
        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.bootsRow()), locked);
    }

    private void renderCooldownColumn(Inventory inventory, Player viewer, int column, WardrobeSet set,
            long cooldownMillis) {
        Map<String, String> placeholders = Map.of(
                "slot", String.valueOf(set.slot()),
                "name", set.name(),
                "seconds", String.valueOf(Math.max(1L, (cooldownMillis + 999) / 1000)));
        ItemStack cooldown = buildTemplate(viewer, guiConfig.cooldownSlotTemplate(), placeholders);

        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.helmetRow()), cooldown);
        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.chestplateRow()), cooldown);
        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.leggingsRow()), cooldown);
        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.bootsRow()), cooldown);
    }

    private void renderSavedColumn(Inventory inventory, Player viewer, int column, WardrobeSet set, boolean selected) {
        Map<String, String> placeholders = Map.of(
                "slot", String.valueOf(set.slot()),
                "name", set.name());

        // Helmet
        ItemStack helmet = set.helmet() != null && !set.helmet().getType().isAir()
                ? createArmorDisplay(set.helmet(), viewer, placeholders, selected)
                : buildTemplate(viewer, guiConfig.emptySlotTemplate(), placeholders);
        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.helmetRow()), helmet);

        // Chestplate
        ItemStack chest = set.chestplate() != null && !set.chestplate().getType().isAir()
                ? createArmorDisplay(set.chestplate(), viewer, placeholders, selected)
                : buildTemplate(viewer, guiConfig.emptySlotTemplate(), placeholders);
        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.chestplateRow()), chest);

        // Leggings
        ItemStack legs = set.leggings() != null && !set.leggings().getType().isAir()
                ? createArmorDisplay(set.leggings(), viewer, placeholders, selected)
                : buildTemplate(viewer, guiConfig.emptySlotTemplate(), placeholders);
        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.leggingsRow()), legs);

        // Boots
        ItemStack boots = set.boots() != null && !set.boots().getType().isAir()
                ? createArmorDisplay(set.boots(), viewer, placeholders, selected)
                : buildTemplate(viewer, guiConfig.emptySlotTemplate(), placeholders);
        setIfConfigured(inventory, guiConfig.getSlotForColumn(column, guiConfig.bootsRow()), boots);
    }

    private ItemStack createArmorDisplay(ItemStack originalArmor, Player viewer, Map<String, String> placeholders,
            boolean selected) {
        ItemStack display = originalArmor.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<Component> lore = resolveLore(viewer, List.of("gui.slot-saved-lore"), placeholders);
            meta.lore(lore);

            if (selected) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            display.setItemMeta(meta);
        }
        return display;
    }

    private void executeAndRefresh(Player viewer, GuiSession session, CompletableFuture<WardrobeResult> future) {
        if (!beginAction(viewer)) {
            return;
        }
        future.thenAccept(result -> schedulerAdapter.runPlayer(viewer, () -> {
            applyFeedback(viewer, result);
            endAction(viewer);
            openMain(viewer, session.targetId(), session.page());
        })).exceptionally(ex -> {
            schedulerAdapter.runPlayer(viewer, () -> {
                messageService.send(viewer, "error.storage", Map.of("reason", ex.getMessage()));
                endAction(viewer);
            });
            return null;
        });
    }

    private void applyFeedback(Player player, WardrobeResult result) {
        messageService.send(player, result.messageKey(), result.placeholders());
        if (pluginConfig.guiBehaviorSettings().actionBarEnabled()) {
            player.sendActionBar(messageService.component(player, result.messageKey(), result.placeholders()));
        }
        if (pluginConfig.guiBehaviorSettings().soundsEnabled()) {
            player.playSound(
                    player.getLocation(),
                    result.isSuccess() ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.ENTITY_VILLAGER_NO,
                    0.8f,
                    result.isSuccess() ? 1.2f : 0.8f);
        }
    }

    private ItemStack nav(Material material, Player player, String nameKey, List<String> loreKeys,
            Map<String, String> placeholders) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(messageService.component(player, nameKey, placeholders));
        meta.lore(resolveLore(player, loreKeys, placeholders));
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private ItemStack buildTemplate(Player player, GuiConfig.GuiItemTemplate template,
            Map<String, String> placeholders) {
        Material material = template.material();
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(messageService.component(player, template.nameKey(), placeholders));
        meta.lore(resolveLore(player, template.loreKeys(), placeholders));
        if (template.modelData() > 0) {
            meta.setCustomModelData(template.modelData());
        }
        if (template.glow()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private boolean slotMatches(int configSlot, int clickedSlot) {
        return configSlot >= 0 && configSlot == clickedSlot;
    }

    private void setIfConfigured(Inventory inventory, int slot, ItemStack itemStack) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, itemStack);
    }

    private int slotsPerPage() {
        return Math.max(1, guiConfig.slotDisplayIndices().size());
    }

    private ArmorPiece resolveArmorPieceFromSlot(int clickedSlot) {
        int row = clickedSlot / 9;
        if (row == guiConfig.helmetRow()) {
            return ArmorPiece.HELMET;
        }
        if (row == guiConfig.chestplateRow()) {
            return ArmorPiece.CHESTPLATE;
        }
        if (row == guiConfig.leggingsRow()) {
            return ArmorPiece.LEGGINGS;
        }
        if (row == guiConfig.bootsRow()) {
            return ArmorPiece.BOOTS;
        }
        return null;
    }

    private ItemStack consumeSingleCursorItem(Player viewer) {
        ItemStack cursor = viewer.getItemOnCursor();
        if (cursor == null || cursor.getType().isAir()) {
            return null;
        }
        ItemStack consumed = cursor.clone();
        consumed.setAmount(1);
        if (cursor.getAmount() <= 1) {
            viewer.setItemOnCursor(null);
        } else {
            ItemStack remaining = cursor.clone();
            remaining.setAmount(cursor.getAmount() - 1);
            viewer.setItemOnCursor(remaining);
        }
        return consumed;
    }

    private ItemStack resolvePickupReward(Player viewer, UUID targetId, int wardrobeSlot, ArmorPiece piece,
            ItemStack fallback) {
        if (fallback == null || fallback.getType().isAir()) {
            return fallback;
        }
        if (!viewer.getUniqueId().equals(targetId)) {
            return fallback.clone();
        }
        ItemStack equipped = switch (piece) {
            case HELMET -> viewer.getInventory().getHelmet();
            case CHESTPLATE -> viewer.getInventory().getChestplate();
            case LEGGINGS -> viewer.getInventory().getLeggings();
            case BOOTS -> viewer.getInventory().getBoots();
        };
        if (!bindingService.isBoundTo(equipped, targetId)) {
            return fallback.clone();
        }
        if (bindingService.getBoundSlot(equipped) != wardrobeSlot) {
            return fallback.clone();
        }
        if (!piece.key().equals(bindingService.getBoundPiece(equipped))) {
            return fallback.clone();
        }
        ItemStack live = bindingService.unbindCopy(equipped);
        if (live == null || live.getType().isAir()) {
            return fallback.clone();
        }
        return live;
    }

    private void restoreCursorItem(Player viewer, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        ItemStack cursor = viewer.getItemOnCursor();
        if (cursor == null || cursor.getType().isAir()) {
            viewer.setItemOnCursor(itemStack.clone());
            return;
        }
        Map<Integer, ItemStack> leftovers = viewer.getInventory().addItem(itemStack.clone());
        for (ItemStack leftover : leftovers.values()) {
            viewer.getWorld().dropItemNaturally(viewer.getLocation(), leftover);
        }
    }

    private boolean isAir(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir();
    }

    private List<Component> resolveLore(Player player, List<String> loreKeys, Map<String, String> placeholders) {
        List<Component> resolved = new ArrayList<>();
        for (String loreKey : loreKeys) {
            resolved.addAll(messageService.componentList(player, loreKey, placeholders));
        }
        return resolved;
    }

    private boolean beginAction(Player viewer) {
        return pendingActions.add(viewer.getUniqueId());
    }

    private void endAction(Player viewer) {
        pendingActions.remove(viewer.getUniqueId());
    }

    private enum ArmorPiece {
        HELMET("helmet") {
            @Override
            boolean matches(Material material) {
                return ArmorPieceMatcher.matchesWardrobePiece(key(), material);
            }

            @Override
            ItemStack read(WardrobeSet set) {
                return set.helmet();
            }
        },
        CHESTPLATE("chestplate") {
            @Override
            boolean matches(Material material) {
                return ArmorPieceMatcher.matchesWardrobePiece(key(), material);
            }

            @Override
            ItemStack read(WardrobeSet set) {
                return set.chestplate();
            }
        },
        LEGGINGS("leggings") {
            @Override
            boolean matches(Material material) {
                return ArmorPieceMatcher.matchesWardrobePiece(key(), material);
            }

            @Override
            ItemStack read(WardrobeSet set) {
                return set.leggings();
            }
        },
        BOOTS("boots") {
            @Override
            boolean matches(Material material) {
                return ArmorPieceMatcher.matchesWardrobePiece(key(), material);
            }

            @Override
            ItemStack read(WardrobeSet set) {
                return set.boots();
            }
        };

        private final String key;

        ArmorPiece(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }

        abstract boolean matches(Material material);

        abstract ItemStack read(WardrobeSet set);
    }
}
