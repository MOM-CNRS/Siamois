package fr.siamois.ui.bean.panel;

import fr.siamois.domain.events.publisher.InstitutionChangeEventPublisher;
import fr.siamois.domain.events.publisher.LoginEventPublisher;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.events.InstitutionChangeEvent;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.*;
import fr.siamois.ui.bean.HistoryBean;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Session-wide state of the main page: read/write and field/office modes, the organization switch,
 * the redirections to a focus URL, and the server-side copy of the overview React opened.
 *
 * @author Grégory Bliault
 */
@Slf4j
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
@Getter
@Setter
public class FlowBean implements Serializable {

    private final SessionSettingsBean sessionSettings;
    private final LangBean langBean;
    private final transient PanelFactory panelFactory;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient InstitutionService institutionService;
    private final transient InstitutionChangeEventPublisher institutionChangeEventPublisher;
    private final transient HistoryBean historyBean;

    private final transient LoginEventPublisher loginEventPublisher;

    // locals
    private Boolean isWriteMode = true;
    private Boolean isFieldMode = false;
    private transient List<InstitutionDTO> institutions;
    private transient InstitutionDTO selectedInstitution;


    public void init() {
        InstitutionDTO institution = sessionSettings.getSelectedInstitution();
        UserInfo info = sessionSettings.getUserInfo();
        institutions = new ArrayList<>();
        institutions.addAll(institutionService.findInstitutionsOfPerson(info.getUser()));
        selectedInstitution = institution;
    }

    @EventListener(InstitutionChangeEvent.class)
    public void handleInstitutionChange() {
        init();
        MessageUtils.displayInfoMessage(langBean, "institution.change.success", sessionSettings.getUserInfo().getInstitution());
    }

    @EventListener(LoginEvent.class)
    public void handleLoginSuccess() {
        init();
    }

    /**
     * Keeps the page's panel in sync with the overview React opened client-side, so a reload (F5)
     * and the navigation history reopen it. Called through the {@code reactAction_setOverview_N}
     * remoteCommand (panel/reactPanelActions.xhtml) with the entity's React registry key
     * ({@code entityType}) and {@code id} as request parameters. An unknown or missing key is a
     * no-op rather than a guess.
     */
    public void addEntityToOverviewFromRequest(AbstractPanel targetPanel) {
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext()
                .getRequestParameterMap();
        String idParam = params.get("id");
        String entityType = params.get("entityType");

        if (targetPanel == null || idParam == null || entityType == null) {
            return;
        }

        AbstractPanel overviewPanel = panelFactory.createForReactEntityType(entityType, Long.parseLong(idParam));
        if (overviewPanel == null) {
            log.warn("setOverview : entityType React inconnu côté serveur : {}", entityType);
            return;
        }
        addPanelToOverview(targetPanel.isRoot() ? targetPanel : targetPanel.getParentOrOverview(), overviewPanel);
    }

    private void addPanelToOverview(AbstractPanel targetPanel, AbstractPanel overviewPanel) {
        overviewPanel.setRoot(false);
        targetPanel.setRoot(true);
        targetPanel.setParentOrOverview(overviewPanel);
        overviewPanel.setParentOrOverview(targetPanel);

        HistoryBean.HistoryItem newEntry = new HistoryBean.HistoryItem();
        newEntry.setMain(historyComponentOf(targetPanel));
        newEntry.setSecondary(historyComponentOf(overviewPanel));
        historyBean.addItem(newEntry);
    }

    private static HistoryBean.HistoryItemComponent historyComponentOf(AbstractPanel panel) {
        HistoryBean.HistoryItemComponent component = new HistoryBean.HistoryItemComponent();
        component.setTitle(panel.resolveTitleOrTitleCode());
        component.setIcon(panel.getIcon());
        component.setUri(panel.ressourceUri());
        component.setStyleClass(panel.getPanelClass());
        return component;
    }

    public void redirectToFocus(String resourceUri) throws IOException {
        redirectToFocus(resourceUri, null, null);
    }

    public void redirectToFocus(String resourceUri, @Nullable String overviewResourceUri) throws IOException {
        redirectToFocus(resourceUri, overviewResourceUri, null);
    }

    public void redirectToFocus(String resourceUri, @Nullable String overviewResourceUri, @Nullable String backUrl) throws IOException {
        String encodedUri = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(resourceUri.getBytes(StandardCharsets.UTF_8));

        FacesContext context = FacesContext.getCurrentInstance();
        String basePath = context.getExternalContext().getRequestContextPath();

        StringBuilder params = new StringBuilder();
        if (overviewResourceUri != null) {
            params.append("?s=").append(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(overviewResourceUri.getBytes(StandardCharsets.UTF_8)));
        }
        if (backUrl != null) {
            params.append(!params.isEmpty() ? "&" : "?").append("back=")
                    .append(Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(backUrl.getBytes(StandardCharsets.UTF_8)));
        }

        context.getExternalContext().redirect(basePath + "/focus/" + encodedUri + params);
    }


    public void redirectToDashboard() throws IOException {
        FacesContext context = FacesContext.getCurrentInstance();
        String basePath = context.getExternalContext().getRequestContextPath();
        String url = basePath + "/focus/L3dlbGNvbWU=";
        context.getExternalContext().redirect(url);
    }

    /**
     * Listener called when the ReadWrite mode variable is flipped.
     */
    public void changeReadWriteMode() {
        PrimeFaces.current().ajax().update("flow");
    }

    /**
     * Listener called when the FieldOffice mode variable is flipped.
     */
    public void changeFieldOfficeMode() {
        // Listener called when the FieldOffice mode variable is flipped.
    }

    /**
     * Do change institution
     *
     */
    public void changeInstitution() {
        if (profilePermissionService.canAccessInstitution(sessionSettings.getUserInfo().getUser(), selectedInstitution)) {
            sessionSettings.setSelectedInstitution(selectedInstitution);
            PrimeFaces.current().ajax().update("navBar", "flow");
            institutionChangeEventPublisher.publishInstitutionChangeEvent();
            loginEventPublisher.publishLoginEvent();
        } else {
            selectedInstitution = sessionSettings.getSelectedInstitution();
        }

    }

    /**
     * On institution select change
     *
     */
    public void onInstitutionChange() {
        changeInstitution();
    }

    /**
     * On institution select change
     *
     */
    public void onFocusInstitutionChange() throws IOException {
        changeInstitution();
        historyBean.getItems().clear(); // clear history
        redirectToDashboard();

    }


    public String getFieldOfficeSwitchTooltip() {
        if (Boolean.TRUE.equals(isFieldMode)) {
            return langBean.msg("common.label.switchToOfficeMode");
        } else {
            return langBean.msg("common.label.switchToFieldMode");
        }
    }

    public String getReadWriteSwitchTooltip() {
        if (Boolean.TRUE.equals(isWriteMode)) {
            return langBean.msg("common.label.switchToReadMode");
        } else {
            return langBean.msg("common.label.switchToWriteMode");
        }
    }
}
