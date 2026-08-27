package fr.siamois.domain.services.identifier;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.TreeMap;

/** Derives the counter key exclusively from partition-aware tokens active in a format. */
@Service
public class IdentifierPartitionService {
    private final IdentifierResolverRegistry resolverRegistry;

    public IdentifierPartitionService(IdentifierResolverRegistry resolverRegistry) {
        this.resolverRegistry = resolverRegistry;
    }

    public String canonicalKey(ConfigurableTable table, String format, IdentifierRenderContext context) {
        resolverRegistry.validate(table, format);
        Map<String, Object> activeDimensions = new TreeMap<>();
        for (IdentifierResolver<IdentifierRenderContext> resolver : resolverRegistry.resolvers(table)) {
            String dimension = resolver.partitionDimensionCode();
            if (dimension != null && resolver.isUsedBy(format)) {
                activeDimensions.put(dimension, resolver.resolvePartitionValue(context));
            }
        }
        return CanonicalPartitionKey.from(activeDimensions);
    }
}
