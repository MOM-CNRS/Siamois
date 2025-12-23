package fr.siamois.ui.bean.panel.models.panel.list;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.table.EntityTableViewModel;
import fr.siamois.ui.table.RecordingUnitTableViewModel;
import fr.siamois.ui.table.TableColumn;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.component.api.UIColumn;
import org.primefaces.event.ColumnToggleEvent;
import org.primefaces.model.Visibility;
import org.primefaces.model.menu.DefaultMenuItem;
import org.springframework.context.ApplicationContext;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor(force = true)
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Slf4j
public abstract class AbstractListPanel<T extends TraceableEntity> extends AbstractPanel  implements Serializable {

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

    // local
    protected BaseLazyDataModel<T> lazyDataModel;
    protected long totalNumberOfUnits;
    protected String errorMessage;

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



    protected abstract void setErrorMessage(String msg);


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
}
