package dev.rique.ruinedwardrobe.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class SnapshotMigrationSupport {

    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private SnapshotMigrationSupport() {
    }

    static WardrobeRepository.MigrationSnapshot normalize(WardrobeRepository.MigrationSnapshot snapshot) {
        Map<String, WardrobeRepository.SetRow> setsByKey = new LinkedHashMap<>();
        for (WardrobeRepository.SetRow row : snapshot.sets()) {
            if (row.playerId() == null) {
                throw new IllegalStateException("Snapshot contains a wardrobe row with no player UUID");
            }
            if (row.slot() < 1) {
                throw new IllegalStateException("Snapshot contains an invalid slot index " + row.slot() + " for " + row.playerId());
            }
            WardrobeRepository.SetRow sanitized = sanitizeSetRow(row);
            if (!hasAnyArmorPayload(sanitized)) {
                continue;
            }
            String key = row.playerId() + "#" + row.slot();
            if (setsByKey.putIfAbsent(key, sanitized) != null) {
                throw new IllegalStateException("Snapshot contains duplicate wardrobe slot rows for " + key);
            }
        }

        Map<UUID, List<Integer>> slotsByPlayer = new LinkedHashMap<>();
        for (WardrobeRepository.SetRow row : setsByKey.values()) {
            slotsByPlayer.computeIfAbsent(row.playerId(), ignored -> new ArrayList<>()).add(row.slot());
        }

        Map<UUID, WardrobeRepository.PlayerRow> playersById = new LinkedHashMap<>();
        for (WardrobeRepository.PlayerRow row : snapshot.players()) {
            if (row.playerId() == null) {
                throw new IllegalStateException("Snapshot contains a player row with no UUID");
            }
            if (playersById.putIfAbsent(row.playerId(), sanitizePlayerRow(row, slotsByPlayer)) != null) {
                throw new IllegalStateException("Snapshot contains duplicate player rows for " + row.playerId());
            }
        }

        for (UUID playerId : slotsByPlayer.keySet()) {
            playersById.computeIfAbsent(playerId, ignored -> new WardrobeRepository.PlayerRow(playerId, 0, -1, 0L, 0L));
        }

        Map<String, WardrobeRepository.MetaRow> metaByKey = new LinkedHashMap<>();
        for (WardrobeRepository.MetaRow row : snapshot.metaRows()) {
            if (row.key() == null || row.key().isBlank()) {
                continue;
            }
            metaByKey.put(row.key(), new WardrobeRepository.MetaRow(row.key(), row.value() == null ? "" : row.value()));
        }

        return new WardrobeRepository.MigrationSnapshot(
                new ArrayList<>(playersById.values()),
                new ArrayList<>(setsByKey.values()),
                new ArrayList<>(metaByKey.values()));
    }

    static File writeBackup(
            File dataFolder,
            String filenamePrefix,
            Map<String, String> details,
            WardrobeRepository.MigrationSnapshot snapshot
    ) throws IOException {
        File backupDirectory = new File(dataFolder, "backups");
        if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
            throw new IOException("Failed to create backup directory " + backupDirectory.getAbsolutePath());
        }

        String filename = filenamePrefix + "-" + BACKUP_STAMP.format(Instant.now()) + ".yml";
        File backupFile = new File(backupDirectory, filename);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("backup-version", 1);
        yaml.set("created-at", Instant.now().toString());
        for (Map.Entry<String, String> entry : details.entrySet()) {
            yaml.set("details." + entry.getKey(), entry.getValue());
        }
        yaml.set("summary.players", snapshot.players().size());
        yaml.set("summary.sets", snapshot.sets().size());
        yaml.set("summary.meta", snapshot.metaRows().size());
        yaml.set("summary.digest", snapshotDigest(snapshot));
        yaml.set("players", snapshot.players().stream().map(SnapshotMigrationSupport::playerBackupRow).toList());
        yaml.set("sets", snapshot.sets().stream().map(SnapshotMigrationSupport::setBackupRow).toList());
        yaml.set("meta", snapshot.metaRows().stream().map(SnapshotMigrationSupport::metaBackupRow).toList());
        yaml.save(backupFile);
        return backupFile;
    }

    static String snapshotDigest(WardrobeRepository.MigrationSnapshot snapshot) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            snapshot.players().stream()
                    .sorted(Comparator.comparing(WardrobeRepository.PlayerRow::playerId))
                    .forEach(row -> updateDigest(digest,
                            row.playerId(),
                            row.bonusSlots(),
                            row.selectedSlot(),
                            row.version(),
                            row.updatedAt()));
            snapshot.sets().stream()
                    .sorted(Comparator.comparing(WardrobeRepository.SetRow::playerId)
                            .thenComparingInt(WardrobeRepository.SetRow::slot))
                    .forEach(row -> updateDigest(digest,
                            row.playerId(),
                            row.slot(),
                            row.name(),
                            row.favorite(),
                            row.helmetData(),
                            row.chestData(),
                            row.legsData(),
                            row.bootsData(),
                            row.createdAt(),
                            row.updatedAt(),
                            row.version()));
            snapshot.metaRows().stream()
                    .sorted(Comparator.comparing(WardrobeRepository.MetaRow::key))
                    .forEach(row -> updateDigest(digest, row.key(), row.value()));
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        if (current == null || current.getMessage() == null || current.getMessage().isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return current.getMessage();
    }

    private static WardrobeRepository.PlayerRow sanitizePlayerRow(
            WardrobeRepository.PlayerRow row,
            Map<UUID, List<Integer>> slotsByPlayer) {
        int selectedSlot = row.selectedSlot();
        List<Integer> slots = slotsByPlayer.getOrDefault(row.playerId(), List.of());
        if (selectedSlot < 1 || !slots.contains(selectedSlot)) {
            selectedSlot = -1;
        }
        return new WardrobeRepository.PlayerRow(
                row.playerId(),
                Math.max(0, row.bonusSlots()),
                selectedSlot,
                Math.max(0L, row.version()),
                Math.max(0L, row.updatedAt()));
    }

    private static WardrobeRepository.SetRow sanitizeSetRow(WardrobeRepository.SetRow row) {
        String name = row.name() == null ? "" : row.name().trim();
        if (name.isBlank()) {
            name = "Slot " + row.slot();
        }
        if (name.length() > 48) {
            name = name.substring(0, 48);
        }
        long createdAt = Math.max(0L, row.createdAt());
        long updatedAt = Math.max(createdAt, Math.max(0L, row.updatedAt()));
        return new WardrobeRepository.SetRow(
                row.playerId(),
                row.slot(),
                name,
                row.favorite(),
                blankToNull(row.helmetData()),
                blankToNull(row.chestData()),
                blankToNull(row.legsData()),
                blankToNull(row.bootsData()),
                createdAt,
                updatedAt,
                Math.max(0L, row.version()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean hasAnyArmorPayload(WardrobeRepository.SetRow row) {
        return row.helmetData() != null
                || row.chestData() != null
                || row.legsData() != null
                || row.bootsData() != null;
    }

    private static Map<String, Object> playerBackupRow(WardrobeRepository.PlayerRow row) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("player_uuid", row.playerId().toString());
        values.put("bonus_slots", row.bonusSlots());
        values.put("selected_slot", row.selectedSlot());
        values.put("version", row.version());
        values.put("updated_at", row.updatedAt());
        return values;
    }

    private static Map<String, Object> setBackupRow(WardrobeRepository.SetRow row) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("player_uuid", row.playerId().toString());
        values.put("slot_index", row.slot());
        values.put("slot_name", row.name());
        values.put("favorite", row.favorite());
        values.put("helmet_data", row.helmetData());
        values.put("chest_data", row.chestData());
        values.put("legs_data", row.legsData());
        values.put("boots_data", row.bootsData());
        values.put("created_at", row.createdAt());
        values.put("updated_at", row.updatedAt());
        values.put("version", row.version());
        return values;
    }

    private static Map<String, Object> metaBackupRow(WardrobeRepository.MetaRow row) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("key", row.key());
        values.put("value", row.value());
        return values;
    }

    private static void updateDigest(MessageDigest digest, Object... values) {
        for (Object value : values) {
            digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }
}
