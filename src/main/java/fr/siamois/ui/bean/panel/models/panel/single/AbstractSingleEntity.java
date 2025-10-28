package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.customfieldanswer.*;
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
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.viewmodel.TreeUiStateViewModel;
import fr.siamois.utils.DateUtils;
import jakarta.faces.component.UIComponent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.event.SelectEvent;
import org.primefaces.model.TreeNode;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Data
@Slf4j
public abstract class AbstractSingleEntity<T> extends AbstractPanel implements Serializable {

    // Deps
    protected final transient SessionSettingsBean sessionSettingsBean;
    protected final transient FieldConfigurationService fieldConfigurationService;
    protected final transient SpatialUnitTreeService spatialUnitTreeService;
    protected final transient SpatialUnitService spatialUnitService;
    protected final transient ActionUnitService actionUnitService;
    protected final transient DocumentService documentService;

    //--------------- Locals
    protected transient T unit;
    protected CustomFormResponse formResponse; // answers to all the fields from overview and details
    protected boolean hasUnsavedModifications = false; // Did we modify the unit?

    public CustomFieldAnswer getFieldAnswer(CustomField field) {
        return formResponse.getAnswers().get(field);
    }

    protected CustomForm detailsForm;
    protected CustomForm overviewForm;

    // For multi select tree UI
    private final Map<CustomFieldAnswerSelectMultipleSpatialUnitTree, TreeUiStateViewModel> treeStates = new HashMap<>();

    public static String generateRandomActionUnitIdentifier() {
        int currentYear = LocalDate.now().getYear();
        return String.valueOf(currentYear);
    }

    public String getConceptFieldsUpdateTargetsOnBlur() {
        // If new unit panel form, update @this when concept is selected, otherwise @form
        if(this.getClass() == GenericNewUnitDialogBean.class) {
            return "@this panelHeader";
        }
        else {
            return "@form panelHeader";
        }
    }

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


    private static final Map<Class<? extends CustomField>, Supplier<? extends CustomFieldAnswer>> ANSWER_CREATORS = Map.of(
            CustomFieldText.class, CustomFieldAnswerText::new,
            CustomFieldSelectOneFromFieldCode.class, CustomFieldAnswerSelectOneFromFieldCode::new,
            CustomFieldSelectOneConceptFromChildrenOfConcept.class, CustomFieldAnswerSelectOneConceptFromChildrenOfConcept::new,
            CustomFieldSelectMultiplePerson.class, CustomFieldAnswerSelectMultiplePerson::new,
            CustomFieldDateTime.class, CustomFieldAnswerDateTime::new,
            CustomFieldSelectOneActionUnit.class, CustomFieldAnswerSelectOneActionUnit::new,
            CustomFieldSelectOneSpatialUnit.class, CustomFieldAnswerSelectOneSpatialUnit::new,
            CustomFieldSelectMultipleSpatialUnitTree.class, CustomFieldAnswerSelectMultipleSpatialUnitTree::new,
            CustomFieldSelectOneActionCode.class, CustomFieldAnswerSelectOneActionCode::new
    );

    public boolean hasAutoGenerationFunction(CustomFieldText field) {
        return field != null && field.getAutoGenerationFunction() != null;
    }

    public void generateValueForField(CustomFieldText field, CustomFieldAnswerText answer) {
        if (field != null && field.getAutoGenerationFunction() != null) {
            String generatedValue = field.generateAutoValue();
            answer.setValue(generatedValue);
            setFieldAnswerHasBeenModified(field);
        }
    }

    public static final String COLUMN_CLASS_NAME = "ui-g-12 ui-md-6 ui-lg-4";

    protected AbstractSingleEntity(Deps deps) {
        this(null, null, null, deps);
    }


    public record Deps(SessionSettingsBean sessionSettingsBean, FieldConfigurationService fieldConfigurationService,
                       SpatialUnitTreeService spatialUnitTreeService, SpatialUnitService spatialUnitService,
                       ActionUnitService actionUnitService, DocumentService documentService) {
    }

    protected AbstractSingleEntity(String titleCodeOrTitle,
                                   String icon,
                                   String panelClass,
                                   Deps deps) {
        super(titleCodeOrTitle, icon, panelClass);
        this.sessionSettingsBean = deps.sessionSettingsBean;
        this.fieldConfigurationService = deps.fieldConfigurationService;
        this.spatialUnitTreeService = deps.spatialUnitTreeService;
        this.spatialUnitService = deps.spatialUnitService;
        this.actionUnitService = deps.actionUnitService;
        this.documentService = deps.documentService;
    }


