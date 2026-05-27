package fr.siamois.ui.bean.settings.project;

import com.sun.faces.application.ProjectStageJndiFactory;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.InstitutionActionManagerListBean;
import fr.siamois.ui.bean.settings.components.OptionElement;
import fr.siamois.ui.bean.settings.institution.InstitutionInfoSettingsBean;
import fr.siamois.ui.bean.settings.institution.InstitutionManagerListBean;
import fr.siamois.ui.bean.settings.institution.InstitutionThesaurusSettingsBean;
import fr.siamois.ui.bean.settings.team.TeamListBean;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.model.StreamedContent;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
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
@RequiredArgsConstructor
public class ProjectDetailsBean {

    private final ProjectUploadSettingsBean projectUploadSettingsBean;
    private final LangBean langBean;
    private final PermissionService permissionService;
    private final SessionSettingsBean sessionSettingsBean;

    // LOCALS
    private ActionUnitDTO project;
    private List<OptionElement> elements;
    private StreamedContent exampleFile;


    public void init() {
        elements = new ArrayList<>();

            elements.add(new OptionElement("bi bi-upload", langBean.msg("projectSettings.titles.upload"),
                    "Importer ou exporter de la donnée au format .xlsx", () -> {
                projectUploadSettingsBean.init(project);
                return "/pages/settings/project/projectUploadSettings.xhtml?faces-redirect=true";
            }));

            elements.add(new OptionElement("bi bi-people", langBean.msg("projectSettings.titles.members"),
                    "(A venir) Les membres du projet et leurs rôles", () -> {
                return null;
            }));

            elements.add(new OptionElement("bi bi-ui-radios",
                    langBean.msg("projectSettings.titles.tables"),
                    "(A venir) Gérer les tables, types, formulaires et identifiants du projet", () -> {
                return null;
            }));



    }

    public String goToProjectList() {
        project = null;
        if(elements != null && !elements.isEmpty()) {
            elements.clear();
        }
        return "/pages/settings/project/projectList.xhtml?faces-redirect=true";
    }

    public String backToProjectSettings() {
        return "/pages/settings/project/projectSettings.xhtml?faces-redirect=true";
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        project = null;
        elements = null;
        projectUploadSettingsBean.reset();
    }

    public StreamedContent getFile() {
        return exampleFile;
    }

}
