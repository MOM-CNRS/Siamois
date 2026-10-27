package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectDetailsBeanSettingsAccessTest {

    @Mock private ProjectUploadSettingsBean projectUploadSettingsBean;
    @Mock private ProjectMembersListBean projectMembersListBean;
    @Mock private ProjectThesaurusSettingsBean projectThesaurusSettingsBean;
    @Mock private ProjectTableFieldSettingsBean projectTableFieldSettingsBean;
    @Mock private LangBean langBean;
    @Mock private SessionSettingsBean sessionSettingsBean;
    @Mock private RedirectBean redirectBean;
    @Mock private ProfilePermissionService profilePermissionService;

    @InjectMocks
    private ProjectDetailsBean bean;


    private ActionUnitDTO project() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(7L);
        return project;
    }

    @Test
    void checkProjectOrRedirect_redirectsToTheListWithoutProject() {
        bean.checkProjectOrRedirect();

        verify(redirectBean).redirectTo("/settings/project");
    }

    @Test
    void checkProjectOrRedirect_answersNotFoundWithoutProjectSettingsPermission() {
        bean.setProject(project());
        when(sessionSettingsBean.getAuthenticatedUser()).thenReturn(null);
        when(profilePermissionService.hasProjectSettingsPermission(nullable(PersonDTO.class), any(ActionUnitDTO.class))).thenReturn(false);

        bean.checkProjectOrRedirect();

        verify(redirectBean).redirectTo(HttpStatus.NOT_FOUND);
        verify(projectUploadSettingsBean, never()).init(project());
    }

    @Test
    void checkProjectOrRedirect_resyncsTheUploadBeanForAProjectSettingsManager() {
        ActionUnitDTO project = project();
        bean.setProject(project);
        when(sessionSettingsBean.getAuthenticatedUser()).thenReturn(null);
        when(profilePermissionService.hasProjectSettingsPermission(nullable(PersonDTO.class), any(ActionUnitDTO.class))).thenReturn(true);

        bean.checkProjectOrRedirect();

        verify(projectUploadSettingsBean).init(project);
        verify(redirectBean, never()).redirectTo(HttpStatus.NOT_FOUND);
    }
}
