package dev.rique.ruinedwardrobe.lang;

import dev.rique.ruinedwardrobe.config.MessageFormatMode;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MessageService {

    private final LanguageRegistry languageRegistry;
    private final MessageFormatMode formatMode;
    private final boolean placeholderApiEnabled;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    public MessageService(LanguageRegistry languageRegistry, MessageFormatMode formatMode, boolean placeholderApiEnabled) {
        this.languageRegistry = languageRegistry;
        this.formatMode = formatMode;
        this.placeholderApiEnabled = placeholderApiEnabled && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public Component component(String key) {
        return component(null, key, Map.of());
    }

    public Component component(Player player, String key, Map<String, String> placeholders) {
        String raw = languageRegistry.get(key);
        return parse(player, applyPlaceholders(player, raw, placeholders));
    }

    public List<Component> componentList(Player player, String key, Map<String, String> placeholders) {
        return languageRegistry.getList(key).stream()
                .map(line -> parse(player, applyPlaceholders(player, line, placeholders)))
                .toList();
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        Player player = sender instanceof Player p ? p : null;
        sender.sendMessage(component(player, key, placeholders));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    private String applyPlaceholders(Player player, String text, Map<String, String> placeholders) {
        Map<String, String> merged = new HashMap<>();
        if (placeholders != null) {
            merged.putAll(placeholders);
        }
        String output = text;
        boolean miniMessageTarget = shouldUseMiniMessage(output);
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", placeholderValue(entry.getValue(), miniMessageTarget));
        }
        if (placeholderApiEnabled && player != null) {
            output = PlaceholderAPI.setPlaceholders(player, output);
        }
        return output;
    }

    private Component parse(Player player, String text) {
        try {
            return switch (formatMode) {
                case LEGACY -> legacySerializer.deserialize(text);
                case MINIMESSAGE -> miniMessage.deserialize(text);
                case BOTH -> {
                    if (shouldUseMiniMessage(text)) {
                        yield miniMessage.deserialize(text);
                    }
                    yield legacySerializer.deserialize(text);
                }
            };
        } catch (RuntimeException ex) {
            return legacySerializer.deserialize(text);
        }
    }

    private boolean shouldUseMiniMessage(String text) {
        return formatMode == MessageFormatMode.MINIMESSAGE
                || (formatMode == MessageFormatMode.BOTH && text.contains("<") && text.contains(">"));
    }

    private String placeholderValue(String value, boolean miniMessageTarget) {
        String safeValue = value == null ? "" : value;
        return miniMessageTarget ? miniMessage.escapeTags(safeValue) : safeValue;
    }
}

