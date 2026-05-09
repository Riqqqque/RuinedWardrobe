package dev.rique.ruinedwardrobe.lang;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LanguageRegistry {

    private final JavaPlugin plugin;
    private final Map<String, Integer> fallbackCountByKey = new ConcurrentHashMap<>();
    private FileConfiguration fallback;
    private FileConfiguration active;
    private String activeLocale = "en_US";

    public LanguageRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String locale) {
        ensureDefault("lang/en_US.yml");
        activeLocale = locale == null || locale.isBlank() ? "en_US" : locale;
        fallbackCountByKey.clear();
        fallback = loadLocale("en_US");
        active = loadLocale(activeLocale);
    }

    public String activeLocale() {
        return activeLocale;
    }

    public String get(String key) {
        String value = active.getString(key);
        if (value != null) {
            return value;
        }
        fallbackCountByKey.merge(key, 1, Integer::sum);
        return fallback.getString(key, key);
    }

    public List<String> getList(String key) {
        if (active.isString(key)) {
            return Collections.singletonList(active.getString(key, key));
        }
        List<String> values = active.getStringList(key);
        if (!values.isEmpty()) {
            return values;
        }
        if (fallback.isString(key)) {
            fallbackCountByKey.merge(key, 1, Integer::sum);
            return Collections.singletonList(fallback.getString(key, key));
        }
        List<String> fallbackValues = fallback.getStringList(key);
        if (!fallbackValues.isEmpty()) {
            fallbackCountByKey.merge(key, 1, Integer::sum);
            return fallbackValues;
        }
        return Collections.singletonList(key);
    }

    public Map<String, Integer> getFallbackCountByKey() {
        return Collections.unmodifiableMap(fallbackCountByKey);
    }

    private FileConfiguration loadLocale(String locale) {
        String normalized = locale.trim();
        ensureDefault("lang/" + normalized + ".yml");
        File file = new File(plugin.getDataFolder(), "lang" + File.separator + normalized + ".yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "lang" + File.separator + "en_US.yml");
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        var defaultStream = plugin.getResource("lang/en_US.yml");
        if (defaultStream != null) {
            try (defaultStream) {
                YamlConfiguration bundledDefaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                loaded.setDefaults(bundledDefaults);
            } catch (Exception ignored) {
            }
        }
        return loaded;
    }

    private void ensureDefault(String path) {
        File file = new File(plugin.getDataFolder(), path.replace('/', File.separatorChar));
        if (file.exists()) {
            return;
        }
        String normalizedPath = path.toLowerCase(Locale.ROOT);
        if (!normalizedPath.startsWith("lang/")) {
            return;
        }
        try {
            plugin.saveResource(path, false);
        } catch (IllegalArgumentException ignored) {
            // If the locale file is not shipped, fallback loading will use en_US.
        }
    }
}