    public String formatDate(OffsetDateTime offsetDateTime) {
        return DateUtils.formatOffsetDateTime(offsetDateTime);
    }


    public String getAutocompleteClass() {
        // Default implementation
        return "";
    }


    public abstract void initForms(boolean forceInit);


    public List<SpatialUnit> getSpatialUnitOptions() {
        // Implement in child classes if necessary
        return List.of();
    }

    // Nom de la propriété qui fait eventuellement changer le formulaire
    protected abstract String getFormScopePropertyName();
    protected abstract void setFormScopePropertyValue(Concept concept);

    protected void onFormScopeChanged(Concept newVal) {
        updateJpaEntityFromFormResponse(formResponse, unit);
        setFormScopePropertyValue(newVal); // change type of unit to be able to init forms
        initForms(false);
    }

    public void setFieldAnswerHasBeenModified(CustomField field) {
        formResponse.getAnswers().get(field).setHasBeenModified(true);
        hasUnsavedModifications = true;
    }

    public void setFieldConceptAnswerHasBeenModified(SelectEvent<Concept> event) {
        UIComponent component = event.getComponent();
        CustomField field = (CustomField) component.getAttributes().get("field");

        formResponse.getAnswers().get(field).setHasBeenModified(true);
        hasUnsavedModifications = true;
        if(Boolean.TRUE.equals(field.getIsSystemField()) && Objects.equals(field.getValueBinding(), getFormScopePropertyName())) {
            Concept newValue =  event.getObject(); // ← la nouvelle valeur
            onFormScopeChanged(newValue);
        }
    }


    public List<Concept> completeDependentConceptChildren(
            String input
    ) {
        return List.of();
    }

    public String getUrlForDependentConcept(
            CustomFieldSelectOneConceptFromChildrenOfConcept dependentField
    ) {
        if (dependentField == null || dependentField.getParentField() == null) {
            return null;
        }

        CustomField parentField = dependentField.getParentField();
        CustomFieldAnswer parentAnswer = formResponse.getAnswers().get(parentField);

        Concept parentConcept = null;

        if (parentAnswer instanceof CustomFieldAnswerSelectOneConceptFromChildrenOfConcept a1) {
            parentConcept = a1.getValue();
        } else if (parentAnswer instanceof CustomFieldAnswerSelectOneFromFieldCode a2) {
            parentConcept = a2.getValue();
        }

        return parentConcept != null ? fieldConfigurationService.getUrlOfConcept(parentConcept) : null;
    }

    public CustomFormResponse initializeFormResponse(CustomForm form, Object jpaEntity, boolean forceInit) {

        // Determine whether to reuse or recreate the response
        CustomFormResponse response;
        Map<CustomField, CustomFieldAnswer> answers;

        if (formResponse != null && !forceInit) {
            // Reuse existing form response
            response = formResponse;
            answers = formResponse.getAnswers();
        } else {
            // Create a new form response
            response = new CustomFormResponse();
            answers = new HashMap<>();
        }

        // If we’re reusing an existing form response, we only need to initialize missing fields
        boolean onlyInitMissingProperties = (response == formResponse && !forceInit);

        if (form.getLayout() == null) return response;

        List<String> bindableFields = getBindableFieldNames(jpaEntity);

        for (CustomFormPanel panel : form.getLayout()) {
            processPanel(panel, jpaEntity, bindableFields, answers, onlyInitMissingProperties);
        }

        response.setAnswers(answers);
        return response;
    }

    private void processPanel(CustomFormPanel panel, Object jpaEntity, List<String> bindableFields, Map<CustomField,
            CustomFieldAnswer> answers, boolean onlyInitMissingProperties) {
        if (panel.getRows() == null) return;

        for (CustomRow row : panel.getRows()) {
            if (row.getColumns() == null) continue;

            for (CustomCol col : row.getColumns()) {
                processColumn(col, jpaEntity, bindableFields, answers, onlyInitMissingProperties);
            }
        }
    }

    private void processColumn(CustomCol col, Object jpaEntity, List<String> bindableFields, Map<CustomField, CustomFieldAnswer> answers,
                               boolean onlyInitMissingProperties) {
        CustomField field = col.getField();
        if (field == null || (answers.containsKey(field) && onlyInitMissingProperties)) return;

        CustomFieldAnswer answer = instantiateAnswerForField(field);
        if (answer == null) return;

        initializeAnswer(answer, field);

        if (Boolean.TRUE.equals(field.getIsSystemField())
                && field.getValueBinding() != null
                && bindableFields.contains(field.getValueBinding())) {

            populateSystemFieldValue(answer, jpaEntity, field);
        }

        answers.put(field, answer);
    }

