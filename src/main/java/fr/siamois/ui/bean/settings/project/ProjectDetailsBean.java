package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.components.OptionElement;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.model.StreamedContent;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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
    private final ProjectMembersListBean projectMembersListBean;
    private final LangBean langBean;
    private final PermissionService permissionService;
    private final SessionSettingsBean sessionSettingsBean;
    private final RedirectBean redirectBean;

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
                langBean.msg("projectSettings.descriptions.members", project.getName()), () -> {
            projectMembersListBean.init(project);
            return "/pages/settings/project/projectMembersSettings.xhtml?faces-redirect=true";
        }));

        elements.add(new OptionElement("bi bi-ui-radios",
                langBean.msg("projectSettings.titles.tables"),
                "(A venir) Gérer les tables, types, formulaires et identifiants du projet", () -> null));

        elements.add(new OptionElement("bi bi-table",
                "\uD83D\uDEA7 Thésaurus",
                "(A venir) Gestion du thésaurus du projet", () -> null));


    }

    public void checkProjectOrRedirect() {
        if (project == null) {
            redirectBean.redirectTo("/settings/project");
            return;
        }
        // projectUploadSettingsBean.project is only set via its own .init(project) call (wired to the
        // upload tile's action in init() above) — if this page was reached any other way (direct URL,
        // refresh, browser back/forward) while this bean's project survived, it can be desynced and
        // stay null, silently breaking institution resolution during import. Re-sync it here.
        if (projectUploadSettingsBean.getProject() == null) {
            projectUploadSettingsBean.init(project);
        }
    }

    public String goToProjectList() {
        project = null;
        if (elements != null && !elements.isEmpty()) {
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
        projectMembersListBean.reset();
    }

    public StreamedContent getFile() {
        return exampleFile;
    }

}
