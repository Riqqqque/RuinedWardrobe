package dev.rique.prismwardrobe.command;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import dev.rique.prismwardrobe.api.event.WardrobeDataReloadedEvent;
import dev.rique.prismwardrobe.api.event.WardrobeSlotsChangedEvent;
import dev.rique.prismwardrobe.api.model.WardrobeProfile;
import dev.rique.prismwardrobe.cache.WardrobeCache;
import dev.rique.prismwardrobe.config.DatabaseType;
import dev.rique.prismwardrobe.config.PluginConfig;
import dev.rique.prismwardrobe.core.SlotLimitServiceImpl;
import dev.rique.prismwardrobe.core.VersionSyncService;
import dev.rique.prismwardrobe.core.WardrobeServiceImpl;
import dev.rique.prismwardrobe.gui.WardrobeGuiController;
import dev.rique.prismwardrobe.lang.LanguageRegistry;
import dev.rique.prismwardrobe.lang.MessageService;
import dev.rique.prismwardrobe.scheduler.SchedulerAdapter;
import dev.rique.prismwardrobe.storage.DatabaseManager;
import dev.rique.prismwardrobe.storage.MigrationService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class WardrobeCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final LanguageRegistry languageRegistry;
    private final MessageService messageService;
    private final WardrobeServiceImpl wardrobeService;
    private final SlotLimitServiceImpl slotLimitService;
    private final WardrobeGuiController guiController;
    private final MigrationService migrationService;
    private final DatabaseManager databaseManager;
    private final WardrobeCache wardrobeCache;
    private final VersionSyncService versionSyncService;
    private final PluginConfig pluginConfig;
    private final SchedulerAdapter schedulerAdapter;
    private final Runnable reloadAction;

    public WardrobeCommand(
            JavaPlugin plugin,
            LanguageRegistry languageRegistry,
            MessageService messageService,
            WardrobeServiceImpl wardrobeService,
            SlotLimitServiceImpl slotLimitService,
            WardrobeGuiController guiController,
            MigrationService migrationService,
            DatabaseManager databaseManager,
            WardrobeCache wardrobeCache,
            VersionSyncService versionSyncService,
            PluginConfig pluginConfig,
            SchedulerAdapter schedulerAdapter,
            Runnable reloadAction) {
        this.plugin = plugin;
        this.languageRegistry = languageRegistry;
        this.messageService = messageService;
        this.wardrobeService = wardrobeService;
        this.slotLimitService = slotLimitService;
        this.guiController = guiController;
        this.migrationService = migrationService;
        this.databaseManager = databaseManager;
        this.wardrobeCache = wardrobeCache;
        this.versionSyncService = versionSyncService;
        this.pluginConfig = pluginConfig;
        this.schedulerAdapter = schedulerAdapter;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (!sender.hasPermission("prismwardrobe.use")) {
                    messageService.send(sender, "error.no-permission");
                    return true;
                }
                guiController.openMain(player, player.getUniqueId(), 0);
                return true;
            }
            handleHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> handleHelp(sender, label);
            case "list" -> handleList(sender, args);
            case "doctor" -> handleDoctor(sender);
            case "reload" -> handleReload(sender);
            case "migrate" -> handleMigrate(sender, args);
            case "admin" -> handleAdmin(sender, args);
            default -> {
                messageService.send(sender, "error.unknown-subcommand");
                handleHelp(sender, label);
            }
        }
        return true;
    }

    private void handleHelp(CommandSender sender, String label) {
        if (!sender.hasPermission("prismwardrobe.command.help")) {
            messageService.send(sender, "error.no-permission");
            return;
        }
        Map<String, String> placeholders = Map.of("root", "/" + label.toLowerCase(Locale.ROOT));
        messageService.send(sender, "help.header", placeholders);
        messageService.send(sender, "help.open", placeholders);
        if (sender.hasPermission("prismwardrobe.command.list")) {
            messageService.send(sender, "help.list", placeholders);
        }
        if (sender.hasPermission("prismwardrobe.command.doctor")) {
            messageService.send(sender, "help.doctor", placeholders);
        }
        if (sender.hasPermission("prismwardrobe.command.reload")) {
            messageService.send(sender, "help.reload", placeholders);
        }
        if (sender.hasPermission("prismwardrobe.command.migrate")) {
            messageService.send(sender, "help.migrate", placeholders);
        }
        if (sender.hasPermission("prismwardrobe.admin")) {
            messageService.send(sender, "help.admin-open", placeholders);
            messageService.send(sender, "help.admin-setslots", placeholders);
            messageService.send(sender, "help.admin-clearslots", placeholders);
        }
        messageService.send(sender, "help.footer", placeholders);
    }

    private void handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("prismwardrobe.command.list")) {
            messageService.send(sender, "error.no-permission");
            return;
        }
        UUID targetId;
        String targetName;
        if (args.length >= 2) {
            OfflinePlayer target = resolveKnownPlayer(args[1]);
            if (target == null) {
                messageService.send(sender, "error.player-not-found", Map.of("player", args[1]));
                return;
            }
            targetId = target.getUniqueId();
            targetName = target.getName() == null ? targetId.toString() : target.getName();
        } else {
            if (!(sender instanceof Player player)) {
                messageService.send(sender, "usage.list");
                return;
            }
            targetId = player.getUniqueId();
            targetName = player.getName();
        }
        wardrobeService.getProfile(targetId).thenAccept(profile -> runGlobal(() -> sendList(sender, targetName, profile)))
                .exceptionally(ex -> {
                    sendStorageError(sender, ex);
                    return null;
                });
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("prismwardrobe.command.reload")) {
            messageService.send(sender, "error.no-permission");
            return;
        }
        reloadAction.run();
        Bukkit.getPluginManager().callEvent(new WardrobeDataReloadedEvent());
        messageService.send(sender, "success.reloaded");
        if (!languageRegistry.getFallbackCountByKey().isEmpty()) {
            plugin.getLogger().info("Lang fallback keys in use: " + languageRegistry.getFallbackCountByKey().size());
        }
    }

    private void handleDoctor(CommandSender sender) {
        if (!sender.hasPermission("prismwardrobe.command.doctor")) {
            messageService.send(sender, "error.no-permission");
            return;
        }

        CacheStats stats = wardrobeCache.stats();
        messageService.send(sender, "doctor.header");
        messageService.send(sender, "doctor.storage", Map.of(
                "storage", pluginConfig.storageSettings().type().name(),
                "ready", databaseManager.isReady() ? "yes" : "no",
                "active", String.valueOf(databaseManager.poolActiveConnections())));
        messageService.send(sender, "doctor.players", Map.of(
                "online", String.valueOf(Bukkit.getOnlinePlayers().size())));
        messageService.send(sender, "doctor.cache", Map.of(
                "size", String.valueOf(wardrobeCache.size()),
                "hit_rate", formatDouble(stats.hitRate()),
                "hits", String.valueOf(stats.hitCount()),
                "misses", String.valueOf(stats.missCount())));
        messageService.send(sender, "doctor.queue", Map.of(
                "depth", String.valueOf(databaseManager.queueDepth()),
                "latency_ms", formatDouble(databaseManager.averageLatencyMs())));
        messageService.send(sender, "doctor.sync", Map.of(
                "poll_ms", String.valueOf(versionSyncService.lastPollMs()),
                "poll_seconds", String.valueOf(pluginConfig.syncSettings().pollSeconds()),
                "batch", String.valueOf(pluginConfig.syncSettings().batchSize())));
        databaseManager.runQuery(connection -> {
            try (var statement = connection.prepareStatement("SELECT 1")) {
                statement.execute();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return true;
        }).thenAccept(ignored -> runGlobal(() -> messageService.send(sender, "doctor.probe-ok")))
                .exceptionally(ex -> {
                    runGlobal(() -> messageService.send(sender, "doctor.probe-failed",
                            Map.of("reason", sanitizeThrowableMessage(ex))));
                    return null;
                });
    }

    private void handleMigrate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("prismwardrobe.command.migrate")) {
            messageService.send(sender, "error.no-permission");
            return;
        }
        if (args.length < 2) {
            messageService.send(sender, "usage.migrate");
            return;
        }
        if (!args[1].equalsIgnoreCase("sqlite") && !args[1].equalsIgnoreCase("mysql")) {
            messageService.send(sender, "usage.migrate");
            return;
        }
        DatabaseType target = DatabaseType.parse(args[1]);
        boolean dryRun = Arrays.stream(args).anyMatch(s -> s.equalsIgnoreCase("--dry-run"));
        migrationService.migrate(target, dryRun).thenAccept(result -> {
            String key = result.success() ? "success.migration-complete" : "error.migration-failed";
            runGlobal(() -> messageService.send(sender, key, Map.of(
                    "players", String.valueOf(result.players()),
                    "sets", String.valueOf(result.sets()),
                    "meta", String.valueOf(result.metaRows()),
                    "message", sanitizeMessage(result.message()),
                    "target", target.name())));
        }).exceptionally(ex -> {
            runGlobal(() -> messageService.send(sender, "error.migration-failed",
                    Map.of("message", sanitizeThrowableMessage(ex))));
            return null;
        });
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("prismwardrobe.admin")) {
            messageService.send(sender, "error.no-permission");
            return;
        }
        if (args.length < 2) {
            messageService.send(sender, "usage.admin");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "open" -> {
                if (!(sender instanceof Player player)) {
                    messageService.send(sender, "error.player-only");
                    return;
                }
                if (args.length < 3) {
                    messageService.send(sender, "usage.admin-open");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    messageService.send(sender, "error.player-not-found", Map.of("player", args[2]));
                    return;
                }
                guiController.openMain(player, target.getUniqueId(), 0);
            }
            case "setslots" -> {
                if (args.length < 4) {
                    messageService.send(sender, "usage.admin-setslots");
                    return;
                }
                OfflinePlayer target = resolveKnownPlayer(args[2]);
                if (target == null) {
                    messageService.send(sender, "error.player-not-found", Map.of("player", args[2]));
                    return;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    messageService.send(sender, "error.invalid-number", Map.of("value", args[3]));
                    return;
                }
                if (amount < 0) {
                    messageService.send(sender, "error.invalid-number", Map.of("value", args[3]));
                    return;
                }
                UUID targetId = target.getUniqueId();
                slotLimitService.getBonusSlots(targetId).thenCompose(
                        oldValue -> slotLimitService.setBonusSlots(targetId, amount).thenApply(ignored -> oldValue))
                        .thenAccept(oldValue -> {
                            runGlobal(() -> {
                                Bukkit.getPluginManager()
                                        .callEvent(new WardrobeSlotsChangedEvent(targetId, oldValue, amount));
                                messageService.send(sender, "success.admin-setslots", Map.of(
                                        "player", target.getName() == null ? targetId.toString() : target.getName(),
                                        "old", String.valueOf(oldValue),
                                        "new", String.valueOf(amount)));
                            });
                        })
                        .exceptionally(ex -> {
                            sendStorageError(sender, ex);
                            return null;
                        });
            }
            case "clearslots" -> {
                if (args.length < 3) {
                    messageService.send(sender, "usage.admin-clearslots");
                    return;
                }
                OfflinePlayer target = resolveKnownPlayer(args[2]);
                if (target == null) {
                    messageService.send(sender, "error.player-not-found", Map.of("player", args[2]));
                    return;
                }
                UUID targetId = target.getUniqueId();
                slotLimitService.getBonusSlots(targetId)
                        .thenCompose(
                                oldValue -> slotLimitService.setBonusSlots(targetId, 0).thenApply(ignored -> oldValue))
                        .thenAccept(oldValue -> {
                            runGlobal(() -> {
                                Bukkit.getPluginManager().callEvent(new WardrobeSlotsChangedEvent(targetId, oldValue, 0));
                                messageService.send(sender, "success.admin-clearslots", Map.of(
                                        "player", target.getName() == null ? targetId.toString() : target.getName()));
                            });
                        })
                        .exceptionally(ex -> {
                            sendStorageError(sender, ex);
                            return null;
                        });
            }
            default -> messageService.send(sender, "usage.admin");
        }
    }

    private void sendList(CommandSender sender, String targetName, WardrobeProfile profile) {
        messageService.send(sender, "list.header", Map.of("player", targetName));
        List<Integer> slots = new ArrayList<>(profile.sets().keySet());
        Collections.sort(slots);
        if (slots.isEmpty()) {
            messageService.send(sender, "list.empty");
            return;
        }
        for (Integer slot : slots) {
            var set = profile.getSet(slot);
            if (set == null) {
                continue;
            }
            messageService.send(sender, "list.entry", Map.of(
                    "slot", String.valueOf(slot),
                    "name", set.name()));
        }
    }

    private void sendStorageError(CommandSender sender, Throwable throwable) {
        runGlobal(() -> messageService.send(sender, "error.storage",
                Map.of("reason", sanitizeThrowableMessage(throwable))));
    }

    private String sanitizeThrowableMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        if (current == null) {
            return "unknown";
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        }
        return sanitizeMessage(message);
    }

    private String sanitizeMessage(String message) {
        String normalized = Objects.toString(message, "unknown")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        if (normalized.isEmpty()) {
            return "unknown";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("access denied for user")) {
            return "database authentication failed (check username/password)";
        }
        if (lower.contains("connection refused")
                || lower.contains("communications link failure")
                || lower.contains("connect timed out")
                || lower.contains("connection timed out")
                || lower.contains("unknown host")) {
            return "unable to reach database host (check host/port/network)";
        }
        if (lower.contains("source and target storage are the same")) {
            return "source and target storage are the same";
        }
        if (lower.contains("no suitable driver")) {
            return "database driver is not available for the selected storage type";
        }
        return stripExceptionPrefix(normalized);
    }

    private String stripExceptionPrefix(String message) {
        String stripped = message;
        for (int i = 0; i < 4; i++) {
            int separator = stripped.indexOf(':');
            if (separator <= 0 || separator >= stripped.length() - 1) {
                break;
            }
            String prefix = stripped.substring(0, separator).trim();
            if (prefix.contains(".") || prefix.endsWith("Exception") || prefix.endsWith("Error")) {
                stripped = stripped.substring(separator + 1).trim();
                continue;
            }
            break;
        }
        return stripped;
    }

    private void runGlobal(Runnable runnable) {
        schedulerAdapter.runGlobal(runnable);
    }

    private OfflinePlayer resolveKnownPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore()) {
            return offline;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> roots = new ArrayList<>();
            if (sender.hasPermission("prismwardrobe.command.help")) {
                roots.add("help");
            }
            if (sender.hasPermission("prismwardrobe.command.list")) {
                roots.add("list");
            }
            if (sender.hasPermission("prismwardrobe.command.doctor")) {
                roots.add("doctor");
            }
            if (sender.hasPermission("prismwardrobe.command.reload")) {
                roots.add("reload");
            }
            if (sender.hasPermission("prismwardrobe.command.migrate")) {
                roots.add("migrate");
            }
            if (sender.hasPermission("prismwardrobe.admin")) {
                roots.add("admin");
            }
            return filterByPrefix(roots, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "admin" -> sender.hasPermission("prismwardrobe.admin")
                        ? filterByPrefix(List.of("open", "setslots", "clearslots"), args[1])
                        : List.of();
                case "migrate" -> sender.hasPermission("prismwardrobe.command.migrate")
                        ? filterByPrefix(List.of("sqlite", "mysql"), args[1])
                        : List.of();
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("prismwardrobe.admin")) {
            return filterByPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()),
                    args[2]);
        }
        return List.of();
    }

    private List<String> filterByPrefix(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
