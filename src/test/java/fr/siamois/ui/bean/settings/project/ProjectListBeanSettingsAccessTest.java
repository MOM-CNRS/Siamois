package fr.siamois.ui.bean.settings.project;

import static org.mockito.ArgumentMatchers.nullable;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.MessageUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectListBeanSettingsAccessTest {

    @Mock private ActionUnitService actionUnitService;
    @Mock private NavBean navBean;
    @Mock private SessionSettingsBean sessionSettingsBean;
    @Mock private ProjectDetailsBean projectDetailsBean;
    @Mock private ProjectMembersListBean projectMembersListBean;
    @Mock private InstitutionService institutionService;
    @Mock private ProfilePermissionService profilePermissionService;
    @Mock private PersonService personService;
    @Mock private LangBean langBean;

    @InjectMocks
    private ProjectListBean bean;

    private static ActionUnitDTO project(long id) {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(id);
        project.setName("P" + id);
        return project;
    }

    @Test
    void initFilteredByPerson_flagsTheProjectsTheUserMayManage() {
        ActionUnitDTO managed = project(7L);
        ActionUnitDTO other = project(8L);
        when(actionUnitService.findAllByTeamMember(any())).thenReturn(Set.of(managed, other));
        when(sessionSettingsBean.getUserInfo()).thenReturn(new UserInfo(null, null, null));
        when(profilePermissionService.actionUnitIdsWithProjectSettingsPermission(any(), any())).thenReturn(Set.of(7L));

        bean.initFilteredByPerson(new PersonDTO());

        assertTrue(bean.canManageSettings(managed));
        assertFalse(bean.canManageSettings(other));
    }

    @Test
    void redirectToProject_opensTheSettingsOfAManagedProject() {
        ActionUnitDTO project = project(7L);
        when(sessionSettingsBean.getAuthenticatedUser()).thenReturn(null);
        when(profilePermissionService.hasProjectSettingsPermission(nullable(PersonDTO.class), any(ActionUnitDTO.class))).thenReturn(true);

        String path = bean.redirectToProject(project);

        assertEquals("/pages/settings/project/projectSettings.xhtml?faces-redirect=true", path);
        verify(projectDetailsBean).setProject(project);
        verify(projectDetailsBean).init();
    }

    @Test
    void redirectToProject_isRefusedWithoutProjectSettingsPermission() {
        when(sessionSettingsBean.getAuthenticatedUser()).thenReturn(null);
        when(profilePermissionService.hasProjectSettingsPermission(nullable(PersonDTO.class), any(ActionUnitDTO.class))).thenReturn(false);

        try (MockedStatic<MessageUtils> messages = Mockito.mockStatic(MessageUtils.class)) {
            assertNull(bean.redirectToProject(project(7L)));

            messages.verify(() -> MessageUtils.displayWarnMessage(any(LangBean.class), Mockito.eq("common.error.forbidden")));
        }
    }
}
