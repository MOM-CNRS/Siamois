package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.services.form.CustomFieldService;
import fr.siamois.domain.services.history.HistoryAuditService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.document.DocumentCreationBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.bean.panel.models.panel.single.tab.ActionTab;
import fr.siamois.ui.bean.panel.models.panel.single.tab.RecordingTab;
import fr.siamois.ui.bean.panel.models.panel.single.tab.SpecimenTab;
import fr.siamois.ui.bean.panel.utils.SpatialUnitHelperService;
import fr.siamois.ui.lazydatamodel.*;
import fr.siamois.utils.MessageUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import software.xdev.chartjs.model.charts.BarChart;
import software.xdev.chartjs.model.color.RGBAColor;
import software.xdev.chartjs.model.data.BarData;
import software.xdev.chartjs.model.dataset.BarDataset;
import software.xdev.chartjs.model.options.BarOptions;
import software.xdev.chartjs.model.options.Plugins;
import software.xdev.chartjs.model.options.Title;
import software.xdev.chartjs.model.options.Tooltip;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
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
public class SpatialUnitPanel extends AbstractSingleMultiHierarchicalEntityPanel<SpatialUnit> implements Serializable {

    // Dependencies
    private final transient RecordingUnitService recordingUnitService;
    private final transient SpecimenService specimenService;
    private final transient SessionSettingsBean sessionSettings;
    private final transient SpatialUnitHelperService spatialUnitHelperService;
    private final transient CustomFieldService customFieldService;
    private final transient ConceptService conceptService;
    private final transient LabelService labelService;
    private final transient LangBean langBean;
    private final transient PersonService personService;

    private String spatialUnitErrorMessage;
    private transient List<SpatialUnit> spatialUnitList;
    private transient List<SpatialUnit> spatialUnitParentsList;
    private String spatialUnitListErrorMessage;
    private String spatialUnitParentsListErrorMessage;

    // lazy model for children
    private SpatialUnitChildrenLazyDataModel lazyDataModelChildren ;
    // lazy model for parents
    private SpatialUnitParentsLazyDataModel lazyDataModelParents ;
    // Lazy model for actions in the spatial unit
    private ActionUnitInSpatialUnitLazyDataModel actionLazyDataModel;
    private Integer totalActionUnitCount;
    // Lazy model for recording unit in the spatial unit
    private RecordingUnitInSpatialUnitLazyDataModel recordingLazyDataModel;
    private Integer totalRecordingUnitCount;
    // Lazy model for recording unit in the spatial unit
    private SpecimenInSpatialUnitLazyDataModel specimenLazyDataModel;
    private Integer totalSpecimenCount;


    private String barModel;


    @Autowired
    private SpatialUnitPanel(RecordingUnitService recordingUnitService,
                             SessionSettingsBean sessionSettings,
                             SpatialUnitHelperService spatialUnitHelperService,
                             DocumentCreationBean documentCreationBean, CustomFieldService customFieldService,
                             ConceptService conceptService,
                             LabelService labelService, LangBean langBean, PersonService personService,
                             AbstractSingleEntity.Deps deps, SpecimenService specimenService, HistoryAuditService historyAuditService) {

        super("common.entity.spatialUnit", "bi bi-geo-alt", "siamois-panel spatial-unit-panel single-panel",
                documentCreationBean, deps, historyAuditService);
        this.recordingUnitService = recordingUnitService;
        this.sessionSettings = sessionSettings;
        this.spatialUnitHelperService = spatialUnitHelperService;
        this.customFieldService = customFieldService;
        this.labelService = labelService;
        this.conceptService = conceptService;
        this.langBean = langBean;
        this.personService = personService;
        this.specimenService = specimenService;
    }


    public List<LocalizedConceptData> categoriesAvailable() {
        List<Concept> cList = conceptService.findAllBySpatialUnitOfInstitution(sessionSettings.getSelectedInstitution());
        return cList.stream()
                .map(concept -> labelService.findLabelOf(
                        concept, langBean.getLanguageCode()
                ))
                .toList();

    }

    @Override
    public String getAutocompleteClass() {
        return "spatial-unit-autocomplete";
    }


