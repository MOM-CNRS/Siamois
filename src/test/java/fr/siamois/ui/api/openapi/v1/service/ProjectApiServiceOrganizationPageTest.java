package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectApiServiceOrganizationPageTest {

    @Mock
    private InstitutionService institutionService;
    @Mock
    private ActionUnitService actionUnitService;
    @Mock
    private RecordingUnitService recordingUnitService;
    @Mock
    private DocumentService documentService;
    @Mock
    private SpecimenService specimenService;
    @Mock
    private ProjectDocumentOpenApiMapper projectDocumentOpenApiMapper;
    @Mock
    private FindOpenApiMapper findOpenApiMapper;
    @Mock
    private PersonMapper personMapper;

    private ProjectApiService projectApiService;

    private final PersonDTO personDto = new PersonDTO();

    @BeforeEach
    void setUp() {
        personDto.setId(1L);
        projectApiService = new ProjectApiService(
                institutionService,
                actionUnitService,
                recordingUnitService,
                documentService,
                specimenService,
                projectDocumentOpenApiMapper,
                findOpenApiMapper,
                personMapper);
    }

    private ProjectApiCaller caller() {
        return new ProjectApiCaller(personDto, Set.of());
    }

    @Test
    void pageAccessibleOrganizations_empty_returnsEmptyPage() {
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of());

        Page<InstitutionDTO> page = projectApiService.pageAccessibleOrganizations(caller(), 0, 10, "name:asc");

        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getContent()).isEmpty();
        verify(institutionService).findInstitutionsOfPerson(eq(personDto));
    }

    @Test
    void pageAccessibleOrganizations_sortByIdDesc() {
        InstitutionDTO low = dto(1L, "B", "id-1");
        InstitutionDTO high = dto(10L, "A", "id-2");
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(low, high));

        Page<InstitutionDTO> page = projectApiService.pageAccessibleOrganizations(caller(), 0, 10, "id:desc");

        assertThat(page.getContent()).extracting(InstitutionDTO::getId).containsExactly(10L, 1L);
    }

    @Test
    void pageAccessibleOrganizations_sortByIdentifierAsc_caseInsensitive() {
        InstitutionDTO z = dto(1L, "N", "zeta");
        InstitutionDTO a = dto(2L, "N", "Alpha");
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(z, a));

        Page<InstitutionDTO> page = projectApiService.pageAccessibleOrganizations(caller(), 0, 10, "identifier:asc");

        assertThat(page.getContent()).extracting(InstitutionDTO::getIdentifier).containsExactly("Alpha", "zeta");
    }

    @Test
    void pageAccessibleOrganizations_sortByCreationDateAsc() {
        OffsetDateTime t1 = OffsetDateTime.parse("2020-01-01T00:00:00Z");
        OffsetDateTime t2 = OffsetDateTime.parse("2021-06-15T12:00:00Z");
        InstitutionDTO older = dto(1L, "Old", "o");
        older.setCreationDate(t1);
        InstitutionDTO newer = dto(2L, "New", "n");
        newer.setCreationDate(t2);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(newer, older));

        Page<InstitutionDTO> page = projectApiService.pageAccessibleOrganizations(caller(), 0, 10, "creationDate:asc");

        assertThat(page.getContent()).extracting(InstitutionDTO::getId).containsExactly(1L, 2L);
    }

    @Test
    void pageAccessibleOrganizations_unknownSortProperty_defaultsToName_withDirectionPreserved() {
        InstitutionDTO b = dto(1L, "Bbb", "x");
        InstitutionDTO a = dto(2L, "Aaa", "y");
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(b, a));

        Page<InstitutionDTO> page = projectApiService.pageAccessibleOrganizations(caller(), 0, 10, "unknownField:desc");

        assertThat(page.getContent()).extracting(InstitutionDTO::getName).containsExactly("Bbb", "Aaa");
    }

    @Test
    void pageAccessibleOrganizations_offsetBeyondTotal_returnsEmptySlice() {
        InstitutionDTO only = dto(1L, "Solo", "s");
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(only));

        Page<InstitutionDTO> page = projectApiService.pageAccessibleOrganizations(caller(), 10, 10, "name:asc");

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).isEmpty();
    }

    private static InstitutionDTO dto(long id, String name, String identifier) {
        InstitutionDTO d = new InstitutionDTO();
        d.setId(id);
        d.setName(name);
        d.setIdentifier(identifier);
        return d;
    }
}
