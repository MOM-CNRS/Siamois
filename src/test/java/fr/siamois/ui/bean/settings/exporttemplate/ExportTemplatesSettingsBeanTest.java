package fr.siamois.ui.bean.settings.exporttemplate;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.exporttemplate.InvalidExportTemplateException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.exporttemplate.ExportTemplateService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.ExportTemplateDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportTemplatesSettingsBeanTest {

    @Mock private ExportTemplateService service;
    @Mock private ProfilePermissionService permissions;
    @Mock private SessionSettingsBean sessionSettingsBean;
    @Mock private LangBean langBean;
    @Mock private RedirectBean redirectBean;

    private ExportTemplatesSettingsBean bean;
    private UserInfo userInfo;
    private PersonDTO person;
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        bean = new ExportTemplatesSettingsBean(service, permissions, sessionSettingsBean, langBean, redirectBean);
        person = new PersonDTO();
        institution = new InstitutionDTO();
        userInfo = new UserInfo(institution, person, "fr");
        lenient().when(sessionSettingsBean.getUserInfo()).thenReturn(userInfo);
        lenient().when(sessionSettingsBean.getSelectedInstitution()).thenReturn(institution);
        lenient().when(langBean.msg(anyString())).thenAnswer(i -> i.getArgument(0));
    }

    private static ExportTemplateDTO dto(long id, String name) {
        return new ExportTemplateDTO(id, "u" + id, name, "1.0.0", null, Instant.parse("2026-10-02T10:00:00Z"), Instant.parse("2026-10-02T10:00:00Z"));
    }

    @Test
    void checkAccessOrRedirect_withoutViewPermission_redirectsTo404() {
        when(permissions.canViewInstitutionData(person, institution)).thenReturn(false);

        bean.checkAccessOrRedirect();

        verify(redirectBean).redirectTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void checkAccessOrRedirect_withViewPermission_doesNothing() {
        when(permissions.canViewInstitutionData(person, institution)).thenReturn(true);

        bean.checkAccessOrRedirect();

        verifyNoInteractions(redirectBean);
    }

    @Test
    void canManage_isTrueForInstanceAdmins() {
        when(permissions.hasInstancePermission(person, PermissionConstants.INSTANCE_MANAGE_SETTINGS)).thenReturn(true);

        assertThat(bean.canManage()).isTrue();
    }

    @Test
    void canManage_fallsBackOnTheOrganisationPermission() {
        when(permissions.hasInstancePermission(person, PermissionConstants.INSTANCE_MANAGE_SETTINGS)).thenReturn(false);
        when(permissions.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS)).thenReturn(true);

        assertThat(bean.canManage()).isTrue();
    }

    @Test
    void init_loadsTheTemplatesAndFiltersWithoutAccentsOrCase() {
        when(service.findAll(userInfo)).thenReturn(List.of(dto(1, "Référentiel national"), dto(2, "Export CSV")));

        bean.init();
        assertThat(bean.getFilteredTemplates()).hasSize(2);

        bean.setFilterText("REFERENTIEL");
        bean.onFilterType();

        assertThat(bean.getFilteredTemplates()).extracting(ExportTemplateDTO::name).containsExactly("Référentiel national");
    }

    @Test
    void create_withBlankName_doesNotCallTheService() {
        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class)) {
            bean.setNewTemplateName("  ");

            bean.create();

            verify(service, never()).createBlank(any(), anyString(), anyString(), anyString());
            messages.verify(() -> MessageUtils.displayErrorMessage(langBean, "exportTemplates.error.nameRequired"));
        }
    }

    @Test
    void importJson_invalidJson_showsTheErrorAndKeepsTheDialogOpen() {
        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class)) {
            bean.setImportedJson("{}");
            when(service.importJson(userInfo, "{}")).thenThrow(new InvalidExportTemplateException("Missing 'id'"));

            bean.importJson();

            messages.verify(() -> MessageUtils.displayErrorMessage(langBean, "exportTemplates.error.invalidJson", "Missing 'id'"));
            verify(service, never()).findAll(any());
        }
    }

    @Test
    void importJson_forbidden_showsTheForbiddenMessage() {
        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class)) {
            bean.setImportedJson("{}");
            when(service.importJson(userInfo, "{}")).thenThrow(new ForbiddenOperationException("no"));

            bean.importJson();

            messages.verify(() -> MessageUtils.displayErrorMessage(langBean, "common.error.forbidden"));
        }
    }

    @Test
    void getTemplateToDeleteName_isEmptyWithoutSelection() {
        assertThat(bean.getTemplateToDeleteName()).isEmpty();
    }

    @Test
    void formatDate_formatsDayMonthYear() {
        assertThat(bean.formatDate(Instant.parse("2026-10-02T12:00:00Z"))).isEqualTo("02/10/2026");
        assertThat(bean.formatDate(null)).isEmpty();
    }

    @Test
    void downloadJson_namesTheFileAfterTheTemplate() throws Exception {
        when(service.exportJson(userInfo, 1L)).thenReturn("{\"a\":1}");

        var content = bean.downloadJson(dto(1, "Référentiel national (2025)"));

        assertThat(content.getName()).isEqualTo("referentiel-national-2025.json");
        assertThat(new String(content.getStream().get().readAllBytes())).isEqualTo("{\"a\":1}");
    }
}