    @Override
    public String ressourceUri() {
        return "/spatial-unit/" + idunit;
    }

    @Override
    public String displayHeader() {
        return "/panel/header/spatialUnitPanelHeader.xhtml";
    }



    public void createBarModel() {
        barModel = new BarChart()
                .setData(new BarData()
                        .addDataset(new BarDataset()
                                .setData(65, 59, 80)
                                .setBackgroundColor(List.of(new RGBAColor(255, 99, 132, 0.5),new RGBAColor(12, 99, 132, 0.5),new RGBAColor(255, 17, 51, 0.5)))
                                .setBorderColor(new RGBAColor(255, 99, 132,1))
                                .setBorderWidth(1))
                        .setLabels("Hors contexte", "Unité stratigraphique", "Unité construite"))
                .setOptions(new BarOptions()
                        .setResponsive(true)
                        .setMaintainAspectRatio(false)
                        .setPlugins(new Plugins()
                                .setTooltip(new Tooltip().setMode("index"))
                                .setTitle(new Title()
                                        .setDisplay(true)
                                        .setText("Unités d'enregistrement (mockup)")
                                )
                        )
                ).toJson();
    }

    @Override
    public List<Person> authorsAvailable() {

        return personService.findAllAuthorsOfSpatialUnitByInstitution(sessionSettings.getSelectedInstitution());

    }

    @Override
    public void initForms(boolean forceInit) {

        overviewForm = SpatialUnit.OVERVIEW_FORM;
        detailsForm = SpatialUnit.DETAILS_FORM;
        // Init system form answers
        formResponse = initializeFormResponse(detailsForm, unit, forceInit);
    }

    @Override
    protected String getFormScopePropertyName() {
        return "";
    }

    @Override
    protected void setFormScopePropertyValue(Concept concept) {
        unit.setCategory(concept);
    }

    public void refreshUnit() {

        hasUnsavedModifications = false;
        unit = null;
        spatialUnitHelperService.reinitialize(
                unit -> this.unit = unit,
                msg -> this.spatialUnitErrorMessage = msg,
                msg -> this.spatialUnitListErrorMessage = msg,
                list -> this.spatialUnitList = list,
                list -> this.spatialUnitParentsList = list,
                msg -> this.spatialUnitParentsListErrorMessage = msg
        );

        try {

            this.unit = spatialUnitService.findById(idunit);
            this.setTitleCodeOrTitle(unit.getName()); // Set panel title

            backupClone = new SpatialUnit(unit);

            initForms(true);

            // Get all the CHILDREN of the spatial unit
            selectedCategoriesChildren = new ArrayList<>();
            lazyDataModelChildren= new SpatialUnitChildrenLazyDataModel(
                    spatialUnitService,
                    langBean,
                    unit
            );
            totalChildrenCount = spatialUnitService.countChildrenByParent(unit);

            // Get all the Parents of the spatial unit
            selectedCategoriesParents = new ArrayList<>();
            lazyDataModelParents = new SpatialUnitParentsLazyDataModel(
                    spatialUnitService,
                    langBean,
                    unit
            );
            totalParentsCount = spatialUnitService.countParentsByChild(unit);

            // Action in spatial unit lazy model
            actionLazyDataModel = new ActionUnitInSpatialUnitLazyDataModel(
                    actionUnitService,
                    sessionSettings,
                    langBean,
                    unit
            );
            totalActionUnitCount = actionUnitService.countBySpatialContext(unit);

            // recording in spatial unit lazy model
            recordingLazyDataModel = new RecordingUnitInSpatialUnitLazyDataModel(
                    recordingUnitService,
                    langBean,
                    unit
            );
            totalRecordingUnitCount = recordingUnitService.countBySpatialContext(unit);

            // specimen in spatial unit lazy model
            specimenLazyDataModel = new SpecimenInSpatialUnitLazyDataModel(
                    specimenService,
                    langBean,
                    unit
            );
            totalSpecimenCount = specimenService.countBySpatialContext(unit);

        } catch (RuntimeException e) {
            this.spatialUnitErrorMessage = "Failed to load spatial unit: " + e.getMessage();
        }


        history = historyAuditService.findAllRevisionForEntity(SpatialUnit.class, idunit);
        documents = documentService.findForSpatialUnit(unit);
    }

