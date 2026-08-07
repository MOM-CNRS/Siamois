package fr.siamois.domain.services.identifier;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifierPartitionServiceTest {
    private final IdentifierResolverRegistry registry = new IdentifierResolverRegistry();
    private final IdentifierPartitionService service = new IdentifierPartitionService(registry);

    @Test
    void displayOnlyFormat_shouldUseUnpartitionedKey() {
        String key = service.canonicalKey(
                ConfigurableTable.UE,
                "UE-{NUM_UE:000}",
                new MapIdentifierRenderContext(Map.of()));

        assertThat(key).isEqualTo("v1");
    }

    @Test
    void onlyDimensionsWhoseTokensAreUsed_shouldEnterTheKey() {
        IdentifierRenderContext context = new MapIdentifierRenderContext(
                Map.of(), Map.of("PARENT_RU", 12L, "SPATIAL_PLACE", 4));

        String parentOnly = service.canonicalKey(
                ConfigurableTable.UE, "{NUM_PARENT}-{NUM_UE}", context);
        String parentAndPlace = service.canonicalKey(
                ConfigurableTable.UE, "{NUM_PARENT}-{NUM_USPATIAL}-{NUM_UE}", context);

        assertThat(parentOnly).isNotEqualTo(parentAndPlace);
    }

    @Test
    void numericalAndTextTokensForSameRelationship_shouldUseSameDimension() {
        IdentifierRenderContext context = new MapIdentifierRenderContext(Map.of(), Map.of("PARENT_RU", 12L));

        String numerical = service.canonicalKey(
                ConfigurableTable.UE, "{NUM_PARENT}-{NUM_UE}", context);
        String textual = service.canonicalKey(
                ConfigurableTable.UE, "{ID_PARENT}-{NUM_UE}", context);

        assertThat(numerical).isEqualTo(textual);
    }

    @Test
    void missingRelationship_shouldUseStableMissingBucket() {
        Map<String, Object> partitions = new HashMap<>();
        partitions.put("PARENT_RU", null);
        IdentifierRenderContext context = new MapIdentifierRenderContext(Map.of(), partitions);

        String key = service.canonicalKey(
                ConfigurableTable.UE, "{NUM_PARENT:000}-{NUM_UE}", context);

        assertThat(key).contains("=~");
    }

    @Test
    void sharedSpatialPlaceNumber_shouldProduceSameKey() {
        IdentifierRenderContext firstSpatialUnit = new MapIdentifierRenderContext(
                Map.of(), Map.of("SPATIAL_PLACE", 7));
        IdentifierRenderContext secondSpatialUnit = new MapIdentifierRenderContext(
                Map.of(), Map.of("SPATIAL_PLACE", 7));

        assertThat(service.canonicalKey(
                ConfigurableTable.UE, "{NUM_USPATIAL}-{NUM_UE}", firstSpatialUnit))
                .isEqualTo(service.canonicalKey(
                        ConfigurableTable.UE, "{NUM_USPATIAL}-{NUM_UE}", secondSpatialUnit));
    }

    @Test
    void mobilierUeNumberAndIdentifier_shouldUseSameDimension() {
        IdentifierRenderContext context = new MapIdentifierRenderContext(Map.of(), Map.of("PARENT_RU", 91L));

        assertThat(service.canonicalKey(
                ConfigurableTable.MOBILIER, "{NUM_MOBILIER}-{NUM_UE}", context))
                .isEqualTo(service.canonicalKey(
                        ConfigurableTable.MOBILIER, "{NUM_MOBILIER}-{ID_UE}", context));
    }
}
