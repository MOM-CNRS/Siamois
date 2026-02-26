package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerText;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.bean.LabelBean;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.form.*;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.utils.DateUtils;
import jakarta.faces.event.ActionEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Base class for panels that display / edit a single entity using a CustomForm.
 * All form state & logic is delegated to EntityFormContext.
 */
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Data
@Slf4j
public abstract class AbstractSingleEntity<T extends AbstractEntityDTO> extends AbstractPanel implements Serializable {

    public static final String FIELD = "field";
    public static final String COLUMN_CLASS_NAME = "ui-g-12 ui-md-6 ui-lg-4";

    // -------------------- Dependencies --------------------

    protected final transient SessionSettingsBean sessionSettingsBean;
    protected final transient FieldConfigurationService fieldConfigurationService;
    protected final transient SpatialUnitTreeService spatialUnitTreeService;
    protected final transient SpatialUnitService spatialUnitService;
    protected final transient ActionUnitService actionUnitService;
    protected final transient DocumentService documentService;
    protected final transient LabelBean labelBean;
    protected final transient LangBean langBean;
    protected final transient FormService formService;
    protected final transient FormContextServices formContextServices;

    // -------------------- Local state ---------------------

    protected transient T unit;

    protected FormUiDto detailsForm;

    /**
     * Per-entity form context. Holds answers, enabled rules, spatial tree state, etc.
     */
    protected transient EntityFormContext<T> formContext;

    // -------------------- Vocabulary constants ------------

    public static final Vocabulary SYSTEM_THESO;
    public static final VocabularyType THESO_VOCABULARY_TYPE;

    static {
        THESO_VOCABULARY_TYPE = new VocabularyType();
        THESO_VOCABULARY_TYPE.setLabel("Thesaurus");
        SYSTEM_THESO = new Vocabulary();
        SYSTEM_THESO.setBaseUri("https://thesaurus.mom.fr");
        SYSTEM_THESO.setExternalVocabularyId("th230");
        SYSTEM_THESO.setType(THESO_VOCABULARY_TYPE);
    }

    // -------------------- Constructors --------------------

    protected AbstractSingleEntity(ApplicationContext context) {
        this.sessionSettingsBean = context.getBean(SessionSettingsBean.class);
        this.fieldConfigurationService = context.getBean(FieldConfigurationService.class);
        this.spatialUnitTreeService = context.getBean(SpatialUnitTreeService.class);
        this.spatialUnitService = context.getBean(SpatialUnitService.class);
        this.actionUnitService = context.getBean(ActionUnitService.class);
        this.documentService = context.getBean(DocumentService.class);
        this.labelBean = context.getBean(LabelBean.class);
        this.formService = context.getBean(FormService.class);
        this.formContextServices = context.getBean(FormContextServices.class);
        this.langBean = context.getBean(LangBean.class);
    }

    protected AbstractSingleEntity(String titleCodeOrTitle,
                                   String icon,
                                   String panelClass,
                                   ApplicationContext context) {
        super(titleCodeOrTitle, icon, panelClass);
        this.sessionSettingsBean = context.getBean(SessionSettingsBean.class);
        this.fieldConfigurationService = context.getBean(FieldConfigurationService.class);
        this.spatialUnitTreeService = context.getBean(SpatialUnitTreeService.class);
        this.spatialUnitService = context.getBean(SpatialUnitService.class);
        this.actionUnitService = context.getBean(ActionUnitService.class);
        this.documentService = context.getBean(DocumentService.class);
        this.labelBean = context.getBean(LabelBean.class);
        this.formService = context.getBean(FormService.class);
        this.formContextServices = context.getBean(FormContextServices.class);
        this.langBean = context.getBean(LangBean.class);
    }

    // -------------------- Utility -------------------------

    public static String generateRandomActionUnitIdentifier() {
        int currentYear = java.time.LocalDate.now().getYear();
        return String.valueOf(currentYear);
    }

    public String formatDate(OffsetDateTime offsetDateTime) {
        return DateUtils.formatOffsetDateTime(offsetDateTime);
    }

    public String getConceptFieldsUpdateTargetsOnBlur() {
        // If new unit panel form, update only header when concept is selected, otherwise @form
        if (this.getClass() == GenericNewUnitDialogBean.class) {
            return "";
        } else {
            return "@form panel-" + getPanelIndex() + "-header";
        }
    }

    public String getPanelHeaderUpdateId() {
        // If new unit panel form
        if (this.getClass() == GenericNewUnitDialogBean.class) {
            return "";
        } else {
            return "panel-" + getPanelIndex() + "-header singlePanelUnitForm-"+getPanelIndex()+":breadcrumbs";
        }
    }

    public String getAutocompleteClass() {
        return formContext != null ? formContext.getAutocompleteClass() : "";
    }

    // -------------------- Abstract methods ----------------

    /**
     * Initialize detailsForm, unit etc., then call initFormContext(forceInit).
     */
    public abstract void initForms(boolean forceInit);

    /**
     * Name of the property on the JPA entity that defines the "scope" of the form.
     * When it changes (via a system field), forms are re-initialized.
     */
    protected abstract String getFormScopePropertyName();

    protected abstract void setFormScopePropertyValue(ConceptDTO concept);

