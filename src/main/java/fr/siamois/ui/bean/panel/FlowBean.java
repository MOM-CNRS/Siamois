package fr.siamois.ui.bean.panel;

import fr.siamois.domain.events.publisher.InstitutionChangeEventPublisher;
import fr.siamois.domain.events.publisher.LoginEventPublisher;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.events.InstitutionChangeEvent;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.recordingunit.StratigraphicRelationshipService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.dto.entity.*;
import fr.siamois.ui.bean.HistoryBean;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.bean.panel.models.panel.list.AbstractListPanel;
import fr.siamois.ui.bean.panel.models.panel.single.*;
import fr.siamois.utils.MessageUtils;
import jakarta.el.MethodExpression;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.springframework.context.NoSuchMessageException;
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
 * <p>This ui.bean handles the home page</p>
 * <p>It is used to display the list of spatial units without parents</p>
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

    private final transient SpatialUnitService spatialUnitService;
    private final transient RecordingUnitService recordingUnitService;
    private final transient ActionUnitService actionUnitService;
    private final SessionSettingsBean sessionSettings;
    private final LangBean langBean;
    private final transient FieldConfigurationService fieldConfigurationService;
    private final transient FieldService fieldService;
    private final transient PanelFactory panelFactory;
    private final transient PersonService personService;
    private final transient ConceptService conceptService;
    private final transient StratigraphicRelationshipService stratigraphicRelationshipService;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient InstitutionService institutionService;
    private final transient InstitutionChangeEventPublisher institutionChangeEventPublisher;
    private final transient HistoryBean historyBean;

    private final RedirectBean redirectBean;
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

    public void addPanelToOverview(AbstractPanel targetPanel, AbstractPanel overviewPanel) {
        addPanelToOverview(targetPanel, overviewPanel, true);
    }

    /**
     * @param updateMainPanel whether to also force-refresh the main/root panel's container (or,
     *                        for a list panel, its whole table). Needed the first time an
     *                        overview is opened (to highlight the newly-selected row), but not
     *                        when merely navigating between entities within an already-open
     *                        overview (prev/next arrows) — nothing in the main panel changed.
     */
    public void addPanelToOverview(AbstractPanel targetPanel, AbstractPanel overviewPanel, boolean updateMainPanel) {

        HistoryBean.HistoryItem newEntry = new HistoryBean.HistoryItem();
        HistoryBean.HistoryItemComponent main = new HistoryBean.HistoryItemComponent();
        HistoryBean.HistoryItemComponent side = new HistoryBean.HistoryItemComponent();

        overviewPanel.setRoot(false);
        targetPanel.setRoot(true);
        targetPanel.setParentOrOverview(overviewPanel);
        overviewPanel.setParentOrOverview(targetPanel);

        if(targetPanel instanceof AbstractListPanel<?>) {
            main.setTitle(targetPanel.resolveTitleOrTitleCode());
        }
        else {
            main.setTitle(targetPanel.getTitleCodeOrTitle());
        }
        main.setIcon(targetPanel.getIcon());
        main.setUri(targetPanel.ressourceUri());
        main.setStyleClass(targetPanel.getPanelClass());
        newEntry.setMain(main);


        if(overviewPanel instanceof AbstractListPanel<?>) {
            side.setTitle(overviewPanel.resolveTitleOrTitleCode());
        }
        else {
            side.setTitle(overviewPanel.getTitleCodeOrTitle());
        }
        side.setIcon(overviewPanel.getIcon());
        side.setUri(overviewPanel.ressourceUri());
        side.setStyleClass(overviewPanel.getPanelClass());
        newEntry.setSecondary(side);

        historyBean.addItem(newEntry);

        if (targetPanel instanceof AbstractListPanel<?> listPanel
                && listPanel.getTableModel() != null
                && overviewPanel instanceof AbstractSingleEntityPanel<?> singlePanel) {
            listPanel.getTableModel().setOverviewEntityId(singlePanel.getUnitId());
        }

        String base64RootUri = Base64.getUrlEncoder().withoutPadding().encodeToString(targetPanel.ressourceUri().getBytes());
        String base64OverviewUri = Base64.getUrlEncoder().withoutPadding().encodeToString(overviewPanel.ressourceUri().getBytes());
        List<String> updateTargets = new ArrayList<>(List.of("sideview-" + targetPanel.getPanelIndex(), "historyForm"));
        if (updateMainPanel) {
            String tableTarget = (targetPanel instanceof AbstractListPanel<?> lp)
                    ? lp.getActiveTableClientId()
                    : "panel-" + targetPanel.getPrefixPanelIndex() + "-container";
            updateTargets.add(tableTarget);
        }
        PrimeFaces.current().ajax().update(updateTargets);
        PrimeFaces.current().executeScript(
                String.format(
                        "showSideview('%s', '%s', '%s');",
                        targetPanel.getPanelIndex(),
                        base64RootUri,
                        base64OverviewUri
                )
        );

    }

    public void addRecordingUnitToOverview(Long id, AbstractPanel targetPanel, @Nullable Integer tabIndex) {
        addRecordingUnitToOverview(id, targetPanel, tabIndex, true);
    }

    public void addRecordingUnitToOverview(Long id, AbstractPanel targetPanel, @Nullable Integer tabIndex, boolean updateMainPanel) {

        if (targetPanel != null) {
            // Add the overview
            RecordingUnitPanel overviewPanel = panelFactory.createRecordingUnitPanel(id);
            overviewPanel.setRoot(false);

            if(tabIndex!=null) {
                overviewPanel.setActiveTabIndex(tabIndex);
            }

            if(targetPanel.isRoot()) {
                addPanelToOverview(targetPanel, overviewPanel, updateMainPanel);
            }
            else {
                addPanelToOverview(targetPanel.getParentOrOverview(), overviewPanel, updateMainPanel);
            }

        }

    }

    public void addRecordingUnitToOverviewFromStratiModule(AbstractPanel targetPanel) {
        String idParam = FacesContext.getCurrentInstance()
                .getExternalContext()
                .getRequestParameterMap()
                .get("clickedUnitId");

        if (idParam != null) {
            addRecordingUnitToOverview(Long.parseLong(idParam), targetPanel, 3, false);
        }


    }


    public void addSpatialUnitToOverview(Long id, AbstractPanel targetPanel,  @Nullable Integer tabIndex) {
        addSpatialUnitToOverview(id, targetPanel, tabIndex, true);
    }

    public void addSpatialUnitToOverview(Long id, AbstractPanel targetPanel, @Nullable Integer tabIndex, boolean updateMainPanel) {

        if (targetPanel != null) {
            // Add the overview
            SpatialUnitPanel overviewPanel = panelFactory.createSpatialUnitPanel(id);
            if(tabIndex!=null) {
                overviewPanel.setActiveTabIndex(tabIndex);
            }
            overviewPanel.setRoot(false);
            if(targetPanel.isRoot()) {
                addPanelToOverview(targetPanel, overviewPanel, updateMainPanel);
            }
            else {
                addPanelToOverview(targetPanel.getParentOrOverview(), overviewPanel, updateMainPanel);
            }
        }
    }

    public void addActionUnitToOverview(Long id, AbstractPanel targetPanel, @Nullable Integer tabIndex) {
        addActionUnitToOverview(id, targetPanel, tabIndex, true);
    }

    public void addActionUnitToOverview(Long id, AbstractPanel targetPanel, @Nullable Integer tabIndex, boolean updateMainPanel) {

        if (targetPanel != null) {
            // Add the overview
            ActionUnitPanel overviewPanel = panelFactory.createActionUnitPanel(id);
            if(tabIndex!=null) {
                overviewPanel.setActiveTabIndex(tabIndex);
            }
            overviewPanel.setRoot(false);
            if(targetPanel.isRoot()) {
                addPanelToOverview(targetPanel, overviewPanel, updateMainPanel);
            }
            else {
                addPanelToOverview(targetPanel.getParentOrOverview(), overviewPanel, updateMainPanel);
            }
        }
    }

    // Bridge for the React main panel's client-side-opened overview (plan §7.3/§8 phase 5,
    // "setOverview"): the overview is rendered optimistically by React before the server knows
    // about it, so unlike addActionUnitToOverview above (called from a JSF row click that already
    // has the id in EL scope), this one reads it off the raw request — exactly like
    // addRecordingUnitToOverviewFromStratiModule does for the same reason. updateMainPanel=false:
    // the React-owned main pane must never be touched by this server round-trip.
    //
    // Dispatches on `entityType` — the same registry key entities/<type>/config.tsx registers
    // under (reactPanelBootstrap.js's setOverviewFn sends it straight off EntityListPanel's
    // onOpenOverview call). Before this method existed, every entity type's setOverview call fell
    // through to this method's own predecessor (action-unit-only), so opening e.g. a recording
    // unit from a related-list tab put the WRONG entity (an action unit sharing that same id) into
    // parentOrOverview server-side — invisible until F5 or an overview-toolbar action (duplicate/
    // refresh/settings/fullscreen) acted on the wrong thing. An unrecognized/missing entityType is
    // a no-op rather than a guess, matching the null-idParam case just below.
    public void addEntityToOverviewFromRequest(AbstractPanel targetPanel) {
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext()
                .getRequestParameterMap();
        String idParam = params.get("id");
        String entityType = params.get("entityType");

        if (idParam == null || entityType == null) {
            return;
        }

        Long id = Long.parseLong(idParam);
        switch (entityType) {
            case "project" -> addActionUnitToOverview(id, targetPanel, null, false);
            case "recordingUnit" -> addRecordingUnitToOverview(id, targetPanel, null, false);
            case "find" -> addSpecimenToOverview(id, targetPanel, null, false);
            case "phase" -> addPhaseToOverview(id, targetPanel, null, false);
            case "container" -> addContainerToOverview(id, targetPanel, null, false);
            case "place" -> addSpatialUnitToOverview(id, targetPanel, null, false);
            default -> log.warn("setOverview : entityType React inconnu côté serveur : {}", entityType);
        }
    }

    public void addSpecimenToOverview(Long id, AbstractPanel targetPanel, @Nullable Integer tabIndex) {
        addSpecimenToOverview(id, targetPanel, tabIndex, true);
    }

    public void addSpecimenToOverview(Long id, AbstractPanel targetPanel, @Nullable Integer tabIndex, boolean updateMainPanel) {

        if (targetPanel != null) {
            // Add the overview
            SpecimenPanel overviewPanel = panelFactory.createSpecimenPanel(id);
            if(tabIndex!=null) {
                overviewPanel.setActiveTabIndex(tabIndex);
            }
            overviewPanel.setRoot(false);
            if(targetPanel.isRoot()) {
                addPanelToOverview(targetPanel, overviewPanel, updateMainPanel);
            }
            else {
                addPanelToOverview(targetPanel.getParentOrOverview(), overviewPanel, updateMainPanel);
            }
        }
    }

    public void addPhaseToOverview(Long id, AbstractPanel targetPanel, @Nullable Integer tabIndex) {
        addPhaseToOverview(id, targetPanel, tabIndex, true);
    }

    public void addPhaseToOverview(Long id, AbstractPanel targetPanel, @Nullable Integer tabIndex, boolean updateMainPanel) {
        if (targetPanel != null) {
            PhasePanel overviewPanel = panelFactory.createPhasePanel(id);
            if (tabIndex != null) {
                overviewPanel.setActiveTabIndex(tabIndex);
            }
            overviewPanel.setRoot(false);
            if (targetPanel.isRoot()) {
                addPanelToOverview(targetPanel, overviewPanel, updateMainPanel);
            } else {
                addPanelToOverview(targetPanel.getParentOrOverview(), overviewPanel, updateMainPanel);
            }
        }
    }

    public void addContainerToOverview(Long id, AbstractPanel targetPanel, @Nullable Integer tabIndex) {
        addContainerToOverview(id, targetPanel, tabIndex, true);
    }

    public void addContainerToOverview(Long id, AbstractPanel targetPanel, @Nullable Integer tabIndex, boolean updateMainPanel) {

        if (targetPanel != null) {
            ContainerPanel overviewPanel = panelFactory.createContainerPanel(id);
            if (tabIndex != null) {
                overviewPanel.setActiveTabIndex(tabIndex);
            }
            overviewPanel.setRoot(false);
            if (targetPanel.isRoot()) {
                addPanelToOverview(targetPanel, overviewPanel, updateMainPanel);
            } else {
                addPanelToOverview(targetPanel.getParentOrOverview(), overviewPanel, updateMainPanel);
            }
        }
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


    public void fullScreen(AbstractPanel panel) throws IOException {
        // panel = overview panel being expanded; its parentOrOverview = root/main panel
        AbstractPanel mainPanel = panel.getParentOrOverview();
        String contextPath = FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath();
        String encodedMain = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mainPanel.ressourceUri().getBytes(StandardCharsets.UTF_8));
        String encodedOverview = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(panel.ressourceUri().getBytes(StandardCharsets.UTF_8));
        String backUrl = contextPath + "/focus/" + encodedMain + "?s=" + encodedOverview;
        redirectToFocus(panel.ressourceUri(), null, backUrl);
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

    public String getInPlaceFieldMode() {
        if (Boolean.TRUE.equals(isWriteMode)) {
            return "input";
        }
        return "output";
    }

    public String headerName(AbstractPanel panel) {
        try {
            return langBean.msg(panel.getTitleCodeOrTitle());
        } catch (NoSuchMessageException e) {
            return panel.getTitleCodeOrTitle();
        }
    }

    public boolean userHasAddSpatialOrActionUnitPermission() {
        UserInfo info = sessionSettings.getUserInfo();
        return profilePermissionService.hasInstancePermission(info.getUser(), PermissionConstants.INSTANCE_MANAGE_SETTINGS)
                || profilePermissionService.hasOrganizationPermission(info, PermissionConstants.ORGANIZATION_MANAGE_PLACES)
                || profilePermissionService.hasActionUnitCreatePermission(info);
    }

    public String invokeOnClick(MethodExpression method, Long id, AbstractPanel panelModel) {
        if (method != null) {
            method.invoke(FacesContext.getCurrentInstance().getELContext(), new Object[]{id, panelModel});
        }
        return null; // for commandLink action return
    }

    /**
     * Is creation of new action units allowed?
     *
     * @return true if creation is allowed
     */
    public boolean isActionUnitCreateAllowed() {
        return profilePermissionService.hasActionUnitCreatePermission(sessionSettings.getUserInfo());
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

    /**
     * cancel changing institution
     *
     */
    public void cancelInstitutionChange(

    ) {
        selectedInstitution = sessionSettings.getSelectedInstitution();
        PrimeFaces.current().ajax().update("searchBarCsrfForm:searchBarForm", "toggleButtonSidebarPanelCsrfForm");
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

    /**
     * Return the active actions units for which i'm a member
     */
    public List<ActionUnitDTO> getMyActionUnits() {
        return actionUnitService.findByTeamMember(
                sessionSettings.getUserInfo().getUser(),
                sessionSettings.getSelectedInstitution(),
                10);
    }
}
