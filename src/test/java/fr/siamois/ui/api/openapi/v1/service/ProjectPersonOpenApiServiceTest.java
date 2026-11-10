package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.PersonName;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.person.PendingAccountPersonService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.request.person.PersonCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectPersonOpenApiServiceTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private ProfilePermissionService profilePermissionService;
    @Mock
    private PendingAccountPersonService pendingAccountPersonService;

    @InjectMocks
    private ProjectPersonOpenApiService service;

    private ProjectApiCaller caller;
    private ActionUnitDTO project;

    @BeforeEach
    void setUp() {
        caller = new ProjectApiCaller(new PersonDTO(), Set.of(10L), List.of());
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        institution.setName("Inst");
        project = new ActionUnitDTO();
        project.setId(3L);
        project.setCreatedByInstitution(institution);
        when(projectApiService.requireAccessibleProject(caller, "3")).thenReturn(new AccessibleProjectForApi(project, 0, 0));
    }

    @Test
    void createPersonAwaitingAccount_withProjectWriteRight_createsPerson() {
        when(profilePermissionService.hasActionUnitWritePermission(any(UserInfo.class), any(ActionUnitDTO.class))).thenReturn(true);
        PersonDTO created = new PersonDTO();
        when(pendingAccountPersonService.createInProject(new PersonName("Marie", "Curie"), project)).thenReturn(created);

        PersonDTO result = service.createPersonAwaitingAccount(caller, "3", new PersonCreateRequest(" Marie ", "Curie"), "fr");

        assertThat(result).isSameAs(created);
    }

    @Test
    void createPersonAwaitingAccount_withStudiesRightOnly_createsPerson() {
        when(profilePermissionService.hasActionUnitWritePermission(any(UserInfo.class), any(ActionUnitDTO.class))).thenReturn(false);
        when(projectApiService.canEditStudiesForProject(caller, "3", "fr")).thenReturn(true);
        PersonDTO created = new PersonDTO();
        when(pendingAccountPersonService.createInProject(any(PersonName.class), any(ActionUnitDTO.class))).thenReturn(created);

        assertThat(service.createPersonAwaitingAccount(caller, "3", new PersonCreateRequest("Marie", "Curie"), "fr"))
                .isSameAs(created);
    }

    @Test
    void createPersonAwaitingAccount_withoutAnyEditRight_isForbidden() {
        when(profilePermissionService.hasActionUnitWritePermission(any(UserInfo.class), any(ActionUnitDTO.class))).thenReturn(false);
        PersonCreateRequest request = new PersonCreateRequest("Marie", "Curie");

        assertThatThrownBy(() -> service.createPersonAwaitingAccount(caller, "3", request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(pendingAccountPersonService);
    }

    @Test
    void createPersonAwaitingAccount_withBlankName_isBadRequest() {
        when(profilePermissionService.hasActionUnitWritePermission(any(UserInfo.class), any(ActionUnitDTO.class))).thenReturn(true);
        PersonCreateRequest request = new PersonCreateRequest("Marie", " ");

        assertThatThrownBy(() -> service.createPersonAwaitingAccount(caller, "3", request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
