package fr.siamois.ui.bean.dialog.newunit;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.handler.INewUnitHandler;
import fr.siamois.ui.bean.field.SpatialUnitFieldBean;
import fr.siamois.ui.bean.panel.EntityForm;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity;
import fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException;
import fr.siamois.ui.form.EntityFormContext;
import fr.siamois.ui.form.FormContextServices;
import fr.siamois.ui.form.FormUiDto;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.utils.MessageUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.language.bm.Lang;
import org.primefaces.PrimeFaces;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Scope("session")
@Component
@Getter
@Setter
@RequiredArgsConstructor
public class GenericNewUnitDialogBean<T extends AbstractEntityDTO>
        implements EntityForm<T>, Serializable {

    private final transient FieldService fieldService;
    private final transient ConceptService conceptService;
    private final transient SpatialUnitFieldBean spatialUnitFieldBean;
    private final transient FlowBean flowBean;
    private final transient RedirectBean redirectBean;
    private final transient FormContextServices formContextServices;
    private final transient Map<UnitKind, INewUnitHandler<? extends AbstractEntityDTO>> handlers;
    private final transient SessionSettingsBean sessionSettingsBean;
    private final transient LangBean langBean;
    private final transient ConversionService conversionService;

    private T unit;
    private FormUiDto detailsForm;
    private EntityFormContext<T> formContext;

    protected static final String UPDATE_FAILED_MESSAGE_CODE = "common.entity.spatialUnits.updateFailed";
    protected static final String ENTITY_ALREADY_EXIST_MESSAGE_CODE = "common.entity.alreadyExist";

    // ==== handlers ====

    private UnitKind kind;
    private transient INewUnitHandler<T> handler;

    // creation  callback + contexte ====
    private transient fr.siamois.ui.table.EntityTableViewModel<T, ?> sourceTableModel;
    private transient NewUnitContext newUnitContext;


    // Unique selectKind
    @SuppressWarnings("unchecked")
    public void selectKind(NewUnitContext ctx,
                           fr.siamois.ui.table.EntityTableViewModel<T, ?> sourceTableModel)
            throws CannotInitializeNewUnitDialogException {

        this.kind = ctx.getKindToCreate();
        this.handler = (INewUnitHandler<T>) handlers.get(this.kind);

        this.sourceTableModel = sourceTableModel;
        this.newUnitContext = ctx;

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

    public Long getUnitId() {
        return unit != null ? unit.getId() : null;
    }

    /**
     * call this to initialize the EntityFormContext.
     */
    public void initFormContext(boolean forceInit) {
        if (unit == null) {
            log.warn("initFormContext called with null unit");
            return;
        }
        PanelFieldSource fieldSource = new PanelFieldSource(detailsForm);
        if (formContext == null || forceInit) {
            formContext = new EntityFormContext<>(
                    unit,
                    fieldSource,
                    formContextServices,
                    conversionService,
                    // callback appelé quand le champ de scope change
                    null,
                    // nom de la propriété qui porte le scope (ex: "type")
                    null
            );
        }
        formContext.setAutoSave(false); // IMPORTANT
        formContext.init(forceInit);
    }

    public void initForms(boolean forceInit) {
        detailsForm = formContextServices.getConversionService().convert(handler.formLayout(), FormUiDto.class);

        initFormContext(forceInit);
    }

    protected void reset() {
        unit = null;
        formContext = null;
    }

    public void init() throws CannotInitializeNewUnitDialogException {
        reset();
        unit = handler.newEmpty();
        unit.setCreatedBy(sessionSettingsBean.getAuthenticatedUser());
        unit.setCreatedByInstitution(sessionSettingsBean.getSelectedInstitution());
        handler.initFromContext(this);
        initForms(true);
    }

    public void create() {

        boolean isDifferentKind = newUnitContext.getTrigger().getType() == NewUnitContext.TriggerType.HOME_PANEL
                || (newUnitContext.getTrigger().getType() == NewUnitContext.TriggerType.CELL &&
                newUnitContext.getTrigger().getClickedKind() != newUnitContext.getKindToCreate());


        performCreate(isDifferentKind, isDifferentKind);

    }


    /**
     * Return the spatial unit options for spatial unit selection field
     *
     * @return The list of selectable spatial unit
     */
    public List<SpatialUnitSummaryDTO> getSpatialUnitOptions() {
        return handler.getSpatialUnitOptions(unit);
    }

    private void performCreate(boolean openAfter, boolean scrollToTop) {
        try {
            createUnit();
            // JS conditionnel (widgetVar fixe)
            String js = "PF('newUnitDiag').hide();";
            PrimeFaces.current().executeScript(js);

            // Display the new unit in the overview
            switch(kind) {
                case SPATIAL -> flowBean.addSpatialUnitToOverview(getUnitId(),sourceTableModel.getParentPanel());
                case RECORDING -> flowBean.addRecordingUnitToOverview(getUnitId(),sourceTableModel.getParentPanel());
                case ACTION -> flowBean.addActionUnitToOverview(getUnitId(),sourceTableModel.getParentPanel());
                case SPECIMEN -> flowBean.addSpecimenToOverview(getUnitId(),sourceTableModel.getParentPanel());
            }


        } catch (EntityAlreadyExistsException e) {
            log.error(e.getMessage(), e);
            MessageUtils.displayErrorMessage(langBean, ENTITY_ALREADY_EXIST_MESSAGE_CODE, unitName(), e.getField());

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            MessageUtils.displayErrorMessage(langBean, UPDATE_FAILED_MESSAGE_CODE, unitName());

        }
    }

    public boolean isColumnEnabled(CustomField field) {
        return formContext != null && formContext.isColumnEnabled(field);
    }

    /**
     * Expose "has unsaved modifications" via the context.
     */
    public boolean isHasUnsavedModifications() {
        return false;
    }

    /**
     * Expose the current CustomFormResponse via the context.
     */
    public CustomFormResponseViewModel getFormResponse() {
        return formContext != null ? formContext.getFormResponse() : null;
    }


    protected void createUnit() throws EntityAlreadyExistsException {
        formContext.flushBackToEntity();
        unit.setValidated(false);
        unit = handler.save(sessionSettingsBean.getUserInfo(), unit);

        // Post-create: laisse la table décider quoi faire (liste/tree) selon ctx
        if (sourceTableModel != null && newUnitContext != null) {
            sourceTableModel.onAnyEntityCreated(unit, newUnitContext);
        }
    }

    public String getConceptFieldsUpdateTargetsOnBlur() {
        return "";

    }

    public String getPanelHeaderUpdateId() {
        return "";

    }

    public String getAutocompleteClass() {
        return "";

    }

    /*
    Initializing the new entity dialog without context from home panel
     */
    public void openNewEntityDiag(UnitKind unitKind) {
        NewUnitContext ctx = NewUnitContext.builder()
                .kindToCreate(unitKind)
                .trigger(NewUnitContext.Trigger.homePanel())
                .build();
        try {
            selectKind(ctx, null);
        } catch (CannotInitializeNewUnitDialogException e) {
            MessageUtils.displayErrorMessage(langBean, "common.message.errorOpeningNewEntityDialog");
        }
    }

    public String getPanelIndex() {
        return "new-unit";
    }

}
