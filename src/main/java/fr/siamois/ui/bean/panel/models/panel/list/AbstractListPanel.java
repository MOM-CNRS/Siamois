package fr.siamois.ui.bean.panel.models.panel.list;

import fr.siamois.domain.services.BookmarkService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.view.ColumnState;
import fr.siamois.dto.view.FilterState;
import fr.siamois.dto.view.SortState;
import fr.siamois.dto.view.TableViewState;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.table.TableViewRuntimeMapper;
import fr.siamois.ui.table.viewmodel.EntityTableViewModel;
import fr.siamois.ui.table.column.TableColumn;
import fr.siamois.utils.MessageUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.model.menu.DefaultMenuItem;
import org.springframework.context.ApplicationContext;

import java.io.Serializable;
import java.util.*;

@Getter
@Setter
@NoArgsConstructor(force = true)
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Slf4j
public abstract class AbstractListPanel<T extends AbstractEntityDTO> extends AbstractPanel  implements Serializable {

    // deps
    protected final transient SpatialUnitService spatialUnitService;
    protected final transient PersonService personService;
    protected final transient ConceptService conceptService;
    protected final transient SessionSettingsBean sessionSettingsBean;
    protected final transient LangBean langBean;
    protected final transient LabelService labelService;
    protected final transient ActionUnitService actionUnitService;
    protected final transient BookmarkService bookmarkService;
    protected final transient FieldService fieldService;
    protected final transient FieldConfigurationService fieldConfigurationService;
    protected final transient TableViewRuntimeMapper tableViewRuntimeMapper;

    // local
    protected BaseLazyDataModel<T> lazyDataModel;
    protected long totalNumberOfUnits;
    protected boolean dirty;

    @Override
    public String buildBookmarkUrl() {
        return "";
    }

    @Override
    public void applyViewState(TableViewState state) {
        if (state == null) {
            return;
        }

        tableViewRuntimeMapper.apply(
                tableModel,
                state
        );

        dirty = false;
    }


    /**
     * Modèle de vue pour la table :
     * - encapsule le LazyDataModel "pur data"
     * - expose les colonnes
     * - gère les EntityFormContext par ligne
     * - expose les opérations dont le panel a besoin (selectedUnits, rowData, addRow...)
     */
    protected transient EntityTableViewModel<T,Long> tableModel;

    protected AbstractListPanel(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
        conceptService = null;
        langBean = null;
        spatialUnitService = null;
        personService = null;
        labelService = null;
        actionUnitService = null;
        sessionSettingsBean = null;
        fieldService = null;
        fieldConfigurationService = null;
        this.tableViewRuntimeMapper = null;
    }

    public void refresh() {
        init();
    }


    protected AbstractListPanel(
            String titleKey,
            String icon,
            String cssClass,
            ApplicationContext applicationContext) {

        super(titleKey, icon, cssClass);

        this.spatialUnitService = applicationContext.getBean(SpatialUnitService.class);
        this.personService = applicationContext.getBean(PersonService.class);
        this.conceptService = applicationContext.getBean(ConceptService.class);
        this.sessionSettingsBean = applicationContext.getBean(SessionSettingsBean.class);
        this.langBean = applicationContext.getBean(LangBean.class);
        this.labelService = applicationContext.getBean(LabelService.class);
        this.actionUnitService = applicationContext.getBean(ActionUnitService.class);
        this.bookmarkService = applicationContext.getBean(BookmarkService.class);
        this.fieldService = applicationContext.getBean(FieldService.class);
        this.fieldConfigurationService = applicationContext.getBean(FieldConfigurationService.class);
        this.tableViewRuntimeMapper = applicationContext.getBean(TableViewRuntimeMapper.class);
    }

    protected abstract long countUnitsByInstitution();

    protected abstract BaseLazyDataModel<T> createLazyDataModel();

    protected void configureLazyDataModel(BaseLazyDataModel<T> model) {
        model.setSortBy(new HashSet<>());
        model.setFirst(0);
        model.setPageSizeState(5);
        model.setSelectedAuthors(new ArrayList<>());
        model.setSelectedTypes(new ArrayList<>());
        model.setNameFilter("");
        model.setGlobalFilter("");
    }

    public void bookmarkRow(String titleOrTitleCode, String ressourceUri) {

        // Maybe check that ressource exists and user has access to it?
        bookmarkService.save(
                sessionSettingsBean.getUserInfo(),
                ressourceUri,
                titleOrTitleCode
        );
        MessageUtils.displayInfoMessage(langBean, "common.bookmark.saved");
    }



    public void init() {

        DefaultMenuItem item = DefaultMenuItem.builder()
                .value(langBean.msg(getBreadcrumbKey()))
                .icon(getBreadcrumbIcon())
                .build();

        if (isBreadcrumbVisible()) {
            this.getBreadcrumb().getModel().getElements().add(item);
        }

        totalNumberOfUnits = countUnitsByInstitution();
        lazyDataModel = createLazyDataModel();
        configureLazyDataModel(lazyDataModel);

        configureTableColumns();

        TableViewState tableViewState = new TableViewState();

        tableViewState.setVersion(1);

        tableViewState.setColumnFilteringEnabled(true);

        tableViewState.setTreeMode(false);

// -------------------------
// COLUMNS
// -------------------------

        List<ColumnState> columns = new ArrayList<>();

        ColumnState identifierCol = new ColumnState();
        identifierCol.setColumnId("identifier");
        identifierCol.setVisible(true);

        ColumnState typeCol = new ColumnState();
        typeCol.setColumnId("type");
        typeCol.setVisible(true);

        ColumnState dateCol = new ColumnState();
        dateCol.setColumnId("openingDate");
        dateCol.setVisible(false);

        columns.add(identifierCol);
        columns.add(typeCol);
        columns.add(dateCol);

        tableViewState.setColumns(columns);

// -------------------------
// SORTING
// -------------------------

        List<SortState> sorting = new ArrayList<>();

        SortState sort = new SortState();
        sort.setColumnId("identifier");
        sort.setDirection(SortState.Direction.ASC);
        sort.setPriority(0);

        sorting.add(sort);

        tableViewState.setSorting(sorting);

// -------------------------
// FILTERS
// -------------------------

        Map<String, FilterState> filters = new HashMap<>();

        FilterState identifierFilter = new FilterState();
        identifierFilter.setColumnId("identifier");
        identifierFilter.setType(FilterState.FilterType.TEXT);
        identifierFilter.setValue("RU-2024");

        filters.put("identifier", identifierFilter);

        FilterState typeFilter = new FilterState();
        typeFilter.setColumnId("type");
        typeFilter.setType(FilterState.FilterType.CONCEPT);
        typeFilter.setValue(List.of(1,2));

        filters.put("type", typeFilter);

        tableViewState.setFilters(filters);

        applyViewState(tableViewState);

        // TODO : reload data??
    }

    protected abstract String getBreadcrumbKey();

    protected abstract String getBreadcrumbIcon();

    @Override
    public abstract String displayHeader();

    /**
     * Exposé pour la vue JSF, utilisé dans <p:columns value="#{panelModel.columns}">
     */
    public List<TableColumn> getColumns() {
        return tableModel != null ? tableModel.getColumns() : List.of();
    }

    abstract void configureTableColumns();

    public String resolveTitleOrTitleCode() {
        try {
            return langBean.msg(titleCodeOrTitle);
        }
        catch(Exception e) {
            return titleCodeOrTitle;
        }
    }

    public boolean hasPreviousNext() {
        return false;
    }
}
