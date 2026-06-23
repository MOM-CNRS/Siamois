package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceOpenApiMapperTest {

    @Mock
    private ConceptResourceIdentifierMapper conceptResourceIdentifierMapper;

    @InjectMocks
    private PlaceOpenApiMapper mapper;

    @Test
    void toResource_null_returnsNull() {
        assertThat(mapper.toResource(null)).isNull();
    }

    @Test
    void toResource_minimalFields() {
        SpatialUnitDTO dto = new SpatialUnitDTO();
        dto.setName("Cave A");

        PlaceResource resource = mapper.toResource(dto);

        assertThat(resource.getResourceType()).isEqualTo("places");
        assertThat(resource.getName()).isEqualTo("Cave A");
        assertThat(resource.getId()).isNull();
        assertThat(resource.getType()).isNull();
        assertThat(resource.getOrganization()).isNull();
    }

    @Test
    void toResource_withId_setsStringId() {
        SpatialUnitDTO dto = new SpatialUnitDTO();
        dto.setId(7L);
        dto.setName("Cave A");

        PlaceResource resource = mapper.toResource(dto);

        assertThat(resource.getId()).isEqualTo("7");
    }

    @Test
    void toResource_withCategory_mapsTypeRelationship() {
        ConceptDTO category = new ConceptDTO();
        category.setId(3L);
        ConceptResourceIdentifier typeRef = new ConceptResourceIdentifier();
        typeRef.setId("3");
        when(conceptResourceIdentifierMapper.convert(category)).thenReturn(typeRef);

        SpatialUnitDTO dto = new SpatialUnitDTO();
        dto.setId(1L);
        dto.setName("Lieu");
        dto.setCategory(category);

        PlaceResource resource = mapper.toResource(dto);

        assertThat(resource.getType()).isNotNull();
        assertThat(resource.getType()).isSameAs(typeRef);
        verify(conceptResourceIdentifierMapper).convert(category);
    }

    @Test
    void toResource_withOrganization_mapsOrganizationRelationship() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);

        SpatialUnitDTO dto = new SpatialUnitDTO();
        dto.setId(1L);
        dto.setCreatedByInstitution(institution);

        PlaceResource resource = mapper.toResource(dto);

        assertThat(resource.getOrganization()).isNotNull();
        assertThat(resource.getOrganization().getResourceType()).isEqualTo("organizations");
        assertThat(resource.getOrganization().getId()).isEqualTo("10");
    }

    @Test
    void toResource_institutionWithoutId_skipsOrganization() {
        SpatialUnitDTO dto = new SpatialUnitDTO();
        dto.setCreatedByInstitution(new InstitutionDTO());

        PlaceResource resource = mapper.toResource(dto);

        assertThat(resource.getOrganization()).isNull();
    }

    @Test
    void toResource_nullInstitution_skipsOrganization() {
        SpatialUnitDTO dto = new SpatialUnitDTO();
        dto.setCreatedByInstitution(null);

        PlaceResource resource = mapper.toResource(dto);

        assertThat(resource.getOrganization()).isNull();
    }

    @Test
    void toResource_nullCategory_skipsType() {
        SpatialUnitDTO dto = new SpatialUnitDTO();
        dto.setId(2L);
        dto.setCategory(null);

        PlaceResource resource = mapper.toResource(dto);

        assertThat(resource.getType()).isNull();
    }
}