    private static void initializeAnswer(CustomFieldAnswer answer, CustomField field) {
        CustomFieldAnswerId answerId = new CustomFieldAnswerId();
        answerId.setField(field);
        answer.setPk(answerId);
        answer.setHasBeenModified(false);
    }

    // --------------------Spatial Unit Tree
    private TreeUiStateViewModel buildUiFor(CustomFieldAnswerSelectMultipleSpatialUnitTree answer) {
        TreeUiStateViewModel ui = new TreeUiStateViewModel();
        ui.setRoot(spatialUnitTreeService.buildTree());          // construit l’arbre metier
        ui.setSelection(answer.getValue());
        return ui;
    }

    // Returns the root for a given answer
    public TreeNode<SpatialUnit> getRoot(CustomFieldAnswerSelectMultipleSpatialUnitTree answer) {
        return treeStates.get(answer).getRoot();
    }

    public List<SpatialUnit> getNormalizedSpatialUnits(CustomFieldAnswerSelectMultipleSpatialUnitTree answer) {
        return getNormalizedSelectedUnits(treeStates.get(answer).getSelection());
    }

    public void addSUToSelection(CustomFieldAnswerSelectMultipleSpatialUnitTree answer, SpatialUnit su) {
        treeStates.get(answer).getSelection().add(su);
    }

