package fr.siamois.ui.bean.panel.models.panel.list;

import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor(force = true)
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Slf4j
public abstract class AbstractListPanel<T> extends AbstractPanel  implements Serializable {

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
    protected Map<String, ConceptFieldConfig> fieldConfigs = new HashMap<>();
    protected Class<T> entityClass;

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

    /**
     * Prepare the configuration entity for the given field code.
     * This method must call the {@link fr.siamois.domain.services.vocabulary.ConceptService#saveAllSubConceptOfIfUpdated(ConceptFieldConfig)} after updating the configuration.
     * When the configuration is update, the {@link fr.siamois.domain.services.vocabulary.FieldConfigurationService} associated to the field code must be updated in the {@link #fieldConfigurations} map.
     * @param fieldCode the field code to prepare the configuration for
     */
    protected void prepareConfigForFieldCode(String fieldCode) throws NoConfigForFieldException {
        ConceptFieldConfig config = fieldConfigurationService.findConfigurationForFieldCode(sessionSettingsBean.getUserInfo(), fieldCode);
        try {
            conceptService.saveAllSubConceptOfIfUpdated(config);
            fieldConfigurations.put(fieldCode, config);
        } catch (ErrorProcessingExpansionException e) {
            log.error(e.getMessage(), e);
        }
    }

    public void onToggle(ColumnToggleEvent e) {
        Integer index = (Integer) e.getData();
        UIColumn column = e.getColumn();
        Visibility visibility = e.getVisibility();
        String header = column.getAriaHeaderText() != null ? column.getAriaHeaderText() : column.getHeaderText();
        FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_INFO, "Column " + index + " toggled: " + header + " " + visibility, null);
        FacesContext.getCurrentInstance().addMessage(null, msg);
    }

    protected AbstractListPanel(
            String titleKey,
            String icon,
            String cssClass,
            SpatialUnitService spatialUnitService,
            PersonService personService,
            ConceptService conceptService,
            SessionSettingsBean sessionSettingsBean,
            LangBean langBean,
            LabelService labelService,
            ActionUnitService actionUnitService,
            BookmarkService bookmarkService,
            FieldService fieldService,
            FieldConfigurationService fieldConfigurationService,
            Class<T> entityClass) {

        super(titleKey, icon, cssClass);

        this.spatialUnitService = spatialUnitService;
        this.personService = personService;
        this.conceptService = conceptService;
        this.sessionSettingsBean = sessionSettingsBean;
        this.langBean = langBean;
        this.labelService = labelService;
        this.actionUnitService = actionUnitService;
        this.bookmarkService = bookmarkService;
        this.fieldService = fieldService;
        this.fieldConfigurationService = fieldConfigurationService;
        this.entityClass = entityClass;
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

        for (String fieldCode : fieldService.findFieldCodesOf(entityClass)) {
            try {
                prepareConfigForFieldCode(fieldCode);
            } catch (NoConfigForFieldException e) {
                setErrorMessage(langBean.msg("panel.list.fieldconfig.error.missing", fieldCode));
            }
        }

    }

    protected abstract String getBreadcrumbKey();

    protected abstract String getBreadcrumbIcon();

    @Override
    public abstract String displayHeader();
}
