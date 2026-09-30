package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PhaseAndContainerOpenApiMapperTest {

    private final ProjectResponseMapper responses = mock(ProjectResponseMapper.class);
    private final PhaseOpenApiMapper phaseMapper = new PhaseOpenApiMapper(responses);
    private final ContainerOpenApiMapper containerMapper = new ContainerOpenApiMapper(responses);

    private static ActionUnitSummaryDTO project(Long institutionId) {
        ActionUnitSummaryDTO au = new ActionUnitSummaryDTO();
        au.setId(2L);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(institutionId);
        au.setCreatedByInstitution(institution);
        return au;
    }

    @Test
    void phase_fullDto() {
        PhaseDTO dto = new PhaseDTO();
        dto.setId(7L);
        dto.setIdentifier("PH-1");
        dto.setTitle("Occupation");
        dto.setActionUnit(project(3L));
        dto.setType(new ConceptDTO());
        Map<Long, String> labels = Map.of();

        PhaseResource r = phaseMapper.toResource(dto, "fr", labels);

        assertThat(r.getResourceType()).isEqualTo("phases");
        assertThat(r.getId()).isEqualTo("7");
        assertThat(r.getLabel()).isEqualTo("Occupation");
        assertThat(r.getProjectId()).isEqualTo("2");
        assertThat(r.getOrganization().getId()).isEqualTo("3");
        assertThat(r.getResourceUri()).isEqualTo("/phase/7");
        verify(responses).toConceptFieldValue(dto.getType(), "fr", labels);
    }

    @Test
    void phase_blankTitleFallsBackToTheIdentifier_andMissingPartsAreLeftOut() {
        PhaseDTO dto = new PhaseDTO();
        dto.setIdentifier("PH-2");
        dto.setTitle(" ");

        PhaseResource r = phaseMapper.toResource(dto, "fr", Map.of());

        assertThat(r.getLabel()).isEqualTo("PH-2");
        assertThat(r.getId()).isNull();
        assertThat(r.getProjectId()).isNull();
        assertThat(r.getOrganization()).isNull();
        assertThat(r.getResourceUri()).isNull();
        verify(responses, never()).toConceptFieldValue(any(), any(), any());
    }

    @Test
    void container_fullDto() {
        ContainerDTO dto = new ContainerDTO();
        dto.setId(8L);
        dto.setIdentifier("C-8");
        dto.setActionUnit(project(5L));
        dto.setType(new ConceptDTO());

        ContainerResource r = containerMapper.toResource(dto, "en", Map.of());

        assertThat(r.getResourceType()).isEqualTo("containers");
        assertThat(r.getId()).isEqualTo("8");
        assertThat(r.getProjectId()).isEqualTo("2");
        assertThat(r.getOrganization().getId()).isEqualTo("5");
        assertThat(r.getResourceUri()).isEqualTo("/container/8");
        verify(responses).toConceptFieldValue(dto.getType(), "en", Map.of());
    }

    @Test
    void container_minimalDtoLeavesOptionalPartsOut() {
        ContainerDTO dto = new ContainerDTO();
        dto.setActionUnit(project(null));

        ContainerResource r = containerMapper.toResource(dto, "en", Map.of());

        assertThat(r.getId()).isNull();
        assertThat(r.getProjectId()).isEqualTo("2");
        assertThat(r.getOrganization()).isNull();
        assertThat(r.getResourceUri()).isNull();
    }
}
