package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.actionunit.FailedActionUnitSaveException;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.authorization.writeverifier.RecordingUnitWriteVerifier;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuIdentifierResolver;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.bean.panel.models.panel.single.tab.ActionSettingsTab;
import fr.siamois.ui.bean.panel.models.panel.single.tab.RecordingTab;
import fr.siamois.ui.bean.settings.team.TeamMembersBean;
import fr.siamois.ui.form.FormUiDto;
import fr.siamois.ui.lazydatamodel.RecordingUnitInActionUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.SpecimenInActionUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.scope.RecordingUnitScope;
import fr.siamois.ui.lazydatamodel.tree.RecordingUnitTreeTableLazyModel;
import fr.siamois.ui.mapper.FormMapper;
import fr.siamois.ui.table.RecordingUnitTableViewModel;
import fr.siamois.ui.table.ToolbarCreateConfig;
import fr.siamois.ui.table.definitions.RecordingUnitTableDefinitionFactory;
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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    public static final String INVALID_FORMAT_CODE = "actionUnit.settings.error.invalidIdentifierFormat";

    private final RedirectBean redirectBean;
    private final transient LabelService labelService;
    private final TeamMembersBean teamMembersBean;
    private final transient RecordingUnitService recordingUnitService;
    private final transient SpecimenService specimenService;
    private final transient NavBean navBean;
    private final transient GenericNewUnitDialogBean<?> genericNewUnitDialogBean;
    private final transient InstitutionService institutionService;
    private final transient RecordingUnitWriteVerifier recordingUnitWriteVerifier;
    private final transient FormMapper formMapper;

    // For entering new code
    private ActionCode newCode;
    private Integer newCodeIndex; // Index of the new code, if primary: 0, otherwise 1 to N
    // (but corresponds to 0 to N-1 in secondary code list)

    // Field related
    private Boolean editType;
    private Concept fType;

    private transient RecordingUnitTableViewModel recordingTabTableModel;


    // Settings tab
    private Integer minNumber;
    private boolean minHasBeenModified = false;
    private Integer maxNumber;
    private boolean maxHasBeenModified = false;
    private String format;

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
    private transient RecordingUnitInActionUnitLazyDataModel recordingUnitListLazyDataModel;
    private Integer totalRecordingUnitCount;
    // Lazy model for recording unit in the spatial unit
    private SpecimenInActionUnitLazyDataModel specimenLazyDataModel;
    private Integer totalSpecimenCount;


    public ActionUnitPanel(ApplicationContext context) {
        super("Unité d'action", "bi bi-arrow-down-square", "siamois-panel action-unit-panel single-panel",
                context);

        this.redirectBean = context.getBean(RedirectBean.class);
        this.labelService = context.getBean(LabelService.class);
        this.teamMembersBean = context.getBean(TeamMembersBean.class);
        this.recordingUnitService = context.getBean(RecordingUnitService.class);
        this.specimenService = context.getBean(SpecimenService.class);
        this.navBean = context.getBean(NavBean.class);
        this.genericNewUnitDialogBean = context.getBean(GenericNewUnitDialogBean.class);
        this.institutionService = context.getBean(InstitutionService.class);
        this.recordingUnitWriteVerifier = context.getBean(RecordingUnitWriteVerifier.class);
        this.formMapper = context.getBean(FormMapper.class);
    }


    @Override
    public String ressourceUri() {
        return String.format("/action-unit/%s", unit.getId());
    }



    public void refreshUnit() {

        // reinit
        errorMessage = null;
        unit = null;
        newCode = new ActionCode();
        secondaryActionCodes = new ArrayList<>();

        try {

            unit = actionUnitService.findById(idunit);
            this.setTitleCodeOrTitle(unit.getName()); // Set panel title
            backupClone = new ActionUnitDTO();
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


        history = historyAuditService.findAllRevisionForEntity(ActionUnitDTO.class, idunit);
        documents = documentService.findForActionUnit(unit);
    }

    @Override
    public void init() {
        try {

            if (idunit == null) {
                this.errorMessage = "The ID of the spatial unit must be defined";
                return;
            }

            refreshUnit();

            if (this.unit == null) {
                log.error("The Action Unit page should not be accessed without ID or by direct page path");
                errorMessage = "The Action Unit page should not be accessed without ID or by direct page path";
            }

            initRecordingTab();

            specimenLazyDataModel = new SpecimenInActionUnitLazyDataModel(
                    specimenService,
                    langBean,
                    unit
            );
            specimenLazyDataModel.setSelectedUnits(new ArrayList<>());

            totalSpecimenCount = specimenService.countByActionContext(unit);

            RecordingTab recordingTab = new RecordingTab(
                    "common.entity.recordingUnits",
                    "bi bi-pencil-square",
                    "recordingTab",
                    recordingTabTableModel,
                    totalRecordingUnitCount);


            ActionSettingsTab settingsTab = new ActionSettingsTab(
                    "nav.configuration",
                    "bi bi-gear",
                    "settingsTab"
            );

            tabs.add(recordingTab);
            tabs.add(settingsTab);


        } catch (
                ActionUnitNotFoundException e) {
            log.error("Action unit with id {} not found", idunit);
            redirectBean.redirectTo(HttpStatus.NOT_FOUND);
        } catch (
                RuntimeException e) {
            this.errorMessage = "Failed to load action unit: " + e.getMessage();
            redirectBean.redirectTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        minNumber = unit.getMinRecordingUnitCode();
        maxNumber = unit.getMaxRecordingUnitCode();
        format = unit.getRecordingUnitIdentifierFormat();
    }

    @Override
    public List<PersonDTO> authorsAvailable() {
        return List.of();
    }

    @Override
    ActionUnitDTO findUnitById(Long id) {
        return actionUnitService.findById(id);
    }

    @Override
    String findLabel(ActionUnitDTO unit) {
        return unit.getName();
    }


    @Override
    String getOpenPanelCommand(ActionUnitDTO unit) {
        return "#{flowBean.addActionUnitPanel(".concat(unit.getId().toString()).concat(")}");
    }

    @Override
    public void initForms(boolean forceInit) {

        detailsForm = formContextServices.getConversionService().convert(ActionUnit.DETAILS_FORM, FormUiDto.class);
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
    public void cancelChanges() {
        unit.setName(backupClone.getName());
        unit.setType(backupClone.getType());
        formContext.setHasUnsavedModifications(false);
        initForms(true);
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
            actionUnitPanel.setIdunit(id);
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
        RecordingUnitInActionUnitLazyDataModel actionLazyDataModel = new RecordingUnitInActionUnitLazyDataModel(
                recordingUnitService,
                sessionSettingsBean,
                langBean,
                unit
        );

        totalRecordingUnitCount = recordingUnitService.countByActionContext(unit);
        RecordingUnitTreeTableLazyModel rLazyTree = new RecordingUnitTreeTableLazyModel(
                recordingUnitService, RecordingUnitScope.builder()
                .actionId(unit.getId())
                .type(RecordingUnitScope.Type.ACTION)
                .build()
        );

        recordingTabTableModel = new RecordingUnitTableViewModel(
                actionLazyDataModel,
                formService,
                sessionSettingsBean,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                flowBean,
                (GenericNewUnitDialogBean<RecordingUnitDTO>) genericNewUnitDialogBean,
                recordingUnitWriteVerifier,
                recordingUnitService,
                rLazyTree,
                langBean,
                formContextServices
        );

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

    public void saveSettings() {
        boolean containsNumRu = false;

        if (format == null || format.isEmpty()) {
            MessageUtils.displayErrorMessage(langBean, "actionUnit.settings.error.missingNumUe");
            return;
        }

        if (specifiedRuFullIdentifierFormatIsInvalid(containsNumRu)) return;

        unit.setMinRecordingUnitCode(minNumber);
        unit.setMaxRecordingUnitCode(maxNumber);
        unit.setRecordingUnitIdentifierFormat(format);

        unit.setRecordingUnitIdentifierLang(sessionSettingsBean.getLanguageCode());

        actionUnitService.save(unit);
        log.trace("Action unit saved with values : {} {} {}", unit.getMinRecordingUnitCode(), unit.getMaxRecordingUnitCode(), unit.getRecordingUnitIdentifierFormat());

        MessageUtils.displayInfoMessage(langBean, "actionUnit.settings.success.identifierConfigSaved");
    }

    private boolean specifiedRuFullIdentifierFormatIsInvalid(boolean containsNumRu) {
        String placeholderPattern = "\\{([^}]+)\\}";
        Pattern pattern = Pattern.compile(placeholderPattern);
        Matcher matcher = pattern.matcher(format);

        String strippedFormat = format.replaceAll(placeholderPattern, "");
        if (strippedFormat.contains("{") || strippedFormat.contains("}")) {
            MessageUtils.displayErrorMessage(langBean, INVALID_FORMAT_CODE);
            return true;
        }

        while (matcher.find()) {
            String placeholderContent = matcher.group(1);
            String[] parts = placeholderContent.split(":", 2);
            String placeholderName = parts[0];

            if (formatContainsInvalidCode(placeholderName)) return true;
            containsNumRu = containsNumRu || placeholderName.equals("NUM_UE");

            if (formatOfCodeIsNotValid(parts, placeholderName)) return true;
        }

        if (!containsNumRu) {
            MessageUtils.displayErrorMessage(langBean, "actionUnit.settings.error.missingNumUe");
            return true;
        }
        return false;
    }

    private boolean formatContainsInvalidCode(String placeholderName) {
        if (!recordingUnitService.findAllIdentifiersCode().contains(placeholderName)) {
            MessageUtils.displayErrorMessage(langBean, INVALID_FORMAT_CODE);
            return true;
        }
        return false;
    }

    private boolean formatOfCodeIsNotValid(String[] parts, String placeholderName) {
        if (parts.length <= 1) return false;
        String formatSpecifier = parts[1];
        return formatSpecifierIsNotValid(placeholderName, formatSpecifier) || oneNumericalFormatIsNotValid(placeholderName, formatSpecifier);
    }

    private boolean formatSpecifierIsNotValid(String placeholderName, String formatSpecifier) {
        if (recordingUnitService.findAllNumericalIdentifiersCode().contains(placeholderName) && !formatSpecifier.matches("0+")) {
            MessageUtils.displayWarnMessage(langBean, "actionUnit.settings.help.numericalFormat", placeholderName);
            return true;
        } else if (!formatSpecifier.matches("X+") || placeholderName.equals("ID_UA")) {
            MessageUtils.displayWarnMessage(langBean, "actionUnit.settings.help.textualFormat", placeholderName);
            return true;
        }
        return false;
    }

    private boolean oneNumericalFormatIsNotValid(String placeholderName, String formatSpecifier) {
        if (recordingUnitService.findAllNumericalIdentifiersCode().contains(placeholderName)) {
            long zeroCount = formatSpecifier.chars().filter(ch -> ch == '0').count();
            if (zeroCount > 0 && maxNumber != null && String.valueOf(maxNumber).length() > zeroCount) {
                MessageUtils.displayErrorMessage(langBean, "actionUnit.settings.error.insufficientDigits", placeholderName);
                return true;
            }
        }
        return false;
    }

    public List<RuIdentifierResolver> findAllResolvers() {
        Map<String, RuIdentifierResolver> resolvers = recordingUnitService.findAllIdentifierResolver();
        List<RuIdentifierResolver> result = new ArrayList<>();
        result.add(resolvers.get("NUM_UE"));
        result.add(resolvers.get("TYPE_UE"));
        result.add(resolvers.get("NUM_PARENT"));
        for (RuIdentifierResolver resolver : resolvers.values()) {
            if (!result.contains(resolver)) {
                result.add(resolver);
            }
        }
        return result;
    }

    @Override
    protected DefaultMenuItem createRootTypeItem()
    {
        return DefaultMenuItem.builder()
                .value(langBean.msg("panel.title.allactionunit"))
                .id("allActionUnits")
                .command("#{flowBean.addActionUnitListPanel()}")
                .update("flow")
                .onstart(PF_BUI_CONTENT_SHOW)
                .oncomplete(PF_BUI_CONTENT_HIDE)
                .process(THIS)
                .build();
    }

    @Override
    public String getPanelIndex() {
        return "action-unit-"+idunit;
    }

    @Override
    public String getPanelTypeClass() {
        return "spatial-unit";
    }


}