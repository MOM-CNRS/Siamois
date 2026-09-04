package fr.siamois.ui.bean.settings;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.components.OptionElement;
import fr.siamois.ui.bean.settings.institution.InstitutionInfoSettingsBean;
import fr.siamois.ui.bean.settings.institution.InstitutionMembersListBean;
import fr.siamois.ui.bean.settings.institution.InstitutionThesaurusSettingsBean;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Getter
@Setter
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class InstitutionDetailsBean implements Serializable {

    private final InstitutionInfoSettingsBean institutionInfoSettingsBean;
    private final InstitutionThesaurusSettingsBean institutionThesaurusSettingsBean;
    private final LangBean langBean;
    private final SessionSettingsBean sessionSettingsBean;
    private InstitutionDTO institution;
    private transient List<OptionElement> elements;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient InstitutionMembersListBean institutionMembersListBean;
    private final RedirectBean redirectBean;

    public InstitutionDetailsBean(InstitutionInfoSettingsBean institutionInfoSettingsBean,
                                  InstitutionThesaurusSettingsBean institutionThesaurusSettingsBean,
                                  LangBean langBean,
                                  SessionSettingsBean sessionSettingsBean,
                                  ProfilePermissionService profilePermissionService,
                                  InstitutionMembersListBean institutionMembersListBean,
                                  RedirectBean redirectBean) {

        this.institutionInfoSettingsBean = institutionInfoSettingsBean;
        this.institutionThesaurusSettingsBean = institutionThesaurusSettingsBean;
        this.langBean = langBean;
        this.sessionSettingsBean = sessionSettingsBean;
        this.profilePermissionService = profilePermissionService;
        this.institutionMembersListBean = institutionMembersListBean;
        this.redirectBean = redirectBean;
    }

    private boolean canManageInstitution() {
        PersonDTO person = sessionSettingsBean.getUserInfo().getUser();
        return profilePermissionService.hasInstancePermission(person, PermissionConstants.INSTANCE_MANAGE_SETTINGS)
                || profilePermissionService.hasOrganizationPermission(person, institution, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS);
    }

    /**
     * Guards the institution settings pages: redirects to the institution list if no institution
     * was selected (e.g. direct URL access), or to a 404 if the current user cannot manage it.
     */
    public void checkInstitutionOrRedirect() {
        if (institution == null) {
            redirectBean.redirectTo("/settings/organisation");
            return;
        }
        if (!canManageInstitution()) {
            redirectBean.redirectTo(HttpStatus.NOT_FOUND);
        }
    }

    public void init() {
        elements = new ArrayList<>();

        if (canManageInstitution()) {
            elements.add(new OptionElement("bi bi-building", langBean.msg("organisationSettings.titles.settings"),
                    langBean.msg("organisationSettings.descriptions.settings"), () -> {
                institutionInfoSettingsBean.init(institution);
                return "/pages/settings/institution/institutionInfoSettings.xhtml?faces-redirect=true";
            }));

            elements.add(new OptionElement("bi bi-table", langBean.msg("common.label.thesaurus"),
                    langBean.msg("organisationSettings.descriptions.thesaurus"), () -> {
                institutionThesaurusSettingsBean.init(institution);
                return "/pages/settings/institution/thesaurusSettings.xhtml?faces-redirect=true";
            }));

            elements.add(new OptionElement("bi bi-people", langBean.msg("organisationSettings.titles.members"),
                    langBean.msg("organisationSettings.descriptions.members", institution.getName()), () -> {
                institutionMembersListBean.init(institution);
                return "/pages/settings/institution/institutionMembersSettings.xhtml?faces-redirect=true";
            }));
        }
    }

    public String goToInstitutionList() {
        institution = null;
        if(elements != null && !elements.isEmpty()) {
            elements.clear();
        }
        return "/pages/settings/institutionListSettings.xhtml?faces-redirect=true";
    }

    public String backToInstitutionSettings() {
        if (elements == null || elements.isEmpty()) {
            init();
        }
        return "/pages/settings/institutionSettings.xhtml?faces-redirect=true";
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        institution = null;
        elements = null;
        institutionInfoSettingsBean.reset();
        institutionThesaurusSettingsBean.reset();
    }

}