    @Override
    public void cancelChanges() {
        unit.setGeom(backupClone.getGeom());
        unit.setName(backupClone.getName());
        unit.setValidated(backupClone.getValidated());
        unit.setArk(backupClone.getArk());
        unit.setCategory(backupClone.getCategory());
        hasUnsavedModifications = false;
        initForms(true);
    }

    @Override
    public void init() {

        createBarModel();

        if (idunit == null) {
            this.spatialUnitErrorMessage = "The ID of the spatial unit must be defined";
            return;
        }

        refreshUnit();

        super.init();

        ActionTab actionTab = new ActionTab(
                "common.entity.actionUnits",
                "bi bi-arrow-down-square",
                "actionTab",

                actionLazyDataModel,
                totalActionUnitCount);

        tabs.add(actionTab);

        RecordingTab recordingTab = new RecordingTab(
                "common.entity.recordingUnits",
                "bi bi-pencil-square",
                "recordingTab",

                recordingLazyDataModel,
                totalRecordingUnitCount);

        tabs.add(recordingTab);

        SpecimenTab specimenTab = new SpecimenTab(
                "common.entity.specimens",
                "bi bi-bucket",
                "specimenTab",

                specimenLazyDataModel,
                totalSpecimenCount);

        tabs.add(specimenTab);

    }

    @Override
    public void visualise(RevisionWithInfo<SpatialUnit> history) {
        // spatialUnitHelperService.visualise(history, hist -> this.revisionToDisplay = hist);
    }

    public void restore(RevisionWithInfo<SpatialUnit> history) {
        spatialUnitHelperService.restore(history);
        init();
        MessageUtils.displayInfoMessage(langBean, "common.entity.spatialUnits.updated", history.getDate().toString());
    }



    public String getFormattedValue(Object value) {
        if (value == null) {
            return "";
        }

        if (value instanceof Number) {
            // Integer or Number case
            return value.toString();
        } else if (value instanceof List<?> list) {
            // Handle list of concepts
            String langCode = sessionSettings.getLanguageCode();
            return list.stream()
                    .map(item -> (item instanceof Concept concept) ? labelService.findLabelOf(concept, langCode).getLabel() : item.toString())
                    .collect(Collectors.joining(", "));
        }

        return value.toString(); // Default case
    }

    @Override
    protected boolean documentExistsInUnitByHash(SpatialUnit unit, String hash) {
        return documentService.existInSpatialUnitByHash(unit, hash);
    }

    @Override
    protected void addDocumentToUnit(Document doc, SpatialUnit unit) {
        documentService.addToSpatialUnit(doc, unit);
    }




    @Override
    public boolean save(Boolean validated) {

        // Recupération des champs systeme

        // Name
        updateJpaEntityFromFormResponse(formResponse, unit);

        unit.setValidated(validated);
        try {
            spatialUnitService.save(unit);
        }
        catch(FailedRecordingUnitSaveException e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.spatialUnits.updateFailed", unit.getName());
            return false;
        }

        refreshUnit();
        MessageUtils.displayInfoMessage(langBean, "common.entity.spatialUnits.updated", unit.getName());
        return true;
    }

    public static class SpatialUnitPanelBuilder {

        private final SpatialUnitPanel spatialUnitPanel;

        public SpatialUnitPanelBuilder(ObjectProvider<SpatialUnitPanel> spatialUnitPanelProvider) {
            this.spatialUnitPanel = spatialUnitPanelProvider.getObject();
        }

        public SpatialUnitPanelBuilder id(Long id) {
            spatialUnitPanel.setIdunit(id);
            return this;
        }

        public SpatialUnitPanelBuilder activeIndex(Integer id) {
            spatialUnitPanel.setActiveTabIndex(id);
            return this;
        }

        public SpatialUnitPanelBuilder breadcrumb(PanelBreadcrumb breadcrumb) {
            spatialUnitPanel.setBreadcrumb(breadcrumb);

            return this;
        }

        public SpatialUnitPanel build() {
            spatialUnitPanel.init();
            return spatialUnitPanel;
        }
    }

}