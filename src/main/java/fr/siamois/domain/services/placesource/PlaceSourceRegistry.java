package fr.siamois.domain.services.placesource;

import org.springframework.stereotype.Component;

import java.util.*;

/** Every {@link PlaceSourceProvider} of the application, by id. */
@Component
public class PlaceSourceRegistry {

    private final Map<String, PlaceSourceProvider> providers = new LinkedHashMap<>();

    public PlaceSourceRegistry(List<PlaceSourceProvider> found) {
        found.forEach(provider -> providers.put(provider.id(), provider));
    }

    public Optional<PlaceSourceProvider> find(String id) {
        return Optional.ofNullable(id == null ? null : providers.get(id));
    }

    public Set<String> ids() {
        return providers.keySet();
    }
}
