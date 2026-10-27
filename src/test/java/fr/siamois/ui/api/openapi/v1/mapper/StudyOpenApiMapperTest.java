package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.StudyDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.study.StudyResource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StudyOpenApiMapperTest {

    private final ProjectResponseMapper responses = mock(ProjectResponseMapper.class);
    private final StudyOpenApiMapper mapper = new StudyOpenApiMapper(responses);

    private static ActionUnitSummaryDTO project(Long institutionId) {
        ActionUnitSummaryDTO project = new ActionUnitSummaryDTO();
        project.setId(2L);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(institutionId);
        project.setCreatedByInstitution(institution);
        return project;
    }

    @Test
    void toResource_fullDto() {
        StudyDTO dto = new StudyDTO();
        dto.setId(8L);
        dto.setIdentifier("ETU0008");
        dto.setValidationStatus(ValidationStatus.VALIDATED);
        dto.setActionUnit(project(5L));
        dto.setType(new ConceptDTO());

        StudyResource resource = mapper.toResource(dto, "fr", Map.of());

        assertThat(resource.getResourceType()).isEqualTo("studies");
        assertThat(resource.getId()).isEqualTo("8");
        assertThat(resource.getIdentifier()).isEqualTo("ETU0008");
        assertThat(resource.getValidationStatus()).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(resource.getProjectId()).isEqualTo("2");
        assertThat(resource.getOrganization().getId()).isEqualTo("5");
        assertThat(resource.getResourceUri()).isEqualTo("/study/8");
        verify(responses).toConceptFieldValue(dto.getType(), "fr", Map.of());
    }

    @Test
    void toResource_minimalDtoLeavesTheOptionalPartsOut() {
        StudyDTO dto = new StudyDTO();
        dto.setActionUnit(project(null));

        StudyResource resource = mapper.toResource(dto, "fr", Map.of());

        assertThat(resource.getId()).isNull();
        assertThat(resource.getProjectId()).isEqualTo("2");
        assertThat(resource.getOrganization()).isNull();
        assertThat(resource.getResourceUri()).isNull();
        verify(responses, never()).toConceptFieldValue(any(), any(), any());
    }

    @Test
    void toResource_withoutAProjectHasNeitherProjectNorOrganization() {
        StudyResource resource = mapper.toResource(new StudyDTO(), "fr", Map.of());

        assertThat(resource.getProjectId()).isNull();
        assertThat(resource.getOrganization()).isNull();
    }
}
