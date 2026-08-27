package fr.siamois.domain.services.identifier;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalPartitionKeyTest {

    @Test
    void emptyDimensions_shouldUseVersionOnly() {
        assertThat(CanonicalPartitionKey.from(Map.of())).isEqualTo("v1");
    }

    @Test
    void dimensions_shouldBeSortedAndIndependentOfInsertionOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("SPATIAL_PLACE", 8);
        first.put("PARENT_RU", 42L);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("PARENT_RU", 42L);
        second.put("SPATIAL_PLACE", 8);

        assertThat(CanonicalPartitionKey.from(first)).isEqualTo(CanonicalPartitionKey.from(second));
    }

    @Test
    void missingValue_shouldUseReservedBucketDistinctFromRealZero() {
        Map<String, Object> missing = new HashMap<>();
        missing.put("PARENT_RU", null);

        assertThat(CanonicalPartitionKey.from(missing)).contains("=~");
        assertThat(CanonicalPartitionKey.from(missing))
                .isNotEqualTo(CanonicalPartitionKey.from(Map.of("PARENT_RU", 0)));
    }

    @Test
    void encoding_shouldPreventDelimiterCollisions() {
        assertThat(CanonicalPartitionKey.from(Map.of("A|B", "C=D")))
                .isNotEqualTo(CanonicalPartitionKey.from(Map.of("A", "B|C=D")));
    }
}
