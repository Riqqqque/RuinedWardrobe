package dev.rique.ruinedwardrobe.config;

public enum DatabaseType {
    SQLITE,
    MYSQL;

    public static DatabaseType parse(String input) {
        if (input == null) {
            return SQLITE;
        }
        try {
            return DatabaseType.valueOf(input.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return SQLITE;
        }
    }
}

