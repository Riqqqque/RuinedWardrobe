package dev.rique.ruinedwardrobe.core;

import dev.rique.ruinedwardrobe.config.PluginConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class WardrobeAuditLogger {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String STOP_MARKER = "\u0000";

    private final JavaPlugin plugin;
    private final PluginConfig.AuditSettings settings;
    private final BlockingQueue<String> queue;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong droppedLines = new AtomicLong();
    private Thread writerThread;
    private Path directory;
    private BufferedWriter writer;
    private LocalDate openDate;

    public WardrobeAuditLogger(JavaPlugin plugin, PluginConfig.AuditSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.queue = new ArrayBlockingQueue<>(Math.max(256, settings.queueSize()));
    }

    private WardrobeAuditLogger() {
        this.plugin = null;
        this.settings = new PluginConfig.AuditSettings(false, "logs", 256, false, false, false);
        this.queue = new ArrayBlockingQueue<>(256);
    }

    public static WardrobeAuditLogger disabled() {
        return new WardrobeAuditLogger();
    }

    public void start() {
        if (!settings.enabled() || plugin == null || !running.compareAndSet(false, true)) {
            return;
        }
        directory = resolveDirectory();
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            running.set(false);
            plugin.getLogger().warning("Audit log disabled because the log directory could not be created: " + ex.getMessage());
            return;
        }
        writerThread = new Thread(this::runWriter, "RuinedWardrobe-AuditLog");
        writerThread.setDaemon(true);
        writerThread.start();
        record("AUDIT_START", null, null, Map.of("directory", directory.toString()));
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        queue.offer(STOP_MARKER);
        if (writerThread != null) {
            try {
                writerThread.join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        closeWriter();
    }

    public void record(String action, UUID playerId, String playerName, Map<String, ?> details) {
        if (!settings.enabled() || !running.get()) {
            return;
        }
        String line = formatLine(action, playerId, playerName, details);
        if (queue.offer(line)) {
            return;
        }
        long dropped = droppedLines.incrementAndGet();
        if (plugin != null && (dropped == 1 || dropped % 100 == 0)) {
            plugin.getLogger().warning("Audit log queue is full; dropped " + dropped + " audit lines.");
        }
    }

    public void error(String action, UUID playerId, String playerName, Throwable throwable, Map<String, ?> details) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (details != null) {
            merged.putAll(details);
        }
        merged.put("error", failureMessage(throwable));
        record(action, playerId, playerName, merged);
    }

    public boolean logSuccessfulSyncs() {
        return settings.enabled() && settings.logSuccessfulSyncs();
    }

    public boolean logBlockedActions() {
        return settings.enabled() && settings.logBlockedActions();
    }

    public boolean includeItemSummaries() {
        return settings.enabled() && settings.includeItemSummaries();
    }

    private Path resolveDirectory() {
        Path dataFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path configured = dataFolder.resolve(settings.directory()).normalize();
        if (!configured.startsWith(dataFolder)) {
            plugin.getLogger().warning("audit.directory points outside the plugin folder; using logs instead.");
            return dataFolder.resolve("logs");
        }
        return configured;
    }

    private void runWriter() {
        while (running.get() || !queue.isEmpty()) {
            try {
                String line = queue.poll(1L, TimeUnit.SECONDS);
                if (line == null || STOP_MARKER.equals(line)) {
                    continue;
                }
                writeLine(line);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException ex) {
                if (plugin != null) {
                    plugin.getLogger().warning("Failed to write audit log: " + ex.getMessage());
                }
            }
        }
    }

    private void writeLine(String line) throws IOException {
        LocalDate today = LocalDate.now();
        if (!Objects.equals(openDate, today)) {
            closeWriter();
            Path file = directory.resolve("wardrobe-audit-" + FILE_DATE.format(today) + ".log");
            writer = Files.newBufferedWriter(
                    file,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            openDate = today;
        }
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    private void closeWriter() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException ignored) {
        } finally {
            writer = null;
            openDate = null;
        }
    }

    private String formatLine(String action, UUID playerId, String playerName, Map<String, ?> details) {
        StringBuilder line = new StringBuilder(192);
        line.append(LINE_TIME.format(LocalDateTime.now()));
        line.append(" action=").append(clean(action));
        if (playerId != null) {
            line.append(" playerId=").append(playerId);
        }
        if (playerName != null && !playerName.isBlank()) {
            appendValue(line, "player", playerName);
        }
        if (details != null) {
            for (Map.Entry<String, ?> entry : details.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                appendValue(line, entry.getKey(), entry.getValue());
            }
        }
        return line.toString();
    }

    private void appendValue(StringBuilder line, String key, Object value) {
        line.append(' ').append(clean(key)).append('=');
        if (value instanceof Number || value instanceof Boolean) {
            line.append(value);
            return;
        }
        line.append('"').append(cleanValue(String.valueOf(value))).append('"');
    }

    private String clean(String value) {
        StringBuilder cleaned = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.') {
                cleaned.append(c);
            }
        }
        return cleaned.isEmpty() ? "unknown" : cleaned.toString();
    }

    private String cleanValue(String value) {
        String cleaned = value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('"', '\'');
        return cleaned.length() <= 500 ? cleaned : cleaned.substring(0, 500);
    }

    private String failureMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        if (current == null) {
            return "unknown";
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
