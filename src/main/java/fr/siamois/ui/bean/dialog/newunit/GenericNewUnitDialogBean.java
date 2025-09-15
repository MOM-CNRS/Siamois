package fr.siamois.ui.bean.dialog.newunit;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.handler.INewUnitHandler;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.component.UIComponent;
import jakarta.faces.event.AjaxBehaviorEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.primefaces.model.LazyDataModel;
import org.springframework.stereotype.Component;

import javax.faces.bean.ViewScoped;
import java.io.Serializable;
import java.util.LinkedHashSet;
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

    // The sets to update after creation
    protected BaseLazyDataModel<T> lazyDataModel;
    protected transient Set<T> setToUpdate;

    protected final LangBean langBean;
    protected final FlowBean flowBean;
    protected final RedirectBean redirectBean;

    protected static final String UPDATE_FAILED_MESSAGE_CODE = "common.entity.spatialUnits.updateFailed";
    protected static final String ENTITY_ALREADY_EXIST_MESSAGE_CODE = "common.entity.alreadyExist";

    // ==== handlers ====
    private transient Map<UnitKind, INewUnitHandler<? extends TraceableEntity>> handlers;
    private UnitKind kind;
    private transient INewUnitHandler<T> handler;


    public GenericNewUnitDialogBean(AbstractSingleEntity.Deps deps,
                                    LangBean langBean,
                                    FlowBean flowBean,
                                    RedirectBean redirectBean,
                                    Set<INewUnitHandler<? extends TraceableEntity>> handlerSet) {
        super(deps);
        this.langBean = langBean;
        this.flowBean = flowBean;
        this.redirectBean = redirectBean;
        this.handlers = handlerSet.stream()
                .collect(java.util.stream.Collectors.toMap(INewUnitHandler::kind, h -> h));
    }

    @SuppressWarnings("unchecked")
    public void selectKind(UnitKind kind) {
        this.kind = kind;
        this.handler = (INewUnitHandler<T>) handlers.get(kind);
        init();
    }

    @SuppressWarnings("unchecked")
    public void selectKind(UnitKind kind, BaseLazyDataModel<T> context) {
        this.kind = kind;
        this.handler = (INewUnitHandler<T>) handlers.get(kind);
        init();
        this.lazyDataModel = context;
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
        return handler != null ? handler.getRessourceUri() : "generic-new-unit";
    }

    public Long getUnitId() {
        return unit != null ? unit.getId() : null;
    }

    // ==== logique ====
    @Override
    public void setFieldConceptAnswerHasBeenModified(AjaxBehaviorEvent event) {
        UIComponent component = event.getComponent();
        CustomField field = (CustomField) component.getAttributes().get("field");
        formResponse.getAnswers().get(field).setHasBeenModified(true);
    }

    @Override
    public void initForms() {
        detailsForm = handler.formLayout();
        formResponse = initializeFormResponse(detailsForm, unit);

    }

    protected void reset() {
        unit = null;
        formResponse = null;
        lazyDataModel = null;
        setToUpdate = null;
    }

    public void init() {
        reset();
        unit = handler.newEmpty();
        unit.setAuthor(sessionSettingsBean.getAuthenticatedUser());
        unit.setCreatedByInstitution(sessionSettingsBean.getSelectedInstitution());
        initForms();
        handler.onInitFromContext(this); // hook optionnel

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

    private void performCreate(boolean openAfter, boolean scrollToTop) {
        try {
            createUnit();
        } catch (EntityAlreadyExistsException e) {
            log.error(e.getMessage(), e);
            MessageUtils.displayErrorMessage(langBean, ENTITY_ALREADY_EXIST_MESSAGE_CODE, unitName());
            return;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            MessageUtils.displayErrorMessage(langBean, UPDATE_FAILED_MESSAGE_CODE, unitName());
            return;
        }

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
