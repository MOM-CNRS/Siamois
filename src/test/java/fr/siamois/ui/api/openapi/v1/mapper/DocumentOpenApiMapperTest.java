package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DocumentOpenApiMapperTest {

    private final ProjectResponseMapper responses = mock(ProjectResponseMapper.class);
    private final DocumentOpenApiMapper mapper = new DocumentOpenApiMapper(responses);

    private static ActionUnitSummaryDTO project(Long institutionId) {
        ActionUnitSummaryDTO au = new ActionUnitSummaryDTO();
        au.setId(2L);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(institutionId);
        au.setCreatedByInstitution(institution);
        return au;
    }

    @Test
    void fullDto_isMappedWithItsFileProjectOrganizationAndCategory() {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(7L);
        dto.setIdentifier("DOC0007");
        dto.setTitle("Plan général");
        dto.setDescription("Le plan");
        dto.setFileName("plan.pdf");
        dto.setMimeType("application/pdf");
        dto.setUrl("/siamois/content/abc.pdf");
        dto.setFileCode("abc");
        dto.setSize(12L);
        dto.setMd5Sum("md5");
        dto.setActionUnit(project(3L));
        dto.setCategory(new ConceptDTO());
        Map<Long, String> labels = Map.of();

        DocumentResource r = mapper.toResource(dto, "fr", labels);

        assertThat(r.getResourceType()).isEqualTo("documents");
        assertThat(r.getId()).isEqualTo("7");
        assertThat(r.getIdentifier()).isEqualTo("DOC0007");
        assertThat(r.getLabel()).isEqualTo("Plan général");
        assertThat(r.getFileName()).isEqualTo("plan.pdf");
        assertThat(r.getFileCode()).isEqualTo("abc");
        assertThat(r.getSize()).isEqualTo(12L);
        assertThat(r.getProjectId()).isEqualTo("2");
        assertThat(r.getOrganization().getId()).isEqualTo("3");
        assertThat(r.getResourceUri()).isEqualTo("/document/7");
        verify(responses).toConceptFieldValue(dto.getCategory(), "fr", labels);
    }

    @Test
    void blankTitleFallsBackToTheIdentifier_andMissingPartsAreLeftOut() {
        DocumentDTO dto = new DocumentDTO();
        dto.setIdentifier("DOC-2");
        dto.setTitle(" ");

        DocumentResource r = mapper.toResource(dto, "fr", Map.of());

        assertThat(r.getLabel()).isEqualTo("DOC-2");
        assertThat(r.getId()).isNull();
        assertThat(r.getProjectId()).isNull();
        assertThat(r.getOrganization()).isNull();
        assertThat(r.getType()).isNull();
        assertThat(r.getResourceUri()).isNull();
        verifyNoInteractions(responses);
    }

    @Test
    void aProjectWithoutInstitutionGivesNoOrganization() {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(1L);
        ActionUnitSummaryDTO au = new ActionUnitSummaryDTO();
        au.setId(5L);
        dto.setActionUnit(au);

        DocumentResource r = mapper.toResource(dto, "fr", Map.of());

        assertThat(r.getProjectId()).isEqualTo("5");
        assertThat(r.getOrganization()).isNull();
    }
}
