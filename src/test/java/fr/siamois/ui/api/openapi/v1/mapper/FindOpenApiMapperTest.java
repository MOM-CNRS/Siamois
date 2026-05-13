package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResourceIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FindOpenApiMapperTest {

    @Mock
    private ConceptResourceIdentifierMapper conceptResourceIdentifierMapper;
    @Mock
    private PersonResourceIdentifierMapper personResourceIdentifierMapper;

    @InjectMocks
    private FindOpenApiMapper mapper;

    @Test
    void toResource_mapsCoreFieldsAndRelationships() {
        ConceptDTO type = new ConceptDTO();
        type.setId(3L);
        ConceptResourceIdentifier typeRef = new ConceptResourceIdentifier();
        typeRef.setId("3");
        typeRef.setResourceType("concepts");
        when(conceptResourceIdentifierMapper.convert(type)).thenReturn(typeRef);

        PersonDTO author = new PersonDTO();
        author.setId(7L);
        PersonResourceIdentifier authorRef = new PersonResourceIdentifier("persons", "7");
        when(personResourceIdentifierMapper.convert(author)).thenReturn(authorRef);

        RecordingUnitSummaryDTO ru = new RecordingUnitSummaryDTO();
        ru.setId(42L);

        InstitutionDTO org = new InstitutionDTO();
        org.setId(100L);

        SpecimenDTO specimen = new SpecimenDTO();
        specimen.setId(99L);
        specimen.setFullIdentifier("INST-UE-99");
        specimen.setType(type);
        specimen.setRecordingUnit(ru);
        specimen.setCreatedByInstitution(org);
        specimen.setAuthors(List.of(author));
        specimen.setCollectors(List.of());

        FindResource r = mapper.toResource(specimen);

        assertThat(r.getResourceType()).isEqualTo("finds");
        assertThat(r.getId()).isEqualTo("99");
        assertThat(r.getFullIdentifier()).isEqualTo("INST-UE-99");
        assertThat(r.getType()).isNotNull();
        assertThat(r.getType().getData()).isSameAs(typeRef);
        assertThat(r.getRecordingUnit().getData().getId()).isEqualTo("42");
        assertThat(r.getRecordingUnit().getData().getResourceType()).isEqualTo("recording-units");
        assertThat(r.getOrganization().getData().getId()).isEqualTo("100");
        assertThat(r.getAuthors().getData()).hasSize(1);
        assertThat(r.getAuthors().getData().get(0)).isSameAs(authorRef);
        assertThat(r.getGeom()).isNull();
    }

    @Test
    void toResource_minimalSpecimen_setsOnlyIdsAndType() {
        SpecimenDTO specimen = new SpecimenDTO();
        specimen.setId(1L);
        specimen.setFullIdentifier("X");

        FindResource r = mapper.toResource(specimen);

        assertThat(r.getResourceType()).isEqualTo("finds");
        assertThat(r.getId()).isEqualTo("1");
        assertThat(r.getFullIdentifier()).isEqualTo("X");
        assertThat(r.getType()).isNull();
        assertThat(r.getRecordingUnit()).isNull();
        assertThat(r.getOrganization()).isNull();
    }
}
