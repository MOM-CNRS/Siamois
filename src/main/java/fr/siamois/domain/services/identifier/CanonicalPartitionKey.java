package fr.siamois.domain.services.identifier;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Stable, opaque serialization of the active counter dimensions. */
public final class CanonicalPartitionKey {
    public static final String VERSION = "v1";
    public static final String MISSING_VALUE = "~";

    private CanonicalPartitionKey() {
    }

    public static String from(Map<String, ?> dimensions) {
        if (dimensions.isEmpty()) return VERSION;
        return VERSION + "|" + new TreeMap<>(dimensions).entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encodeValue(entry.getValue()))
                .collect(Collectors.joining("|"));
    }

    private static String encodeValue(Object value) {
        return value == null ? MISSING_VALUE : encode(value.toString());
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
