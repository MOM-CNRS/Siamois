package fr.siamois.ui.bean.dialog.project;

import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.project.ProjectListBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The identifier the new project dialog proposes, and when it does so. */
class NewProjectDialogBeanTest {

    private static final String LANG = "fr";

    private final ProjectApiService projectApiService = mock(ProjectApiService.class);
    private final ProjectApiCaller caller = mock(ProjectApiCaller.class);

    private NewProjectDialogBean bean;

    @BeforeEach
    void setUp() {
        LangBean langBean = mock(LangBean.class);
        when(langBean.getLanguageCode()).thenReturn(LANG);
        bean = new NewProjectDialogBean(mock(InstitutionService.class), mock(ProfilePermissionService.class),
                mock(FieldConfigurationService.class), projectApiService, mock(ProjectListBean.class),
                mock(SessionSettingsBean.class), langBean);
        when(projectApiService.requireCaller()).thenReturn(caller);
    }

    @Test
    void proposeIdentifier_shouldFillTheFieldWithTheNextIdentifierOfTheOrganisation() {
        bean.setInstitutionId(3L);
        when(projectApiService.previewNextProjectIdentifier(caller, "3", LANG)).thenReturn(Optional.of("PRJ-004"));

        bean.proposeIdentifier();

        assertThat(bean.getIdentifier()).isEqualTo("PRJ-004");
    }

    @Test
    void proposeIdentifier_shouldLeaveTheFieldEmpty_whenTheOrganisationCannotNumberItsProjects() {
        bean.setInstitutionId(3L);
        bean.setIdentifier("old");
        when(projectApiService.previewNextProjectIdentifier(caller, "3", LANG)).thenReturn(Optional.empty());

        bean.proposeIdentifier();

        assertThat(bean.getIdentifier()).isNull();
    }

    @Test
    void proposeIdentifier_shouldLeaveTheFieldEmpty_whenNoOrganisationIsChosen() {
        bean.setIdentifier("old");

        bean.proposeIdentifier();

        assertThat(bean.getIdentifier()).isNull();
        verify(projectApiService, never()).previewNextProjectIdentifier(any(), any(), any());
    }

    @Test
    void proposeIdentifier_shouldNotBlockTheDialog_whenThePreviewIsRefused() {
        bean.setInstitutionId(3L);
        when(projectApiService.previewNextProjectIdentifier(caller, "3", LANG))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        bean.proposeIdentifier();

        assertThat(bean.getIdentifier()).isNull();
    }

    @Test
    void onOrganisationChange_shouldProposeTheIdentifierOfTheNewOrganisation() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(5L);
        bean.setCreatableInstitutions(List.of(institution));
        bean.setInstitutionId(5L);
        when(projectApiService.previewNextProjectIdentifier(eq(caller), eq("5"), eq(LANG))).thenReturn(Optional.of("B-001"));

        bean.onOrganisationChange();

        assertThat(bean.getIdentifier()).isEqualTo("B-001");
        assertThat(bean.getTypeId()).isNull();
    }
}
