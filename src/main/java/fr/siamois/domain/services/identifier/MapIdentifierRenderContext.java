package fr.siamois.domain.services.identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Map-backed context; null values deliberately represent missing source data. */
public final class MapIdentifierRenderContext implements IdentifierRenderContext {
    private final Map<String, Object> values;
    private final Map<String, Object> partitionValues;

    public MapIdentifierRenderContext(Map<String, ?> values) {
        this(values, Map.of());
    }

    public MapIdentifierRenderContext(Map<String, ?> values, Map<String, ?> partitionValues) {
        Map<String, Object> copy = new HashMap<>();
        values.forEach(copy::put);
        this.values = Collections.unmodifiableMap(copy);
        Map<String, Object> partitionCopy = new HashMap<>();
        partitionValues.forEach(partitionCopy::put);
        this.partitionValues = Collections.unmodifiableMap(partitionCopy);
    }

    @Override
    public Object value(String tokenCode) {
        return values.get(tokenCode);
    }

    @Override
    public Object partitionValue(String dimensionCode) {
        return partitionValues.get(dimensionCode);
    }
}
