package dev.rique.prismwardrobe.config;

public enum MessageFormatMode {
    LEGACY,
    MINIMESSAGE,
    BOTH;

    public static MessageFormatMode parse(String input) {
        if (input == null) {
            return BOTH;
        }
        try {
            return MessageFormatMode.valueOf(input.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return BOTH;
        }
    }
}

