package fr.siamois.ui.bean.panel;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.events.InstitutionChangeEvent;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>This ui.bean handles the home page</p>
 * <p>It is used to display the list of spatial units without parents</p>
 *
 * @author Grégory Bliault
 */
@Slf4j
@Component
@SessionScoped
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

    public static final String READ_MODE = "READ";
    public static final String WRITE_MODE = "WRITE";

    // locals
    private transient DashboardModel responsiveModel;
    private static final String RESPONSIVE_CLASS = "col-12 lg:col-6 xl:col-6";
    private String readWriteMode = READ_MODE;
    private static final int MAX_NUMBER_OF_PANEL = 10;

    // Search bar
    private List<SpatialUnit> fSpatialUnits = List.of();
    private List<ActionUnit> fActionUnits = List.of();
    private SpatialUnit fSelectedSpatialUnit;
    private ActionUnit fSelectedActionUnit;

    @Getter
    private transient List<AbstractPanel> panels = new ArrayList<>();
    private transient int fullscreenPanelIndex = -1;

    private transient Set<AbstractSingleEntityPanel<?>> unsavedPanels = new HashSet<>();

    public void init() {
        fullscreenPanelIndex = -1;
        panels = new ArrayList<>();
        addWelcomePanel();
        Institution institution = sessionSettings.getSelectedInstitution();
        fSpatialUnits = spatialUnitService.findAllOfInstitution(institution);
    }

    @EventListener(InstitutionChangeEvent.class)
    public void handleInstitutionChange() {
        init();
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
        if(fullscreenPanelIndex >= 0) {
            fullscreenPanelIndex = 0;
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


    public void goToSpatialUnitByIdNewPanel(Long id, AbstractPanel currentPanel) {
        // Create new panel type and add items to its breadcrumb
        SpatialUnitPanel newPanel = panelFactory.createSpatialUnitPanel(id, currentPanel.getBreadcrumb());
        addPanel(newPanel);
    }

    public void goToSpatialUnitByIdNewPanel(Long id, AbstractPanel currentPanel, Integer activeIndex) {
        // Create new panel type and add items to its breadcrumb
        SpatialUnitPanel newPanel = panelFactory.createSpatialUnitPanel(id, currentPanel.getBreadcrumb(), activeIndex);
        addPanel(newPanel);
    }



    public void  goToRecordingUnitByIdCurrentPanel(Long id, Integer currentPanelIndex) {

        RecordingUnitPanel newPanel = panelFactory.createRecordingUnitPanel(id, panels.get(currentPanelIndex).getBreadcrumb());
        panels.set(currentPanelIndex, newPanel);

    }

    public void  goToRecordingUnitByIdNewPanel(Long id, Integer currentPanelIndex) {

        RecordingUnitPanel newPanel = panelFactory.createRecordingUnitPanel(id, panels.get(currentPanelIndex).getBreadcrumb());
        addPanel(newPanel);

    }

    public void  goToRecordingUnitByIdNewPanel(Long id, Integer currentPanelIndex, Integer tabIndex) {

        RecordingUnitPanel newPanel = panelFactory.createRecordingUnitPanel(id, panels.get(currentPanelIndex).getBreadcrumb(), tabIndex);
        addPanel(newPanel);

    }

    public void  goToRecordingUnitByIdNewPanel(Long id, AbstractPanel panel) {

        RecordingUnitPanel newPanel = panelFactory.createRecordingUnitPanel(id, panel.getBreadcrumb());
        addPanel(newPanel);

    }

    public void  goToSpecimenByIdNewPanel(Long id, AbstractPanel currentPanel) {

        SpecimenPanel newPanel = panelFactory.createSpecimenPanel(id, currentPanel.getBreadcrumb());
        addPanel(newPanel);

    }

    public void  goToSpecimenByIdNewPanel(Long id, Integer currentPanelIndex) {

        SpecimenPanel newPanel = panelFactory.createSpecimenPanel(id, panels.get(currentPanelIndex).getBreadcrumb());
        addPanel(newPanel);

    }

    public void goToActionUnitByIdNewPanel(Long id, Integer currentPanelIndex) {
        // Create new panel type and add items to its breadcrumb
        ActionUnitPanel newPanel = panelFactory.createActionUnitPanel(id, panels.get(currentPanelIndex).getBreadcrumb());
        addPanel(newPanel);
    }

    public void goToActionUnitByIdNewPanel(Long id, AbstractPanel panel) {
        // Create new panel type and add items to its breadcrumb
        ActionUnitPanel newPanel = panelFactory.createActionUnitPanel(id, panel.getBreadcrumb());
        addPanel(newPanel);
    }

    public void goToActionUnitByIdNewPanel(Long id, AbstractPanel panel, Integer activeTabIndex) {
        // Create new panel type and add items to its breadcrumb
        ActionUnitPanel newPanel = panelFactory.createActionUnitPanel(id, panel.getBreadcrumb(), activeTabIndex);
        addPanel(newPanel);
    }

    public void goToSpatialUnitByIdCurrentPanel(Long id, AbstractPanel currentPanel) {
        // Change current panel type add item to its breadcrumb
        int index = panels.indexOf(currentPanel);
        if (index != -1) {
            SpatialUnitPanel newPanel = panelFactory.createSpatialUnitPanel(id, currentPanel.getBreadcrumb());
            panels.set(index, newPanel);

        }

    }

    public void goToActionUnitByIdCurrentPanel(Long id, Integer currentPanelIndex) {
        // Change current panel type add item to its breadcrumb
        int index = currentPanelIndex;
        ActionUnitPanel newPanel = panelFactory.createActionUnitPanel(id, panels.get(currentPanelIndex).getBreadcrumb());
        panels.set(index, newPanel);
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

    public void handleToggleOfPanelAtIndex(int idx)
    {
        AbstractPanel panel = panels.get(idx);
        panel.setCollapsed(!panel.getCollapsed());
    }

    public void closePanelAtIndex(int idx) {
        if (panels == null || panels.isEmpty() || idx < 0 || idx >= panels.size()) {
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
        if(fullscreenPanelIndex > 0) {
            if(fullscreenPanelIndex > panels.size() - 1) {
                fullscreenPanelIndex = 0;
            }
            PrimeFaces.current().ajax().update("flow");
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
        if (readWriteMode.equals(READ_MODE)) {
            fillAllUnsavedPanel();
            if (unsavedPanels.isEmpty()) {
                PrimeFaces.current().ajax().update("flow");
                return;
            }

            readWriteMode = WRITE_MODE;
            PrimeFaces.current().executeScript("PF('confirmUnsavedDialog').show();");
        } else {
            PrimeFaces.current().ajax().update("flow");
        }
    }

    public void saveAllPanels() {
        for (AbstractSingleEntityPanel<?> panel : unsavedPanels) {
            boolean entityHasBeenSaved = panel.save(true);
            if (!entityHasBeenSaved) {
                String title = findMatchingTitle(panel);
                MessageUtils.displayErrorMessage(langBean, "dialog.unsaved.error", title);
                return;
            }
        }
        readWriteMode = READ_MODE;
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
        readWriteMode = READ_MODE;
    }

    public String getInPlaceFieldMode() {
        if (readWriteMode.equals(WRITE_MODE)) {
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
        return actionUnitService.hasCreatePermission(sessionSettings.getUserInfo());
    }
}