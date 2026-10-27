package fr.siamois.ui.redirection;

import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.InstitutionListSettingsBean;
import fr.siamois.ui.bean.settings.administration.ApplicationMembersListBean;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplatesSettingsBean;
import fr.siamois.ui.bean.settings.project.ProjectDetailsBean;
import fr.siamois.ui.bean.settings.project.ProjectListBean;
import fr.siamois.ui.bean.settings.project.ProjectTableFieldSettingsBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingsControllerProjectTablesTest {

    @Mock private NavBean navBean;
    @Mock private InstitutionListSettingsBean institutionListSettingsBean;
    @Mock private ApplicationMembersListBean applicationMembersListBean;
    @Mock private SessionSettingsBean sessionSettingsBean;
    @Mock private ProfilePermissionService profilePermissionService;
    @Mock private ProjectListBean projectListBean;
    @Mock private PersonService personService;
    @Mock private PersonMapper personMapper;
    @Mock private ExportTemplatesSettingsBean exportTemplatesSettingsBean;
    @Mock private ProjectDetailsBean projectDetailsBean;
    @Mock private ProjectTableFieldSettingsBean projectTableFieldSettingsBean;
    @Mock private ActionUnitService actionUnitService;

    @InjectMocks
    private SettingsController controller;


    @Test
    void goToProjectTablesSettings_isForbiddenWithoutProjectSettingsPermission() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(7L);
        when(sessionSettingsBean.getAuthenticatedUser()).thenReturn(null);
        when(actionUnitService.findById(7L)).thenReturn(project);
        when(profilePermissionService.hasProjectSettingsPermission(nullable(PersonDTO.class), any(ActionUnitDTO.class))).thenReturn(false);

        // ForbiddenException cannot be built without a JAX-RS runtime, hence the generic type here
        assertThrows(RuntimeException.class, () -> controller.goToProjectTablesSettings(7L, null, null, null));

        verify(projectDetailsBean, never()).setProject(any());
        verify(projectTableFieldSettingsBean, never()).init(any());
    }

    @Test
    void goToProjectTablesSettings_answersLikeAForbiddenOneForAnUnknownProject() {
        when(actionUnitService.findById(7L)).thenThrow(new IllegalStateException("not found"));

        assertThrows(RuntimeException.class, () -> controller.goToProjectTablesSettings(7L, null, null, null));

        verify(projectDetailsBean, never()).setProject(any());
    }

    @Test
    void goToProjectTablesSettings_opensThePageForAProjectSettingsManager() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(7L);
        when(sessionSettingsBean.getAuthenticatedUser()).thenReturn(null);
        when(profilePermissionService.hasProjectSettingsPermission(nullable(PersonDTO.class), any(ActionUnitDTO.class))).thenReturn(true);
        when(actionUnitService.findById(7L)).thenReturn(project);

        String view = controller.goToProjectTablesSettings(7L, null, null, null);

        assertEquals("forward:/pages/settings/project/projectTablesSettings.xhtml", view);
        verify(projectDetailsBean).setProject(project);
        verify(projectTableFieldSettingsBean).init(project);
    }

    @Test
    void goToProjectTablesSettings_opensOnTheTypeAndFieldOfAFiche() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(7L);
        when(sessionSettingsBean.getAuthenticatedUser()).thenReturn(null);
        when(profilePermissionService.hasProjectSettingsPermission(nullable(PersonDTO.class), any(ActionUnitDTO.class))).thenReturn(true);
        when(actionUnitService.findById(7L)).thenReturn(project);

        controller.goToProjectTablesSettings(7L, "UE", 12L, 34L);

        verify(projectTableFieldSettingsBean).openOn(fr.siamois.domain.models.settings.tableconfig.ConfigurableTable.UE, 12L, 34L);
    }

    @Test
    void goToProjectSettings_isForbiddenForAnotherOrganisationManager() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(7L);
        when(actionUnitService.findById(7L)).thenReturn(project);
        when(sessionSettingsBean.getAuthenticatedUser()).thenReturn(null);
        when(profilePermissionService.hasProjectSettingsPermission(nullable(PersonDTO.class), any(ActionUnitDTO.class))).thenReturn(false);

        assertThrows(RuntimeException.class, () -> controller.goToProjectSettings(7L));

        verify(projectDetailsBean, never()).setProject(any());
    }

    @Test
    void goToProjectSettings_opensTheSettingsOfAManagedProject() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(7L);
        when(actionUnitService.findById(7L)).thenReturn(project);
        when(sessionSettingsBean.getAuthenticatedUser()).thenReturn(null);
        when(profilePermissionService.hasProjectSettingsPermission(nullable(PersonDTO.class), any(ActionUnitDTO.class))).thenReturn(true);

        String view = controller.goToProjectSettings(7L);

        assertEquals("forward:/pages/settings/project/projectSettings.xhtml", view);
        verify(projectDetailsBean).setProject(project);
    }
}
