package fr.siamois.ui.bean.settings;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.components.OptionElement;
import fr.siamois.ui.bean.settings.institution.InstitutionInfoSettingsBean;
import fr.siamois.ui.bean.settings.institution.InstitutionManagerListBean;
import fr.siamois.ui.bean.settings.institution.InstitutionThesaurusSettingsBean;
import fr.siamois.ui.bean.settings.team.TeamListBean;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
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
    private final InstitutionManagerListBean institutionManagerListBean;
    private final InstitutionThesaurusSettingsBean institutionThesaurusSettingsBean;
    private final LangBean langBean;
    private final InstitutionActionManagerListBean institutionActionManagerListBean;
    private final transient PermissionService permissionService;
    private final SessionSettingsBean sessionSettingsBean;
    private final TeamListBean teamListBean;
    private Institution institution;
    private transient List<OptionElement> elements;

    public InstitutionDetailsBean(InstitutionInfoSettingsBean institutionInfoSettingsBean,
                                  InstitutionManagerListBean institutionManagerListBean,
                                  InstitutionThesaurusSettingsBean institutionThesaurusSettingsBean,
                                  LangBean langBean,
                                  InstitutionActionManagerListBean institutionActionManagerListBean,
                                  PermissionService permissionService,
                                  SessionSettingsBean sessionSettingsBean, TeamListBean teamListBean) {

        this.institutionInfoSettingsBean = institutionInfoSettingsBean;
        this.institutionManagerListBean = institutionManagerListBean;
        this.institutionThesaurusSettingsBean = institutionThesaurusSettingsBean;
        this.langBean = langBean;
        this.institutionActionManagerListBean = institutionActionManagerListBean;
        this.permissionService = permissionService;
        this.sessionSettingsBean = sessionSettingsBean;
        this.teamListBean = teamListBean;
    }

    public void init() {
        elements = new ArrayList<>();

        if (permissionService.isInstitutionManager(sessionSettingsBean.getUserInfo())) {
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

            elements.add(new OptionElement("bi bi-person-circle", langBean.msg("organisationSettings.titles.managers"),
                    langBean.msg("organisationSettings.descriptions.managers", institution.getName()), () -> {
                institutionManagerListBean.init(institution);
                return "/pages/settings/institution/institutionManagerSettings.xhtml?faces-redirect=true";
            }));

            elements.add(new OptionElement("bi bi-person-circle",
                    langBean.msg("organisationSettings.titles.actionManagers"),
                    langBean.msg("organisationSettings.descriptions.actionManagers"), () -> {
                institutionActionManagerListBean.init(institution);
                return "/pages/settings/institution/institutionActionManagerSettings.xhtml?faces-redirect=true";
            }));
        }


        elements.add(new OptionElement("bi bi-people", langBean.msg("organisationSettings.titles.teams"),
                langBean.msg("organisationSettings.descriptions.teams", institution.getName()), () -> {
            teamListBean.init(institution);
            return "/pages/settings/team/teamList.xhtml?faces-redirect=true";
        }));


    }

    public String goToInstitutionList() {
        institution = null;
        elements.clear();
        return "/pages/settings/institutionListSettings.xhtml?faces-redirect=true";
    }

    public String backToInstitutionSettings() {
        return "/pages/settings/institutionSettings.xhtml?faces-redirect=true";
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        institution = null;
        elements = null;
        institutionInfoSettingsBean.reset();
        institutionManagerListBean.reset();
        institutionThesaurusSettingsBean.reset();
        institutionActionManagerListBean.reset();
    }

}
