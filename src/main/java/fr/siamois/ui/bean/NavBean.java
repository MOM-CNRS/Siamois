package fr.siamois.ui.bean;

import fr.siamois.domain.events.publisher.InstitutionChangeEventPublisher;
import fr.siamois.domain.models.events.InstitutionChangeEvent;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.*;
import fr.siamois.ui.bean.converter.InstitutionConverter;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.settings.InstitutionListSettingsBean;
import fr.siamois.ui.bean.settings.administration.ApplicationMembersListBean;
import fr.siamois.ui.bean.settings.project.ProjectDetailsBean;
import fr.siamois.ui.bean.settings.project.ProjectListBean;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;

/**
 * Bean to manage the navigation bar of the application. Allows the user to select a team.
 *
 * @author Julien Linget
 */
@Slf4j
@Component
@Getter
@Setter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class NavBean implements Serializable {

    private final SessionSettingsBean sessionSettingsBean;
    private final transient InstitutionChangeEventPublisher institutionChangeEventPublisher;
    private final transient InstitutionConverter converter;
    private final transient InstitutionService institutionService;
    private final RedirectBean redirectBean;
    private final InstitutionListSettingsBean institutionListSettingsBean;
    private final ProjectListBean projectListBean;
    private final transient BookmarkService bookmarkService;
    private final FlowBean flowBean;
    private final LangBean langBean;
    private final transient ProjectDetailsBean projectDetailsBean;
    private final ApplicationMembersListBean applicationMembersListBean;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient ActionUnitService actionUnitService;

    private String urlToGoBack; // URL to go back from settings

    private ApplicationMode applicationMode = ApplicationMode.SIAMOIS;

    public NavBean(SessionSettingsBean sessionSettingsBean,
                   InstitutionChangeEventPublisher institutionChangeEventPublisher,
                   InstitutionConverter converter,
                   InstitutionService institutionService,
                   RedirectBean redirectBean,
                   InstitutionListSettingsBean institutionListSettingsBean, ProjectListBean projectListBean, BookmarkService bookmarkService, FlowBean flowBean, LangBean langBean, ProjectDetailsBean projectDetailsBean, ApplicationMembersListBean applicationMembersListBean, ProfilePermissionService profilePermissionService,
                   ActionUnitService actionUnitService) {
        this.sessionSettingsBean = sessionSettingsBean;
        this.institutionChangeEventPublisher = institutionChangeEventPublisher;
        this.converter = converter;
        this.institutionService = institutionService;
        this.redirectBean = redirectBean;
        this.institutionListSettingsBean = institutionListSettingsBean;
        this.projectListBean = projectListBean;
        this.bookmarkService = bookmarkService;
        this.flowBean = flowBean;
        this.langBean = langBean;
        this.projectDetailsBean = projectDetailsBean;
        this.applicationMembersListBean = applicationMembersListBean;
        this.profilePermissionService = profilePermissionService;
        this.actionUnitService = actionUnitService;
    }

    public boolean isAdministrationVisible() {
        return profilePermissionService.hasInstancePermission(
                sessionSettingsBean.getAuthenticatedUser(), PermissionConstants.INSTANCE_MANAGE_SETTINGS);
    }

    public InstitutionDTO getSelectedInstitution() {
        return sessionSettingsBean.getSelectedInstitution();
    }

    public PersonDTO currentUser() {
        return sessionSettingsBean.getAuthenticatedUser();
    }

    public boolean isSiamoisMode() {
        return applicationMode == ApplicationMode.SIAMOIS;
    }

    public boolean isSettingsMode() {
        return applicationMode == ApplicationMode.SETTINGS;
    }

    public void onPreRenderView() {
        String viewId = FacesContext.getCurrentInstance().getViewRoot().getViewId();
        if (viewId != null && viewId.contains("/settings")) {
            applicationMode = ApplicationMode.SETTINGS;
        }
    }

    public void goToOrganisationSettings() {
        institutionListSettingsBean.init();
        redirectBean.redirectTo("/settings/organisation");
    }

    /**
     * Same as {@link #goToOrganisationSettings()}, pre-filtered to the organisations the given member
     * belongs to. The filter travels as a {@code memberId} query param — {@code SettingsController}'s
     * {@code /settings/organisation} mapping applies it after the redirect, since it unconditionally
     * (re)initialises {@code institutionListSettingsBean} on every request to that URL.
     */
    public void goToOrganisationSettings(PersonDTO person) {
        redirectBean.redirectTo("/settings/organisation?memberId=" + person.getId());
    }

    public void goToUserManagementSettings() {
        applicationMembersListBean.init();
        redirectBean.redirectTo("/settings/administration");
    }

    public String bookmarkTitle(BookmarkDTO bookmark) {
        try {
            return langBean.msg(bookmark.getTitle());
        } catch (NoSuchMessageException e) {
            return bookmark.getTitle();
        }
    }

    public void logout() {
        SecurityContextHolder.getContext().setAuthentication(null);
        FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
        redirectBean.redirectTo("/");
    }

    @EventListener(InstitutionChangeEvent.class)
    public void resetBackUrlOnInstitutionChange() {
        urlToGoBack = null;
    }

    public void backFromSettings() throws IOException {
        setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        if (urlToGoBack != null && !urlToGoBack.isEmpty()) {
            FacesContext.getCurrentInstance()
                    .getExternalContext()
                    .redirect(urlToGoBack);
        } else {
            redirectBean.redirectTo("/focus/L3dlbGNvbWU=");
        }
    }

    public void goToProjectsSettings() {
        setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        projectListBean.init();
        redirectBean.redirectTo("/settings/project");
    }

    /**
     * Same as {@link #goToProjectsSettings()}, pre-filtered to the projects the given member belongs to.
     * The filter travels as a {@code memberId} query param — see {@link #goToOrganisationSettings(PersonDTO)}.
     */
    public void goToProjectsSettings(PersonDTO person) {
        setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        redirectBean.redirectTo("/settings/project?memberId=" + person.getId());
    }

    public enum ApplicationMode {
        SIAMOIS,
        SETTINGS
    }

    public void goToActionUnitList() throws IOException {
        flowBean.redirectToFocus("/action-unit");
    }

    public void redirectToActionUnit() throws IOException {
        String id = FacesContext.getCurrentInstance()
                .getExternalContext()
                .getRequestParameterMap()
                .get("id");
        flowBean.redirectToFocus("/action-unit/" + id);
    }

    public void redirectToActionUnitSettings(ActionUnitDTO actionUnit) throws IOException {
        setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        projectListBean.init();
        String path = projectListBean.redirectToProject(actionUnit);
        if (path == null) {
            redirectBean.redirectTo(HttpStatus.FORBIDDEN);
            return;
        }
        FacesContext facesContext = FacesContext.getCurrentInstance();
        String contextPath = facesContext.getExternalContext().getRequestContextPath();
        facesContext.getExternalContext().redirect(contextPath + path);
    }

    /**
     * React main panel's settings button (reactPanelActions.xhtml, reactAction_openProjectSettings):
     * the project id travels as a remoteCommand param instead of being bound to the panel bean JSF
     * mounted, so the button keeps working after React navigated to another project client-side.
     * Same permission check as the JSF button (inside ProjectListBean#redirectToProject).
     */
    public void redirectToActionUnitSettingsFromRequest() throws IOException {
        String idParam = FacesContext.getCurrentInstance().getExternalContext()
                .getRequestParameterMap().get("projectId");
        if (idParam == null || idParam.isBlank()) {
            return;
        }
        redirectToActionUnitSettings(actionUnitService.findById(Long.parseLong(idParam)));
    }

}
