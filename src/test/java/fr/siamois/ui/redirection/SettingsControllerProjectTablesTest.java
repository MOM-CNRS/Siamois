package fr.siamois.ui.redirection;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.ActionUnitDTO;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private final UserInfo userInfo = new UserInfo(null, null, null);

    @Test
    void goToProjectTablesSettings_isForbiddenWithoutProjectSettingsPermission() {
        when(sessionSettingsBean.getUserInfo()).thenReturn(userInfo);
        when(profilePermissionService.hasProjectSettingsPermission(userInfo, 7L)).thenReturn(false);

        // ForbiddenException cannot be built without a JAX-RS runtime, hence the generic type here
        assertThrows(RuntimeException.class, () -> controller.goToProjectTablesSettings(7L, null));

        verify(actionUnitService, never()).findById(7L);
    }

    @Test
    void goToProjectTablesSettings_opensThePageForAProjectSettingsManager() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(7L);
        when(sessionSettingsBean.getUserInfo()).thenReturn(userInfo);
        when(profilePermissionService.hasProjectSettingsPermission(userInfo, 7L)).thenReturn(true);
        when(actionUnitService.findById(7L)).thenReturn(project);

        String view = controller.goToProjectTablesSettings(7L, null);

        assertEquals("forward:/pages/settings/project/projectTablesSettings.xhtml", view);
        verify(projectDetailsBean).setProject(project);
        verify(projectTableFieldSettingsBean).init(project);
    }
}
