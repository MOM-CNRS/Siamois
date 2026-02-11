package fr.siamois.ui.bean.panel;

import fr.siamois.domain.events.publisher.InstitutionChangeEventPublisher;
import fr.siamois.domain.events.publisher.LoginEventPublisher;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.events.InstitutionChangeEvent;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.recordingunit.StratigraphicRelationshipService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.bean.panel.models.panel.WelcomePanel;
import fr.siamois.ui.bean.panel.models.panel.single.*;
import fr.siamois.utils.MessageUtils;
import jakarta.el.MethodExpression;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.primefaces.model.dashboard.DashboardModel;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

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
    private final transient PermissionService permissionService;
    private final transient InstitutionService institutionService;
    private final transient InstitutionChangeEventPublisher institutionChangeEventPublisher;

    private final RedirectBean redirectBean;
    private final transient LoginEventPublisher loginEventPublisher;

    // locals
    private transient DashboardModel responsiveModel;
    private static final String RESPONSIVE_CLASS = "col-12 lg:col-6 xl:col-6";
    private Boolean isWriteMode = true;
    private Boolean isFieldMode = false;
    private static final int MAX_NUMBER_OF_PANEL = 10;

    // Search bar
    private List<SpatialUnit> fSpatialUnits = List.of();
    private List<Institution> institutions = List.of();
    private List<ActionUnit> fActionUnits = List.of();
    private SpatialUnit fSelectedSpatialUnit;
    private ActionUnit fSelectedActionUnit;
    private Institution selectedInstitution;

    @Getter
    private transient List<AbstractPanel> panels = new ArrayList<>();
    private transient int fullscreenPanelIndex = -1;

    private transient Set<AbstractSingleEntityPanel<?>> unsavedPanels = new HashSet<>();


    public void init() {
        fullscreenPanelIndex = -1;
        panels = new ArrayList<>();
        addWelcomePanel();
        Institution institution = sessionSettings.getSelectedInstitution();
        UserInfo info = sessionSettings.getUserInfo();
        institutions = new ArrayList<>();
        institutions.addAll(institutionService.findInstitutionsOfPerson(info.getUser()));
        fSpatialUnits = spatialUnitService.findAllOfInstitution(institution.getId());
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

    public void addSpatialUnitListPanel(PanelBreadcrumb bc) {
        addPanel(panelFactory.createSpatialUnitListPanel(bc));
    }

    public void addActionUnitListPanel(PanelBreadcrumb bc) {
        addPanel(panelFactory.createActionUnitListPanel(bc));
    }

    public void addRecordingUnitListPanel(PanelBreadcrumb bc) {
        addPanel(panelFactory.createRecordingUnitListPanel(bc));
    }

    public void addSpecimenListPanel(PanelBreadcrumb bc) {
        addPanel(panelFactory.createSpecimenListPanel(bc));
    }


    public void addPanel(AbstractPanel panel) {

        if (panels == null || panels.isEmpty()) {
            panels = new ArrayList<>();
        }

        // If panel already exists, move it to the top
        panels.remove(panel);
        panels.add(0, panel);

        // Trim the list if it exceeds max allowed
        if (panels.size() > MAX_NUMBER_OF_PANEL) {
            panels = new ArrayList<>(panels.subList(0, MAX_NUMBER_OF_PANEL));
        }

        if (panels.size() == 1) {
            // Only one panel: open it
            panels.get(0).setCollapsed(false);
        } else {
            // Collapse all except the first
            for (int i = 1; i < panels.size(); i++) {
                panels.get(i).setCollapsed(true);
            }
            // Ensure the top one is open
            panels.get(0).setCollapsed(false);
        }

        //if fullscreen set this new panel as the active one
        if (fullscreenPanelIndex >= 0) {
            fullscreenPanelIndex = 0;
        }

        // Update context form for sync
        FacesContext facesContext = FacesContext.getCurrentInstance();
        // Check if the current request is an AJAX request
        if (facesContext != null) {
            PrimeFaces.current().ajax().update("contextForm");
        }

    }


    public void addWelcomePanel() {

        // Add a new instance
        addPanel(panelFactory.createWelcomePanel());

    }


    public void addActionUnitPanel(Long actionUnitId) {
        addPanel(panelFactory.createActionUnitPanel(actionUnitId));
    }

    public void addRecordingUnitPanel(Long recordingUnitId) {
        addPanel(panelFactory.createRecordingUnitPanel(recordingUnitId));
    }

    public void addSpecimenPanel(Long specimenId) {
        addPanel(panelFactory.createSpecimenPanel(specimenId));
    }


    public void goToSpatialUnitByIdNewPanel(Long id,Integer activeIndex) {
        // Create new panel type and add items to its breadcrumb
        SpatialUnitPanel newPanel = panelFactory.createSpatialUnitPanel(id, activeIndex);
        addPanel(newPanel);
    }

    public void goToSpatialUnitByIdNewPanel(Long id) {

        SpatialUnitPanel newPanel = panelFactory.createSpatialUnitPanel(id);
        addPanel(newPanel);

    }

    public void goToRecordingUnitByIdNewPanel(Long id) {

        RecordingUnitPanel newPanel = panelFactory.createRecordingUnitPanel(id);
        addPanel(newPanel);

    }


    public void goToRecordingUnitByIdNewPanel(Long id, Integer tabIndex) {

        RecordingUnitPanel newPanel = panelFactory.createRecordingUnitPanel(id, tabIndex);
        addPanel(newPanel);

    }


    public void goToSpecimenByIdNewPanel(Long id, AbstractPanel currentPanel) {

        SpecimenPanel newPanel = panelFactory.createSpecimenPanel(id, currentPanel.getBreadcrumb());
        addPanel(newPanel);

    }

    public void goToSpecimenByIdNewPanel(Long id) {

        SpecimenPanel newPanel = panelFactory.createSpecimenPanel(id);
        addPanel(newPanel);

    }


    public void goToActionUnitByIdNewPanel(Long id) {
        // Create new panel type and add items to its breadcrumb
        ActionUnitPanel newPanel = panelFactory.createActionUnitPanel(id);
        addPanel(newPanel);
    }

    public void goToActionUnitByIdNewPanel(Long id, Integer activeTabIndex) {
        // Create new panel type and add items to its breadcrumb
        ActionUnitPanel newPanel = panelFactory.createActionUnitPanel(id, activeTabIndex);
        addPanel(newPanel);
    }



    public void fullScreen(AbstractPanel panel) {
        // Could use setter if we don't add more code
        int index = panels.indexOf(panel);
        if (index != -1) {
            fullscreenPanelIndex = index;
        }
    }

    public void closeFullScreen() {
        fullscreenPanelIndex = -1;
    }


    public void addSpatialUnitPanel(Long id) {
        addPanel(panelFactory.createSpatialUnitPanel(id));
    }

    public void handleToggleOfPanel(String panelId) {
        if (panels == null || panels.isEmpty()) {
            return;
        }

        // Find the index of the panel with the given panelId
        int idx = getPanelIndex(panelId);
        if (idx == -1) {
            // Panel not found
            return;
        }
        AbstractPanel panel = panels.get(idx);
        panel.setCollapsed(!panel.getCollapsed());
    }

    private int getPanelIndex(String panelId) {
        for (int i = 0; i < panels.size(); i++) {
            if (panels.get(i).getPanelIndex().equals(panelId)) {
                return i;
            }
        }
        return -1; // Panel not found
    }

    public void closePanel(String panelId) {
        if (panels == null || panels.isEmpty()) {
            return;
        }

        // Find the index of the panel with the given panelId
        int idx = getPanelIndex(panelId);
        if (idx == -1) {
            // Panel not found
            return;
        }

        panels.remove(idx);

        // If only one panel is left, uncollapse it
        if (panels.size() == 1 || (idx == 0 && !panels.isEmpty())) {
            panels.get(0).setCollapsed(false);
        }
        // If no panel left, open the homepanel
        else if (panels.isEmpty()) {
            addWelcomePanel();
            PrimeFaces.current().ajax().update("flow");
        }

        // If fullscreen, update the whole flow and check that the index is valid
        if (fullscreenPanelIndex > 0) {
            if (fullscreenPanelIndex > panels.size() - 1) {
                fullscreenPanelIndex = 0;
            }
            PrimeFaces.current().ajax().update("flow");
        }

        // Update context form for sync
        FacesContext facesContext = FacesContext.getCurrentInstance();
        // Check if the current request is an AJAX request
        if (facesContext != null) {
            PrimeFaces.current().ajax().update("contextForm");
        }

    }

    private void fillAllUnsavedPanel() {
        unsavedPanels.clear();
        for (AbstractPanel panel : panels) {
            if (panel instanceof AbstractSingleEntityPanel<?> singleEntity && singleEntity.isHasUnsavedModifications()) {
                unsavedPanels.add(singleEntity);
            }
        }
    }

    /**
     * Listener called when the ReadWrite mode variable is flipped.
     */
    public void changeReadWriteMode() {
        if (Boolean.FALSE.equals(isWriteMode)) {
            fillAllUnsavedPanel();
            if (unsavedPanels.isEmpty()) {
                PrimeFaces.current().ajax().update("flow");
                return;
            }

            isWriteMode = true;
            PrimeFaces.current().executeScript("PF('confirmUnsavedDialog').show();");
        } else {
            PrimeFaces.current().ajax().update("flow");
        }
    }

    /**
     * Listener called when the FieldOffice mode variable is flipped.
     */
    public void changeFieldOfficeMode() {
        // Listener called when the FieldOffice mode variable is flipped.
    }

    /**
     * Save all open panels and return true if succeeded
     */
    public boolean saveAllPanelsMethod() {
        for (AbstractSingleEntityPanel<?> panel : unsavedPanels) {
            boolean entityHasBeenSaved = panel.save(true);
            if (!entityHasBeenSaved) {
                String title = findMatchingTitle(panel);
                MessageUtils.displayErrorMessage(langBean, "dialog.unsaved.error", title);
                return false;
            }
        }
        return true;
    }

    public void saveAllPanels() {
        if(saveAllPanelsMethod()) {
            isWriteMode = false;
            PrimeFaces.current().ajax().update("readWriteSwitchForm");
        }
    }

    private static String findMatchingTitle(AbstractSingleEntityPanel<?> panel) {
        String title = "UNKNOWN";
        if (panel.getUnit() instanceof SpatialUnit su) {
            title = su.getName();
        } else if (panel.getUnit() instanceof ActionUnit au) {
            title = au.getFullIdentifier();
        } else if (panel.getUnit() instanceof RecordingUnit ru) {
            title = ru.getFullIdentifier();
        } else if (panel.getUnit() instanceof Specimen sp) {
            title = sp.getFullIdentifier();
        }
        return title;
    }

    public void undoChangesOnAllPanels() {
        for (AbstractSingleEntityPanel<?> panel : unsavedPanels) {
            panel.cancelChanges();
        }
        isWriteMode = false;
        PrimeFaces.current().ajax().update("readWriteSwitchForm");
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
        return info.getUser().isSuperAdmin() || permissionService.isActionManager(info) || permissionService.isInstitutionManager(info);
    }

    public String invokeOnClick(MethodExpression method, Long id, AbstractPanel panelModel) {
        if (method != null) {
            method.invoke(FacesContext.getCurrentInstance().getELContext(), new Object[]{id, panelModel});
        }
        return null; // for commandLink action return
    }

    public void updateHomePanel() {
        for (AbstractPanel panel : panels) {
            if (panel instanceof WelcomePanel welcomePanel) {
                welcomePanel.init();
            }
        }
    }

    /**
     * Is creation of new action units allowed?
     *
     * @return true if creation is allowed
     */
    public boolean isActionUnitCreateAllowed() {
        return permissionService.isInstitutionManager(sessionSettings.getUserInfo())
                || permissionService.isActionManager(sessionSettings.getUserInfo());
    }

    /**
     * Do change institution
     *
     */
    public void changeInstitution(boolean withSave) {
        if(withSave && !saveAllPanelsMethod()) {
            selectedInstitution = sessionSettings.getSelectedInstitution();
            return;
        }

        if (selectedInstitution != null
                && (institutionService.personIsInInstitution(sessionSettings.getUserInfo().getUser(), selectedInstitution)
                || institutionService.isManagerOf(selectedInstitution, sessionSettings.getUserInfo().getUser()))) {
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
        fillAllUnsavedPanel();
        if (unsavedPanels.isEmpty()) {
            changeInstitution(false);
            return;
        }
        PrimeFaces.current().executeScript("PF('confirmUnsavedOnInstitutionDialog').show();");
        PrimeFaces.current().ajax().update("unsavedUpdatesOnInstitutionChangeForm");
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
        if(Boolean.TRUE.equals(isFieldMode)) {
            return langBean.msg("common.label.switchToOfficeMode");
        }
        else {
            return langBean.msg("common.label.switchToFieldMode");
        }
    }

    public String getReadWriteSwitchTooltip() {
        if(Boolean.TRUE.equals(isWriteMode)) {
            return langBean.msg("common.label.switchToReadMode");
        }
        else {
            return langBean.msg("common.label.switchToWriteMode");
        }
    }

    /**
     * Retourne les URIs des panels actuels sous forme de chaîne (ex: "/spatial/1,/action/2").
     * Pour verifier la desynchronisation coté client
     */
    public String getCurrentPanelIdsAsString() {
        return panels.stream()
                .map(AbstractPanel::ressourceUri) // Utilise resourceUri() au lieu des IDs
                .filter(Objects::nonNull) // Ignore les panels sans URI
                .collect(Collectors.joining(","));
    }

    /**
     * Return the active actions units for which i'm a member
     */
    public List<ActionUnit> getMyActionUnits() {
        return actionUnitService.findByTeamMember(sessionSettings.getUserInfo().getUser(),  sessionSettings.getSelectedInstitution());
    }



}