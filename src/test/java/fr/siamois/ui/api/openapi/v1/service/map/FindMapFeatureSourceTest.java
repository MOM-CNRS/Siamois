package fr.siamois.ui.api.openapi.v1.service.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldGeometry;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FindMapFeatureSourceTest {

    @Mock private SpecimenRepository repository;
    @Mock private FindOpenApiMapper mapper;
    @Mock private CustomFieldRepository customFieldRepository;
    @InjectMocks private FindMapFeatureSource source;

    /**
     * A find reads as a feature with the identifier, type and status of its list row.
     */
    @Test
    void toFeature_TakesTheListRowsIdentifierTypeAndStatus() throws Exception {
        SpecimenDTO dto = new SpecimenDTO();
        dto.setId(7L);
        ResolvedConceptResource type = new ResolvedConceptResource();
        type.setResolvedLabel("Céramique");
        FindResource resource = new FindResource();
        resource.setId("7");
        resource.setFullIdentifier("MOB-007");
        resource.setType(type);
        resource.setValidationStatus(ValidationStatus.VALIDATED);
        when(mapper.toResource(dto)).thenReturn(resource);
        JsonNode geometry = new ObjectMapper().readTree("{\"type\":\"Point\",\"coordinates\":[2.35,48.85]}");

        EntityMapResponse.Feature feature = source.toFeature(dto, geometry);

        assertEquals(new EntityMapResponse.Feature("7", "MOB-007", type, ValidationStatus.VALIDATED, geometry), feature);
        assertEquals(7L, source.idOf(dto));
    }

    /**
     * A find with no type nor status still reads as a feature.
     */
    @Test
    void toFeature_WithoutTypeNorStatus_KeepsThemEmpty() throws Exception {
        SpecimenDTO dto = new SpecimenDTO();
        FindResource resource = new FindResource();
        resource.setId("8");
        resource.setFullIdentifier("MOB-008");
        when(mapper.toResource(dto)).thenReturn(resource);
        JsonNode geometry = new ObjectMapper().readTree("{\"type\":\"Point\",\"coordinates\":[0,0]}");

        EntityMapResponse.Feature feature = source.toFeature(dto, geometry);

        assertEquals(new EntityMapResponse.Feature("8", "MOB-008", null, null, geometry), feature);
    }

    /**
     * A mapper that answers nothing is a bug, not a find without a feature.
     */
    @Test
    void toFeature_WhenTheMapperAnswersNothing_Throws() {
        SpecimenDTO dto = new SpecimenDTO();
        when(mapper.toResource(dto)).thenReturn(null);

        assertThrows(NullPointerException.class, () -> source.toFeature(dto, null));
    }

    /**
     * The geometry columns are the find's own geometry (the default, from the system field catalog) and
     * the additional fields of type geometry — not any other field.
     */
    @Test
    void geometryColumns_AreTheOwnGeometryAndTheAdditionalGeometryFields() {
        when(customFieldRepository.findById(40L)).thenReturn(Optional.of(new CustomFieldGeometry()));
        when(customFieldRepository.findById(41L)).thenReturn(Optional.of(new CustomFieldText()));
        when(customFieldRepository.findById(42L)).thenReturn(Optional.empty());

        assertEquals(-438L, source.defaultGeometryFieldId());
        assertTrue(source.isGeometryColumn(-438L));
        assertTrue(source.isGeometryColumn(40L));
        assertFalse(source.isGeometryColumn(41L));
        assertFalse(source.isGeometryColumn(42L));
    }

    /**
     * The find's own geometry is known without asking the database.
     */
    @Test
    void isGeometryColumn_OwnGeometry_DoesNotLookItUp() {
        assertTrue(source.isGeometryColumn(-438L));

        verify(customFieldRepository, never()).findById(-438L);
    }

    /**
     * The recording units' own geometry is not a column of the finds.
     */
    @Test
    void isGeometryColumn_TheRecordingUnitsGeometry_IsNotOne() {
        when(customFieldRepository.findById(-332L)).thenReturn(Optional.empty());

        assertFalse(source.isGeometryColumn(-332L));
    }

    /**
     * By its own geometry, PostGIS's; by an additional field, its answers'.
     */
    @Test
    void geoJsonWgs84ByIds_ReadsTheColumnsGeometries() {
        List<Object[]> own = new ArrayList<>();
        own.add(new Object[]{7L, "{}"});
        List<Object[]> answers = new ArrayList<>();
        answers.add(new Object[]{7L, "{\"type\":\"Point\"}"});
        when(repository.findGeoJsonWgs84ByIds(List.of(7L))).thenReturn(own);
        when(repository.findAnswerGeoJsonWgs84ByIds(List.of(7L), 40L)).thenReturn(answers);

        assertSame(own, source.geoJsonWgs84ByIds(List.of(7L), -438L));
        assertSame(answers, source.geoJsonWgs84ByIds(List.of(7L), 40L));
    }

    /**
     * Each column filters the rows on its own emptiness.
     */
    @Test
    void withGeometry_GivesASpecificationPerColumn() {
        assertNotNull(source.withGeometry(-438L));
        assertNotNull(source.withGeometry(40L));
    }
}
