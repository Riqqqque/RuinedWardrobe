package dev.rique.fluxwardrobe.api.model;

import java.util.Collections;
import java.util.Map;

public record WardrobeResult(ResultCode code, String messageKey, Map<String, String> placeholders) {

    public WardrobeResult {
        placeholders = placeholders == null ? Collections.emptyMap() : Collections.unmodifiableMap(placeholders);
    }

    public static WardrobeResult success(ResultCode code, String messageKey) {
        return new WardrobeResult(code, messageKey, Collections.emptyMap());
    }

    public static WardrobeResult denied(ResultCode code, String messageKey, Map<String, String> placeholders) {
        return new WardrobeResult(code, messageKey, placeholders);
    }

    public static WardrobeResult error(String messageKey) {
        return new WardrobeResult(ResultCode.ERROR_UNKNOWN, messageKey, Collections.emptyMap());
    }

    public boolean isSuccess() {
        return code == ResultCode.SUCCESS
                || code == ResultCode.SUCCESS_SAVED
                || code == ResultCode.SUCCESS_EQUIPPED
                || code == ResultCode.SUCCESS_DELETED
                || code == ResultCode.SUCCESS_RENAMED;
    }
}
