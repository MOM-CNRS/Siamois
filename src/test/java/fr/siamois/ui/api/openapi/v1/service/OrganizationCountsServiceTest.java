package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.ContainerService;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationCountsResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationCountsServiceTest {

    @Mock private ProjectApiService projectApiService;
    @Mock private ActionUnitService actionUnitService;
    @Mock private SpatialUnitService spatialUnitService;
    @Mock private RecordingUnitService recordingUnitService;
    @Mock private SpecimenService specimenService;
    @Mock private PhaseService phaseService;
    @Mock private ContainerService containerService;

    private OrganizationCountsService service;
    private ProjectApiCaller caller;
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        service = new OrganizationCountsService(projectApiService, actionUnitService, spatialUnitService,
                recordingUnitService, specimenService, phaseService, containerService);
        institution = new InstitutionDTO();
        institution.setId(10L);
        PersonDTO person = new PersonDTO();
        person.setId(1L);
        caller = new ProjectApiCaller(person, Set.of(10L), List.of(institution));
    }

    @Test
    void countsForOrganization_delegatesEachCountToItsService() {
        when(projectApiService.requireOrganization(10L, caller)).thenReturn(institution);
        when(actionUnitService.countByInstitutionId(10L)).thenReturn(1L);
        when(spatialUnitService.countByInstitutionId(10L)).thenReturn(2L);
        when(recordingUnitService.countByInstitutionId(10L)).thenReturn(3L);
        when(specimenService.countByInstitution(institution)).thenReturn(4L);
        when(phaseService.countSearchResults(eq(institution), any())).thenReturn(5);
        when(containerService.countSearchResults(eq(institution), any())).thenReturn(6);

        OrganizationCountsResource counts = service.countsForOrganization(caller, 10L);

        assertThat(counts).isEqualTo(new OrganizationCountsResource(1, 2, 3, 4, 5, 6));
    }

    @Test
    void countsForOrganization_outsideScope_propagates403AndCountsNothing() {
        when(projectApiService.requireOrganization(99L, caller))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.countsForOrganization(caller, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(actionUnitService, spatialUnitService, recordingUnitService,
                specimenService, phaseService, containerService);
    }
}
