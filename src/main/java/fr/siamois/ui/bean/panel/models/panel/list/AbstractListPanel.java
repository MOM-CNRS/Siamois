package fr.siamois.ui.bean.panel.models.panel.list;

import fr.siamois.domain.services.BookmarkService;
import fr.siamois.domain.services.UiViewService;
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
    protected final transient UiViewService uiViewService;

    // local
    protected BaseLazyDataModel<T> lazyDataModel;
    protected long totalNumberOfUnits;
    protected transient TableViewState tableViewState = new TableViewState(); // init state
    protected Long viewId; // view defining the apparence the panel (table configuration mainly)

    @Override
    public boolean isBookmarked(

    ) {
        return bookmarkService.isRessourceBookmarkedByUser(sessionSettingsBean.getUserInfo(), buildBookmarkUrl());
    }

    @Override
    public void togglePanelBookmark() {
        if(Boolean.TRUE.equals(bookmarkService.isRessourceBookmarkedByUser(sessionSettingsBean.getUserInfo(), buildBookmarkUrl()))) {
            bookmarkService.delete(sessionSettingsBean.getUserInfo(), buildBookmarkUrl());
        }
        else {
            // ADD THE VIEW IF EXIST, DUPLICATE IF NOT YOUR OWN VIEW
            bookmarkService.save(sessionSettingsBean.getUserInfo(), buildBookmarkUrl(), titleCodeOrTitle);
        }
    }

    @Override
    public boolean isDirty() {

        TableViewState saved = tableViewState;
        if (saved == null) {
            return false;
        }

        TableViewState current = tableViewRuntimeMapper.extract(tableModel);

        if (current == null) {
            return false;
        }

        return !saved.normalize()
                .equals(current.normalize());
    }

    @Override
    public String buildBookmarkUrl() {
        return "/recording-unit"
                + "?viewId=" + viewId;
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
        uiViewService = null;
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
        this.uiViewService = applicationContext.getBean(UiViewService.class);
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

        totalNumberOfUnits = countUnitsByInstitution(); // todo : modify based on view??
        lazyDataModel = createLazyDataModel();
        configureLazyDataModel(lazyDataModel);

        configureTableColumns();

        // get view from id
        tableViewState = new TableViewState();
        // Try to fetch from db if set
        if(viewId != null) {
            tableViewState = uiViewService.getState(viewId);
        }
        else {
            // init from current
            tableViewState = tableViewRuntimeMapper.extract(tableModel);
        }
        // otherwise default
        applyViewState(tableViewState);

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
