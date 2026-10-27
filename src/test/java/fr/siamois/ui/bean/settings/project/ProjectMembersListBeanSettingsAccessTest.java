package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.services.ProjectMembersServiceInterface;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProjectMemberDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.project.NewProjectMemberDialogBean;
import fr.siamois.ui.email.InvitationMailer;
import fr.siamois.utils.MessageUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectMembersListBeanSettingsAccessTest {

    @Mock private ProjectMembersServiceInterface projectMembersService;
    @Mock private NewProjectMemberDialogBean newProjectMemberDialogBean;
    @Mock private LangBean langBean;
    @Mock private ProfilePermissionService profilePermissionService;
    @Mock private SessionSettingsBean sessionSettingsBean;
    @Mock private PendingPersonService pendingPersonService;
    @Mock private InvitationMailer invitationMailer;

    @Test
    void removeMember_isRefusedWithoutProjectSettingsPermission() {
        ProjectMembersListBean bean = new ProjectMembersListBean(projectMembersService, newProjectMemberDialogBean,
                langBean, profilePermissionService, sessionSettingsBean, pendingPersonService, invitationMailer);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(7L);
        bean.setProject(project);
        when(sessionSettingsBean.getAuthenticatedUser()).thenReturn(null);
        when(profilePermissionService.hasProjectSettingsPermission(nullable(PersonDTO.class), any(ActionUnitDTO.class))).thenReturn(false);
        ProjectMemberDTO member = Mockito.mock(ProjectMemberDTO.class);

        try (MockedStatic<MessageUtils> messages = Mockito.mockStatic(MessageUtils.class)) {
            bean.removeMember(member);

            messages.verify(() -> MessageUtils.displayWarnMessage(any(LangBean.class),
                    Mockito.eq(ProjectMembersListBean.PROJECT_SETTINGS_ERROR_NOT_MANAGER)));
        }
        verify(projectMembersService, never()).removeMemberFromProject(any(), any());
    }
}
