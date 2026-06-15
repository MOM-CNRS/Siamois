package fr.siamois.ui.api.openapi.v1.generic.response;

import com.fasterxml.jackson.annotation.JsonAnyGetter;

import java.util.LinkedHashMap;
import java.util.Map;

public class ResourceCounts {

    private final Map<String, Long> counts = new LinkedHashMap<>();

    public ResourceCounts add(String key, Long value) {
        counts.put(key, value);
        return this;
    }

    @JsonAnyGetter
    public Map<String, Long> getCounts() {
        return counts;
    }
}