    /**
     * In list panels children may override this to provide options.
     */
    public List<SpatialUnit> getSpatialUnitOptions() {
        return List.of();
    }

    // -------------------- Form context helpers ------------

    /**
     * Helper for children: once unit + detailsForm are set,
     * call this to initialize the EntityFormContext.
     */
    protected void initFormContext(boolean forceInit) {
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
                    // callback appelé quand le champ de scope change
                    (field, concept) -> onFormScopeChanged(concept),
                    // nom de la propriété qui porte le scope (ex: "type")
                    getFormScopePropertyName()
            );
        }
        formContext.init(forceInit);
    }

    /**
     * Expose the current CustomFormResponse via the context.
     */
    public CustomFormResponse getFormResponse() {
        return formContext != null ? formContext.getFormResponse() : null;
    }

    /**
     * Expose "has unsaved modifications" via the context.
     */
    public boolean isHasUnsavedModifications() {
        return formContext != null && formContext.isHasUnsavedModifications();
    }

    public boolean isColumnEnabled(CustomField field) {
        return formContext != null && formContext.isColumnEnabled(field);
    }

    public CustomFieldAnswer getFieldAnswer(CustomField field) {
        return formContext != null ? formContext.getFieldAnswer(field) : null;
    }

    // -------------------- Auto-generation -----------------

    public boolean hasAutoGenerationFunction(CustomFieldText field) {
        return field != null && field.getAutoGenerationFunction() != null;
    }

    public void generateValueForField(ActionEvent event) {
        CustomFieldText field = (CustomFieldText) event.getComponent().getAttributes().get(FIELD);
        CustomFieldAnswerText answer = (CustomFieldAnswerText) event.getComponent().getAttributes().get("answer");
        if (field != null && field.getAutoGenerationFunction() != null && answer != null) {
            String generatedValue = field.generateAutoValue();
            answer.setValue(generatedValue);
            setFieldAnswerHasBeenModified(field);
        }
    }

    // -------------------- Field modification --------------

    public void setFieldAnswerHasBeenModified(CustomField field) {
        if (formContext != null) {
            formContext.markFieldModified(field);
        }
    }

    /**
     * Called when the "scope" system concept field changes.
     * Flushes current answers to the entity, updates the entity scope, then re-inits forms.
     */
    protected void onFormScopeChanged(ConceptDTO newVal) {
        if (formContext != null) {
            formContext.flushBackToEntity();
        }
        setFormScopePropertyValue(newVal); // change type of unit to be able to init forms
        initForms(false);
    }


    // -------------------- Convenience: system concept binding --------
    // If you still use populateSystemFieldValue for Concepts in lists etc.,
    // you can keep convenience methods here that rely on labelBean:

    protected ConceptAutocompleteDTO toAutocompleteDTO(ConceptDTO c) {
        if (c == null) return null;
        return new ConceptAutocompleteDTO(
                c,
                labelBean.findLabelOf(c),
                labelBean.getCurrentUserLang()
        );
    }

    protected void configureSystemFieldsBeforeInit() {
        // Default : nothing
    }

    protected List<CustomField> getAllFieldsFrom(FormUiDto... forms) {
        if (isEmpty(forms)) {
            return List.of();
        }

        Set<CustomField> fields = new LinkedHashSet<>();
        for (FormUiDto form : forms) {
            addFieldsFromForm(fields, form);
        }
        return new ArrayList<>(fields);
    }

    private boolean isEmpty(FormUiDto[] forms) {
        return forms == null || forms.length == 0;
    }

    private void addFieldsFromForm(Set<CustomField> fields, FormUiDto form) {
        if (form == null) {
            return;
        }
        addFieldsFromLayout(fields, form.getLayout());
    }

    private void addFieldsFromLayout(Set<CustomField> fields, List<CustomFormPanelUiDto> layout) {
        if (layout == null) {
            return;
        }
        for (CustomFormPanelUiDto panel : layout) {
            addFieldsFromPanel(fields, panel);
        }
    }

    private void addFieldsFromPanel(Set<CustomField> fields, CustomFormPanelUiDto panel) {
        if (panel == null) {
            return;
        }
        addFieldsFromRows(fields, panel.getRows());
    }

    private void addFieldsFromRows(Set<CustomField> fields, List<CustomRowUiDto> rows) {
        if (rows == null) {
            return;
        }
        for (CustomRowUiDto row : rows) {
            addFieldsFromRow(fields, row);
        }
    }

    private void addFieldsFromRow(Set<CustomField> fields, CustomRowUiDto row) {
        if (row == null) {
            return;
        }
        addFieldsFromColumns(fields, row.getColumns());
    }

    private void addFieldsFromColumns(Set<CustomField> fields, List<CustomColUiDto> columns) {
        if (columns == null) {
            return;
        }
        for (CustomColUiDto col : columns) {
            addFieldFromColumn(fields, col);
        }
    }

    private void addFieldFromColumn(Set<CustomField> fields, CustomColUiDto col) {
        if (col == null) {
            return;
        }
        CustomField field = col.getField();
        if (field != null) {
            fields.add(field);
        }
    }

    public String resolveTitleOrTitleCode() {
        try {
            return langBean.msg(titleCodeOrTitle);
        }
        catch(Exception e) {
            return titleCodeOrTitle;
        }
    }


}
