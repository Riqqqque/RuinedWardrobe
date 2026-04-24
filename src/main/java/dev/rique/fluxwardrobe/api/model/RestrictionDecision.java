package dev.rique.fluxwardrobe.api.model;

import java.util.Collections;
import java.util.Map;

public record RestrictionDecision(boolean allowed, ResultCode code, String messageKey, Map<String, String> placeholders) {

    public RestrictionDecision {
        placeholders = placeholders == null ? Collections.emptyMap() : Collections.unmodifiableMap(placeholders);
    }

    public static RestrictionDecision allow() {
        return new RestrictionDecision(true, ResultCode.SUCCESS, "restriction.allow", Collections.emptyMap());
    }
}

