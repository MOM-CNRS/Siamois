package fr.siamois.ui.bean.dialog.newunit;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.dialog.newunit.handler.INewUnitHandler;
import fr.siamois.ui.bean.field.SpatialUnitFieldBean;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity;
import fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.component.UIComponent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.primefaces.event.SelectEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.faces.bean.ViewScoped;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@ViewScoped
@Component
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class GenericNewUnitDialogBean<T extends TraceableEntity>
        extends AbstractSingleEntity<T> implements Serializable {

    private final transient FieldService fieldService;
    private final transient ConceptService conceptService;
    private final SpatialUnitFieldBean spatialUnitFieldBean;
    // The sets to update after creation
    protected BaseLazyDataModel<T> lazyDataModel;
    protected transient Set<T> setToUpdate;
    // Context of creation (parent)
    protected TraceableEntity parent;
    // Multi hierarchy parent/children
    protected T multiHierarchyParent;
    protected T multiHierarchyChild;

    protected final LangBean langBean;
    protected final FlowBean flowBean;
    protected final RedirectBean redirectBean;

    protected static final String UPDATE_FAILED_MESSAGE_CODE = "common.entity.spatialUnits.updateFailed";
    protected static final String ENTITY_ALREADY_EXIST_MESSAGE_CODE = "common.entity.alreadyExist";

    // ==== handlers ====
    private transient Map<UnitKind, INewUnitHandler<? extends TraceableEntity>> handlers;
    private UnitKind kind;
    private transient INewUnitHandler<T> handler;


    public GenericNewUnitDialogBean(ApplicationContext context,
                                    Set<INewUnitHandler<? extends TraceableEntity>> handlerSet) {
        super(context);
        this.langBean = context.getBean(LangBean.class);
        this.flowBean = context.getBean(FlowBean.class);
        this.redirectBean = context.getBean(RedirectBean.class);
        this.handlers = handlerSet.stream()
                .collect(java.util.stream.Collectors.toMap(INewUnitHandler::kind, h -> h));
        this.fieldService = context.getBean(FieldService.class);
        this.conceptService = context.getBean(ConceptService.class);
        this.spatialUnitFieldBean = context.getBean(SpatialUnitFieldBean.class);
    }

    @SuppressWarnings("unchecked")
    public void selectKind(UnitKind kind) throws CannotInitializeNewUnitDialogException {
        this.kind = kind;
        this.handler = (INewUnitHandler<T>) handlers.get(kind);
        this.lazyDataModel = null;
        this.multiHierarchyChild = null;
        this.multiHierarchyParent = null;
        init();
    }

    @SuppressWarnings("unchecked")
    public void selectKind(UnitKind kind, BaseLazyDataModel<T> context) throws CannotInitializeNewUnitDialogException {
        this.kind = kind;
        this.handler = (INewUnitHandler<T>) handlers.get(kind);
        this.lazyDataModel = context;
        this.setToUpdate = null;
        this.parent = null;
        this.multiHierarchyChild = null;
        this.multiHierarchyParent = null;
        init();
    }

    public void selectKind(UnitKind kind, Set<T> context, TraceableEntity parent) throws CannotInitializeNewUnitDialogException {
        this.kind = kind;
        this.handler = (INewUnitHandler<T>) handlers.get(kind);
        this.lazyDataModel = null;
        this.setToUpdate = context;
        this.parent = parent;
        this.multiHierarchyChild = null;
        this.multiHierarchyParent = null;
        init();
    }

    public void selectKind(UnitKind kind, Set<T> context, T parent, T child) throws CannotInitializeNewUnitDialogException {
        this.kind = kind;
        this.handler = (INewUnitHandler<T>) handlers.get(kind);
        this.lazyDataModel = null;
        this.setToUpdate = context;
        this.parent = null;
        this.multiHierarchyChild = child;
        this.multiHierarchyParent = parent;
        init();
    }

    // ==== méthodes utilitaires (ex-abstracts supprimées) ====

    public String getDialogWidgetVar() {
        return handler != null ? handler.dialogWidgetVar() : "newUnitDialog";
    }

    public String getSuccessMessageCode() {
        return handler.successMessageCode();
    }

    public String unitName() {
        return unit != null ? handler.getName(unit) : " Unnamed unit";
    }

    @Override
    public String ressourceUri() {
        return handler != null ? handler.getResourceUri() : "generic-new-unit";
    }

    public Long getUnitId() {
        return unit != null ? unit.getId() : null;
    }

    // ==== logique ====
    @Override
    public void setFieldConceptAnswerHasBeenModified(SelectEvent<Concept> event) {
        UIComponent component = event.getComponent();
        CustomField field = (CustomField) component.getAttributes().get("field");
        formResponse.getAnswers().get(field).setHasBeenModified(true);
    }

    @Override
    public void initForms(boolean forceInit) {
        detailsForm = handler.formLayout();
        formResponse = initializeFormResponse(detailsForm, unit, forceInit);
    }

    protected void reset() {
        unit = null;
        formResponse = null;
    }

    public void init() throws CannotInitializeNewUnitDialogException {
        reset();
        unit = handler.newEmpty();
        unit.setCreatedBy(sessionSettingsBean.getAuthenticatedUser());
        unit.setCreatedByInstitution(sessionSettingsBean.getSelectedInstitution());
        handler.initFromContext(this);
        initForms(true);
    }

    public void createAndOpen() { performCreate(true, true); }
    public void create() { performCreate(false, false); }

    @Override
    public String display() {
        return "";
    }

    @Override
    public String getAutocompleteClass() {
        // Default implementation
        return handler.getAutocompleteClass();
    }

    /**
     * Return the spatial unit options for spatial unit selection field
     * @return The list of selectable spatial unit
     */
    @Override
    public List<SpatialUnit> getSpatialUnitOptions() {
        return handler.getSpatialUnitOptions(unit);
    }

    @Override
    protected String getFormScopePropertyName() {
        return "";
    }

    @Override
    protected void setFormScopePropertyValue(Concept concept) {
        // Empty because new unit form don't change based on type.
        // Need refactoring? Wrong parent class
    }

    private void performCreate(boolean openAfter, boolean scrollToTop) {
        try {
            createUnit();
            // JS conditionnel (widgetVar fixe)
            String js = "PF('newUnitDiag').hide();" + (scrollToTop ? "handleScrollToTop();" : "");
            PrimeFaces.current().executeScript(js);

            // Refresh commun
            PrimeFaces.current().ajax().update("flow");

            // Message succès
            MessageUtils.displayInfoMessage(langBean, getSuccessMessageCode(), unitName());

            // update des compteurs du home panel
            flowBean.updateHomePanel();

            if (openAfter) {
                redirectBean.redirectTo(handler.viewUrlFor(getUnitId()));
            }

        } catch (EntityAlreadyExistsException e) {
            log.error(e.getMessage(), e);
            MessageUtils.displayErrorMessage(langBean, ENTITY_ALREADY_EXIST_MESSAGE_CODE, unitName(), e.getField());

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            MessageUtils.displayErrorMessage(langBean, UPDATE_FAILED_MESSAGE_CODE, unitName());

        }
    }

    protected void createUnit() throws EntityAlreadyExistsException {
        updateJpaEntityFromFormResponse(formResponse, unit); // Update Unit based on form response
        unit.setValidated(false);
        unit = handler.save(sessionSettingsBean.getUserInfo(), unit);
        updateCollections();
    }

    private void updateCollections() {
        if (lazyDataModel != null) {
            lazyDataModel.addRowToModel(unit);
        }
        if (setToUpdate != null) {
            LinkedHashSet<T> newSet = new LinkedHashSet<>();
            newSet.add(unit);
            newSet.addAll(setToUpdate);
            setToUpdate.clear();
            setToUpdate.addAll(newSet);
        }
    }
}