    /**
     * Normalise la sélection pour les "chips" au niveau MÉTIER (graph multi-parents).
     */
    public List<SpatialUnit> getNormalizedSelectedUnits(Set<SpatialUnit> selectedNodes) {
        if (selectedNodes == null || selectedNodes.isEmpty()) return Collections.emptyList();

        // 1) Ramène à des IDs uniques d'entités
        Map<Long, SpatialUnit> byId = new HashMap<>();
        Set<Long> selectedIds = new LinkedHashSet<>();
        for (SpatialUnit u : selectedNodes) {
            if (u == null) continue;
            byId.putIfAbsent(u.getId(), u);
            selectedIds.add(u.getId());
        }

        // 2) Marque les entités "dominées" par un ancêtre sélectionné
        Set<Long> toRemove = new HashSet<>();
        for (Long id : selectedIds) {
            if (toRemove.contains(id)) continue;
            Set<Long> ancestors = getAllAncestorIds(id); // transitif, métier
            // si l'intersection ancestors ∩ selectedIds n'est pas vide -> enlever l'enfant
            for (Long a : ancestors) {
                if (selectedIds.contains(a)) {
                    toRemove.add(id);
                    break;
                }
            }
        }

        // 3) Garde seulement l’ensemble minimal
        selectedIds.removeAll(toRemove);

        // 4) Retourne la liste des entités pour afficher les chips
        List<SpatialUnit> chips = new ArrayList<>(selectedIds.size());
        for (Long id : selectedIds) chips.add(byId.get(id));
        // (optionnel) ordonner
        chips.sort(Comparator.comparing(SpatialUnit::getName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return chips;
    }

    /**
     * Renvoie tous les IDs des ancêtres métier (transitifs), avec détection de cycles.
     */
    private Set<Long> getAllAncestorIds(long id) {
        Set<Long> res = new HashSet<>();
        Deque<Long> stack = spatialUnitService.findDirectParentsOf(id).stream()
                .map(SpatialUnit::getId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toCollection(ArrayDeque::new));
        while (!stack.isEmpty()) {
            long cur = stack.pop();
            if (res.add(cur)) {
                List<Long> parents = spatialUnitService.findDirectParentsOf(id).stream()
                        .map(SpatialUnit::getId)
                        .filter(Objects::nonNull)
                        .toList();
                for (Long p : parents) {
                    if (!res.contains(p)) stack.push(p);
                }
            }
        }
        return res;
    }

    // Remove a spatial unit from the selection
    public boolean removeSpatialUnit(CustomFieldAnswerSelectMultipleSpatialUnitTree answer, SpatialUnit su) {

        treeStates.get(answer).getSelection().remove(su);

        return true;
    }


    // ------------- End spatial unit tree

    private void populateSystemFieldValue(CustomFieldAnswer answer, Object jpaEntity, CustomField field) {
        Object value = getFieldValue(jpaEntity, field.getValueBinding());

        if (value instanceof OffsetDateTime odt && answer instanceof CustomFieldAnswerDateTime dateTimeAnswer) {
            dateTimeAnswer.setValue(odt.toLocalDateTime());
        } else if (value instanceof String str && answer instanceof CustomFieldAnswerText textAnswer) {
            textAnswer.setValue(str);
        } else if (value instanceof List<?> list && answer instanceof CustomFieldAnswerSelectMultiplePerson multiplePersonAnswer &&
                list.stream().allMatch(Person.class::isInstance)) {
            multiplePersonAnswer.setValue((List<Person>) list);
        } else if (value instanceof Concept c) {
            if (answer instanceof CustomFieldAnswerSelectOneFromFieldCode codeAnswer) {
                codeAnswer.setValue(c);
            } else if (answer instanceof CustomFieldAnswerSelectOneConceptFromChildrenOfConcept childAnswer) {
                childAnswer.setValue(c);
            }
        } else if (value instanceof ActionUnit a && answer instanceof CustomFieldAnswerSelectOneActionUnit actionUnitAnswer) {
            actionUnitAnswer.setValue(a);
        } else if (value instanceof SpatialUnit s && answer instanceof CustomFieldAnswerSelectOneSpatialUnit spatialUnitAnswer) {
            spatialUnitAnswer.setValue(s);
        } else if (value instanceof ActionCode code && answer instanceof CustomFieldAnswerSelectOneActionCode actionCodeAnswer) {
            actionCodeAnswer.setValue(code);
        } else if (value instanceof Set<?> set && answer instanceof CustomFieldAnswerSelectMultipleSpatialUnitTree treeAnswer) {
            // Cast set to the expected type
            treeAnswer.setValue((Set<SpatialUnit>) set);
            TreeUiStateViewModel ui = buildUiFor(treeAnswer);
            treeStates.put(treeAnswer, ui);
        }
    }


    public static void updateJpaEntityFromFormResponse(CustomFormResponse response, Object jpaEntity) {
        if (response == null || jpaEntity == null) return;

        List<String> bindableFields = getBindableFieldNames(jpaEntity);

        for (Map.Entry<CustomField, CustomFieldAnswer> entry : response.getAnswers().entrySet()) {
            CustomField field = entry.getKey();
            CustomFieldAnswer answer = entry.getValue();

            if (!isBindableSystemField(field, answer, bindableFields)) continue;

            Object value = extractValueFromAnswer(answer);
            if (value != null) {
                setFieldValue(jpaEntity, field.getValueBinding(), value);
            }
        }
    }

    private static boolean isBindableSystemField(CustomField field, CustomFieldAnswer answer, List<String> bindableFields) {
        return field != null
                && answer != null
                && Boolean.TRUE.equals(field.getIsSystemField())
                && field.getValueBinding() != null
                && bindableFields.contains(field.getValueBinding());
    }

    private static Object extractValueFromAnswer(CustomFieldAnswer answer) {
        if (answer instanceof CustomFieldAnswerDateTime a && a.getValue() != null) {
            return a.getValue().atOffset(ZoneOffset.UTC);
        } else if (answer instanceof CustomFieldAnswerText a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectMultiplePerson a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectOneFromFieldCode a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectOneConceptFromChildrenOfConcept a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectOneActionUnit a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectOneSpatialUnit a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectMultipleSpatialUnitTree a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectOneActionCode a) {
            return a.getValue();
        }


        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getBindableFieldNames(Object entity) {
        try {
            Method method = entity.getClass().getMethod("getBindableFieldNames");
            return (List<String>) method.invoke(entity);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static Object getFieldValue(Object obj, String fieldName) {
        try {
            PropertyDescriptor pd = new PropertyDescriptor(fieldName, obj.getClass());
            return pd.getReadMethod().invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static void setFieldValue(Object obj, String fieldName, Object value) {
        try {
            PropertyDescriptor pd = new PropertyDescriptor(fieldName, obj.getClass());
            Method setter = pd.getWriteMethod();
            setter.invoke(obj, value);
        } catch (Exception e) {
            // Ignored, the value won't be set
        }
    }

    private static CustomFieldAnswer instantiateAnswerForField(CustomField field) {
        Supplier<? extends CustomFieldAnswer> creator = ANSWER_CREATORS.get(field.getClass());
        if (creator != null) {
            return creator.get();
        }
        throw new IllegalArgumentException("Unsupported CustomField type: " + field.getClass().getName());
    }


}
