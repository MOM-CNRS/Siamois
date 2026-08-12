package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.actionunit.FailedActionUnitSaveException;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.form.EffectiveFormResolver;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.specs.SpecimenSpec;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.bean.panel.models.panel.single.tab.RecordingTab;
import fr.siamois.ui.lazydatamodel.RecordingUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.SpecimenLazyDataModel;
import fr.siamois.ui.table.ToolbarCreateConfig;
import fr.siamois.ui.table.definitions.RecordingUnitTableDefinitionFactory;
import fr.siamois.ui.table.viewmodel.RecordingUnitTableViewModel;
import fr.siamois.utils.MessageUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.model.menu.DefaultMenuItem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static fr.siamois.dto.FilterDTO.FilterType.CONTAINS;
import static fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec.ACTION_UNIT_FILTER;

/**
 * <p>This bean handles the spatial unit page</p>
 *
 * @author Grégory Bliault
 */
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Slf4j
@Getter
@Setter
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ActionUnitPanel extends AbstractSingleEntityPanel<ActionUnitDTO> implements Serializable {

    private final RedirectBean redirectBean;
    private final transient LabelService labelService;
    private final transient RecordingUnitService recordingUnitService;
    private final transient SpecimenService specimenService;
    private final transient NavBean navBean;
    private final transient GenericNewUnitDialogBean<?> genericNewUnitDialogBean;
    private final transient InstitutionService institutionService;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient EffectiveFormResolver effectiveFormResolver;

    // For entering new code
    private ActionCode newCode;
    private Integer newCodeIndex; // Index of the new code, if primary: 0, otherwise 1 to N
    // (but corresponds to 0 to N-1 in secondary code list)

    // Field related
    private Boolean editType;
    private Concept fType;

    private transient RecordingUnitTableViewModel recordingTabTableModel;

    // Inline identifier editing (panel header chip)
    private boolean editingIdentifier;
    private String editingIdentifierValue;

    @Override
    protected boolean documentExistsInUnitByHash(ActionUnitDTO unit, String hash) {
        return documentService.existInActionUnitByHash(unit, hash);
    }

    @Override
    protected void addDocumentToUnit(Document doc, ActionUnitDTO unit) {
        documentService.addToActionUnit(doc, unit);
    }


    private transient List<ActionCode> secondaryActionCodes;

    // Linked recording units
    private Integer totalRecordingUnitCount;
    private Integer totalSpecimenCount;


    public ActionUnitPanel(ApplicationContext context) {
        super("Unité d'action", "bi bi-arrow-down-square", "siamois-panel action-unit-panel single-panel",
                context);

        this.redirectBean = context.getBean(RedirectBean.class);
        this.labelService = context.getBean(LabelService.class);
        this.recordingUnitService = context.getBean(RecordingUnitService.class);
        this.specimenService = context.getBean(SpecimenService.class);
        this.navBean = context.getBean(NavBean.class);
        this.genericNewUnitDialogBean = context.getBean(GenericNewUnitDialogBean.class);
        this.institutionService = context.getBean(InstitutionService.class);
        this.profilePermissionService = context.getBean(ProfilePermissionService.class);
        this.effectiveFormResolver = context.getBean(EffectiveFormResolver.class);
    }


    public String entityRessourceUri() {
        return String.format("/action-unit/%s", unit.getId());
    }



    public void refreshUnit() {

        // reinit
        errorMessage = null;
        unit = null;
        newCode = new ActionCode();
        secondaryActionCodes = new ArrayList<>();

        try {

            unit = actionUnitService.findById(unitId);
            this.setTitleCodeOrTitle(unit.getName()); // Set panel title

            this.titleCodeOrTitle = unit.getName();

            initForms(true);


            // Get all the CHILDREN of the spatial unit
            selectedCategoriesChildren = new ArrayList<>();
            totalChildrenCount = 0;
            // Get all the Parentsof the spatial unit
            selectedCategoriesParents = new ArrayList<>();
            totalParentsCount = 0;


        } catch (RuntimeException e) {
            this.errorMessage = "Failed to load action unit: " + e.getMessage();
        }


        documents = documentService.findForActionUnit(unit);
    }

    public void startEditIdentifier() {
        this.editingIdentifierValue = unit.getFullIdentifier();
        this.editingIdentifier = true;
    }

    public void cancelEditIdentifier() {
        this.editingIdentifier = false;
        this.editingIdentifierValue = null;
    }

    public void applyEditIdentifier() {
        String trimmed = editingIdentifierValue == null ? "" : editingIdentifierValue.trim();
        if (trimmed.isEmpty()) {
            MessageUtils.displayWarnMessage(langBean, "actionunit.error.identifier.blank");
            return;
        }

        String previous = unit.getFullIdentifier();
        unit.setFullIdentifier(trimmed);

        if (actionUnitService.fullIdentifierAlreadyExistInInstitution(unit)) {
            unit.setFullIdentifier(previous);
            MessageUtils.displayWarnMessage(langBean, "actionunit.error.identifier.alreadyExists");
            return;
        }

        try {
            actionUnitService.save(unit);
            cancelEditIdentifier();
        } catch (FailedActionUnitSaveException e) {
            unit.setFullIdentifier(previous);
            MessageUtils.displayErrorMessage(langBean, "common.entity.actionUnit.updateFailed", unit.getFullIdentifier());
        }
    }

    @Override
    public void init() {
        try {

            if (unitId == null) {
                this.errorMessage = "The ID of the spatial unit must be defined";
                return;
            }

            refreshUnit();

            if (this.unit == null) {
                log.error("The Action Unit page should not be accessed without ID or by direct page path");
                errorMessage = "The Action Unit page should not be accessed without ID or by direct page path";
                return;
            }

            initRecordingTab();

            SpecimenLazyDataModel specimenLazyDataModel = new SpecimenLazyDataModel(specimenService, sessionSettingsBean, langBean);
            specimenLazyDataModel.withConstantFilter(SpecimenSpec.ACTION_UNIT_FILTER, List.of(unit.getId()), FilterDTO.FilterType.CONTAINS);
            specimenLazyDataModel.setSelectedUnits(new ArrayList<>());

            totalSpecimenCount = specimenService.countByActionContext(unit);

            RecordingTab recordingTab = new RecordingTab(
                    "common.entity.recordingUnits",
                    "bi bi-pencil-square",
                    "recordingTab",
                    recordingTabTableModel,
                    totalRecordingUnitCount);

            tabs.add(recordingTab);


        } catch (
                ActionUnitNotFoundException e) {
            log.error("Action unit with id {} not found", unitId);
            redirectBean.redirectTo(HttpStatus.NOT_FOUND);
        } catch (
                RuntimeException e) {
            this.errorMessage = "Failed to load action unit: " + e.getMessage();
            redirectBean.redirectTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<PersonDTO> authorsAvailable() {
        return List.of();
    }

    @Override
    protected String getFocusPath(Long id) {
        return "/action-unit/"+id;
    }

    @Override
    protected void addToOverview(Long id, AbstractPanel parentOrOverview, Integer activeTabIndex) {
        flowBean.addActionUnitToOverview(id,parentOrOverview, activeTabIndex);
    }

    @Override
    protected ActionUnitDTO findNext() {
        return actionUnitService.findPreviousByInstitution(unit.getCreatedByInstitution(), unit);

    }

    @Override
    protected ActionUnitDTO findPrevious() {
        return actionUnitService.findNextByInstitution(unit.getCreatedByInstitution(), unit);
    }

    @Override
    public void toggleValidate() {
        unit = actionUnitService.toggleValidated(unit.getId());
    }

    @Override
    ActionUnitDTO findUnitById(Long id) {
        return actionUnitService.findById(id);
    }





    @Override
    public void initForms(boolean forceInit) {

        detailsForm = ActionUnit.DETAILS_FORM;
        // Init system form answers
        initFormContext(forceInit);

    }

    @Override
    protected String getFormScopePropertyName() {
        return "";
    }

    @Override
    protected void setFormScopePropertyValue(ConceptDTO concept) {
        // to be implemented
    }



    @Override
    public void visualise(RevisionWithInfo<ActionUnitDTO> history) {
        // button is deactivated
    }

    @Override
    public boolean save(Boolean validated) {
        return formContext.save();
    }
    

    @Override
    public String displayHeader() {
        return "/panel/header/actionUnitPanelHeader.xhtml";
    }

    @Override
    public boolean canOpenInProjectSettings() {
        return true;
    }

    @Override
    public UnitKind getCreationUnitKind() {
        return UnitKind.ACTION;
    }

    public void addNewSecondaryCode() {
        ActionCode code = new ActionCode();
        Concept c = new Concept();
        code.setCode("");
        code.setType(c);
        secondaryActionCodes.add(code);
    }

    @Override
    public String getAutocompleteClass() {
        return "action-unit-autocomplete";
    }

    public void initNewActionCode(int index) {
        newCodeIndex = index;
        newCode = new ActionCode();
    }

    public static class ActionUnitPanelBuilder {

        private final ActionUnitPanel actionUnitPanel;

        public ActionUnitPanelBuilder(ObjectProvider<ActionUnitPanel> actionUnitPanelProvider) {
            this.actionUnitPanel = actionUnitPanelProvider.getObject();
        }

        public ActionUnitPanelBuilder id(Long id) {
            actionUnitPanel.setUnitId(id);
            return this;
        }

        public ActionUnitPanelBuilder breadcrumb(PanelBreadcrumb breadcrumb) {
            actionUnitPanel.setBreadcrumb(breadcrumb);

            return this;
        }

        public ActionUnitPanelBuilder activeIndex(Integer id) {
            actionUnitPanel.setActiveTabIndex(id);
            return this;
        }


        public ActionUnitPanel build() {
            actionUnitPanel.init();
            return actionUnitPanel;
        }
    }

    public void goToMemberList() {
        redirectBean.redirectTo(String.format("/settings/organisation/actionunit/%s/members", unit.getId()));
    }

    public void initRecordingTab() {
        RecordingUnitLazyDataModel actionLazyDataModel = new RecordingUnitLazyDataModel(
                recordingUnitService,
                sessionSettingsBean,
                langBean
        );

        actionLazyDataModel.withConstantFilter(ACTION_UNIT_FILTER, List.of(unit.getId()), CONTAINS);

        totalRecordingUnitCount = recordingUnitService.countByActionContext(unit);

        recordingTabTableModel = new RecordingUnitTableViewModel(
                actionLazyDataModel,
                formService,
                sessionSettingsBean,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                flowBean,
                (GenericNewUnitDialogBean<RecordingUnitDTO>) genericNewUnitDialogBean,
                profilePermissionService,
                recordingUnitService,
                langBean,
                formContextServices,
                effectiveFormResolver
        );
        recordingTabTableModel.setParentPanel(this);

        RecordingUnitTableDefinitionFactory.applyTo(recordingTabTableModel);

        // configuration du bouton creer
        recordingTabTableModel.setToolbarCreateConfig(
                ToolbarCreateConfig.builder()
                        .kindToCreate(UnitKind.RECORDING)
                        .scopeSupplier(() ->
                                NewUnitContext.Scope.builder()
                                        .key("ACTION")
                                        .entityId(unit.getId())
                                        .build()
                        )
                        .build()
        );
    }

    @Override
    public String getTabView() {
        return "/panel/tabview/actionUnitTabView.xhtml";
    }

    @Override
    protected DefaultMenuItem createRootTypeItem()
    {

        return DefaultMenuItem.builder()
                .value(langBean.msg("panel.title.allactionunit"))
                .id("allActionUnits")
                .command("#{navBean.redirectToBookmarked('/action-unit/')}")
                .update("flow")
                .onstart(PF_BUI_CONTENT_SHOW)
                .oncomplete(PF_BUI_CONTENT_HIDE)
                .process(THIS)
                .build();
    }

    @Override
    public String getPrefixPanelIndex() {
        return "action-unit-"+ unitId;
    }

    @Override
    public String svgIcon() {
        return "/resources/img/svg/arrow-down-square.svg";
    }

    @Override
    public String getPanelTypeClass() {
        return "spatial-unit";
    }


}