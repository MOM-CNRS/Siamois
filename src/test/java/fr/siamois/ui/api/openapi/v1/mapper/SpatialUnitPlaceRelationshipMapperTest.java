package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceResourceIdentifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpatialUnitPlaceRelationshipMapperTest {

  private final SpatialUnitPlaceRelationshipMapper mapper = new SpatialUnitPlaceRelationshipMapper() {};

  @Test
  void spatialUnitToPlace_nullSummary_returnsNull() {
    assertThat(mapper.spatialUnitToPlace(null)).isNull();
  }

  @Test
  void spatialUnitToPlace_withId_buildsPlaceRelationship() {
    SpatialUnitSummaryDTO summary = new SpatialUnitSummaryDTO();
    summary.setId(15L);

    PlaceResourceIdentifier relationship = mapper.spatialUnitToPlace(summary);

    assertThat(relationship).isNotNull();
    assertThat(relationship.getResourceType()).isEqualTo("places");
    assertThat(relationship.getId()).isEqualTo("15");
  }

  @Test
  void spatialUnitToPlace_withoutId_leavesResourceIdUnset() {
    SpatialUnitSummaryDTO summary = new SpatialUnitSummaryDTO();

    PlaceResourceIdentifier relationship = mapper.spatialUnitToPlace(summary);

    assertThat(relationship).isNotNull();
    assertThat(relationship.getResourceType()).isEqualTo("places");
    assertThat(relationship.getId()).isNull();
  }
}
