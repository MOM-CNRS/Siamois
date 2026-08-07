package fr.siamois.domain.services.identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Map-backed context; null values deliberately represent missing source data. */
public final class MapIdentifierRenderContext implements IdentifierRenderContext {
    private final Map<String, Object> values;

    public MapIdentifierRenderContext(Map<String, ?> values) {
        Map<String, Object> copy = new HashMap<>();
        values.forEach(copy::put);
        this.values = Collections.unmodifiableMap(copy);
    }

    @Override
    public Object value(String tokenCode) {
        return values.get(tokenCode);
    }
}
