package fr.siamois.ui.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldStratigraphy;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerStratigraphy;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.form.rules.ColumnApplier;
import fr.siamois.ui.form.rules.ValueProvider;
import fr.siamois.ui.viewmodel.TreeUiStateViewModel;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIComponent;
import jakarta.faces.component.UIInput;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.AjaxBehaviorEvent;
import lombok.Data;
import lombok.Getter;
import org.primefaces.PrimeFaces;
import org.primefaces.event.SelectEvent;
import org.primefaces.model.TreeNode;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Per-entity "dynamic form" context.
 * <p>
 * Holds:
 * <ul>
 *  <li>CustomFormResponse (answers)</li>
 *  <li>EnabledRulesEngine (column enable/disable)</li>
 *  <li>hasUnsavedModifications flag</li>
 *  <li>column enabled state map</li>
 *  <li>spatial unit tree UI state</li>
 *  </ul>
 *  </p>
 */
@Data
public class EntityFormContext<T extends TraceableEntity> {

    @Getter
    private final T unit;

    private final FieldSource fieldSource;
    private final FormService formService;
    private final SpatialUnitTreeService spatialUnitTreeService;
    private final SpatialUnitService spatialUnitService;
    private final RecordingUnitService recordingUnitService;

    @Getter
    private CustomFormResponse formResponse;

    @Getter
    private boolean hasUnsavedModifications = false;

    private EnabledRulesEngine enabledEngine;

    private final BiConsumer<CustomField, Concept> formScopeChangeCallback;
    private final String formScopeValueBinding;

    // Column enabled state; if key missing, considered enabled
    private final Map<Long, Boolean> colEnabledByFieldId = new HashMap<>();

    // For multi-select spatial unit tree UI (per-answer state)
    private final Map<CustomFieldAnswerSelectMultipleSpatialUnitTree, TreeUiStateViewModel> treeStates =
            new HashMap<>();

    // Rules engine plumbing
    private final ValueProvider vp = this::getFieldAnswer;
    private final ColumnApplier applier = (colField, enabled) ->
            colEnabledByFieldId.put(colField.getId(), enabled);

    public EntityFormContext(T unit,
                             FieldSource fieldSource,
                             FormContextServices services,
                             BiConsumer<CustomField, Concept> formScopeChangeCallback,
                             String formScopeValueBinding) {
        this.unit = unit;
        this.fieldSource = fieldSource;
        this.formService = services.getFormService();
        this.spatialUnitTreeService = services.getSpatialUnitTreeService();
        this.spatialUnitService = services.getSpatialUnitService();
        this.recordingUnitService = services.getRecordingUnitService();
        this.formScopeChangeCallback = formScopeChangeCallback;
        this.formScopeValueBinding = formScopeValueBinding;
    }

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    /**
     * Initialize or refresh the form response & enabled rules.
     *
     * @param forceInit if true, discard previous answers and reinitialize everything
     */
    public void init(boolean forceInit) {
        this.formResponse = formService.initOrReuseResponse(
                this.formResponse,
                unit,
                fieldSource,
                forceInit
        );

        this.enabledEngine = formService.buildEnabledEngine(fieldSource);
        this.enabledEngine.applyAll(vp, applier);

        initSpatialUnitTreeStates();
    }

    // -------------------------------------------------------------------------
    // Column / answer helpers
    // -------------------------------------------------------------------------

    public CustomFieldAnswer getFieldAnswer(CustomField field) {
        if (formResponse == null || formResponse.getAnswers() == null) return null;
        return formResponse.getAnswers().get(field);
    }

    public boolean isColumnEnabled(CustomField field) {
        return colEnabledByFieldId.getOrDefault(field.getId(), true);
    }

    /**
     * Mark a field as modified and set global "hasUnsavedModifications".
     */
    public void markFieldModified(CustomField field) {
        CustomFieldAnswer answer = getFieldAnswer(field);
        if (answer != null) {
            answer.setHasBeenModified(true);
        }
        hasUnsavedModifications = true;
    }

    /**
     * Notify that a Concept answer changed on the given field – triggers enabled rules re-eval.
     */
    public void onConceptChanged(CustomField field, Concept newVal) {
        if (enabledEngine != null) {
            enabledEngine.onAnswerChange(field, newVal, vp, applier);
        }
    }

    /**
     * Flush current response values back into the underlying JPA entity.
     */
    public void flushBackToEntity() {
        formService.updateJpaEntityFromResponse(formResponse, unit);
    }

    // -------------------------------------------------------------------------
    // Spatial Unit Tree helpers
    // -------------------------------------------------------------------------

    private void initSpatialUnitTreeStates() {
        if (formResponse == null || formResponse.getAnswers() == null) return;

        for (CustomFieldAnswer a : formResponse.getAnswers().values()) {
            if (a instanceof CustomFieldAnswerSelectMultipleSpatialUnitTree treeAnswer) {
                treeStates.computeIfAbsent(treeAnswer, this::buildUiFor);
            }
        }
    }

    private TreeUiStateViewModel buildUiFor(CustomFieldAnswerSelectMultipleSpatialUnitTree answer) {
        TreeUiStateViewModel ui = new TreeUiStateViewModel();
        ui.setRoot(spatialUnitTreeService.buildTree());
        ui.setSelection(answer.getValue());
        return ui;
    }

    /**
     * Returns the root TreeNode for a given spatial-unit-tree answer.
     */
    public TreeNode<SpatialUnit> getRoot(CustomFieldAnswerSelectMultipleSpatialUnitTree answer) {
        TreeUiStateViewModel ui = treeStates.get(answer);
        return ui != null ? ui.getRoot() : null;
    }

    /**
     * Returns normalized selected spatial units (business-level "chips").
     */
    public List<SpatialUnit> getNormalizedSpatialUnits(CustomFieldAnswerSelectMultipleSpatialUnitTree answer) {
        TreeUiStateViewModel ui = treeStates.get(answer);
        if (ui == null) return Collections.emptyList();
        return getNormalizedSelectedUnits(ui.getSelection());
    }

    public void addSUToSelection(CustomFieldAnswerSelectMultipleSpatialUnitTree answer, SpatialUnit su) {
        TreeUiStateViewModel ui = treeStates.computeIfAbsent(answer, this::buildUiFor);
        if (ui.getSelection() == null) {
            ui.setSelection(new HashSet<>());
        }
        ui.getSelection().add(su);
        markTreeAnswerModified(answer);
    }

    public boolean removeSpatialUnit(CustomFieldAnswerSelectMultipleSpatialUnitTree answer, SpatialUnit su) {
        TreeUiStateViewModel ui = treeStates.get(answer);
        if (ui == null || ui.getSelection() == null) return false;
        boolean removed = ui.getSelection().remove(su);
        if (removed) {
            markTreeAnswerModified(answer);
        }
        return removed;
    }

    private void markTreeAnswerModified(CustomFieldAnswerSelectMultipleSpatialUnitTree answer) {
        answer.setHasBeenModified(true);
        hasUnsavedModifications = true;
    }

    /**
     * Normalize the selection for "chips" at business level (multi-parent graph).
     *
     * Keeps a minimal set where no selected node is a descendant of another selected node.
     */
    public List<SpatialUnit> getNormalizedSelectedUnits(Set<SpatialUnit> selectedNodes) {
        if (selectedNodes == null || selectedNodes.isEmpty()) return Collections.emptyList();

        Map<Long, SpatialUnit> byId = new HashMap<>();
        Set<Long> selectedIds = new LinkedHashSet<>();
        for (SpatialUnit u : selectedNodes) {
            if (u == null || u.getId() == null) continue;
            byId.putIfAbsent(u.getId(), u);
            selectedIds.add(u.getId());
        }

        Set<Long> toRemove = new HashSet<>();
        for (Long id : selectedIds) {
            if (toRemove.contains(id)) continue;
            Set<Long> ancestors = getAllAncestorIds(id);
            for (Long a : ancestors) {
                if (selectedIds.contains(a)) {
                    toRemove.add(id);
                    break;
                }
            }
        }

        selectedIds.removeAll(toRemove);

        List<SpatialUnit> chips = selectedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        chips.sort(Comparator.comparing(SpatialUnit::getName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return chips;
    }

    /**
     * Returns all ancestor IDs in the business graph (transitive), with cycle detection.
     */
    private Set<Long> getAllAncestorIds(long id) {
        Set<Long> res = new HashSet<>();

        Deque<Long> stack = spatialUnitService.findDirectParentsOf(id).stream()
                .map(SpatialUnit::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(ArrayDeque::new));

        while (!stack.isEmpty()) {
            long cur = stack.pop();
            if (res.add(cur)) {
                List<Long> parents = spatialUnitService.findDirectParentsOf(cur).stream()
                        .map(SpatialUnit::getId)
                        .filter(Objects::nonNull)
                        .toList();
                for (Long p : parents) {
                    if (!res.contains(p)) {
                        stack.push(p);
                    }
                }
            }
        }
        return res;
    }

    public void handleConceptChange(CustomField field, Concept newValue) {
        // 1) Marquer comme modifié
        markFieldModified(field);

        // 2) Appliquer la logique actuelle de changement de concept
        onConceptChanged(field, newValue);

        // 3) Si c'est le champ de scope de formulaire → callback
        if (isFormScopeField(field) && formScopeChangeCallback != null) {
            formScopeChangeCallback.accept(field, newValue);
        }
    }

    private boolean isFormScopeField(CustomField field) {
        return field != null
                && Boolean.TRUE.equals(field.getIsSystemField())
                && formScopeValueBinding != null
                && formScopeValueBinding.equals(field.getValueBinding());
    }

    public String getAutocompleteClass() {
        if (unit instanceof RecordingUnit) return "recording-unit-autocomplete";
        if (unit instanceof SpatialUnit)   return "spatial-unit-autocomplete";
        return "";
    }

    /**
     * Returns all the spatial units a recording unit can be attached to
     * @return The list of spatial unit
     */
    public List<SpatialUnit> getSpatialUnitOptions() {

        if (!(unit instanceof RecordingUnit ru)) {
            return Collections.emptyList();
        }

        return spatialUnitService.getSpatialUnitOptionsFor(ru);
    }

    public void setFieldAnswerHasBeenModified(CustomField field) {
        markFieldModified(field);

    }

    public void onFieldAnswerModifiedListener(AjaxBehaviorEvent event) {
        CustomField field = (CustomField) event.getComponent().getAttributes().get("field");
        setFieldAnswerHasBeenModified(field);
    }

    public void setFieldConceptAnswerHasBeenModified(SelectEvent<ConceptAutocompleteDTO> event) {
        UIComponent component = event.getComponent();
        CustomField field = (CustomField) component.getAttributes().get("field");
        Concept newValue = event.getObject().getConceptLabelToDisplay().getConcept();

        handleConceptChange(field, newValue);
    }

    /**
     * Get all recording units of the same scope (action unit) as the current unit.
     * @return The list of recording units
     */
    public List<RecordingUnit> getRecordingUnitOptions() {
        if (unit instanceof RecordingUnit recordingUnit) {
            return recordingUnitService.findAllByActionUnit(recordingUnit.getActionUnit());
        }
        return Collections.emptyList();
    }


    public void addStratigraphicRelationship(CustomFieldAnswerStratigraphy answer) {
        FacesContext context = FacesContext.getCurrentInstance();
        boolean isValid = true;

        UIComponent cc = UIComponent.getCurrentCompositeComponent(context);

        // Validate conceptToAdd
        if (answer.getConceptToAdd() == null) {
            UIInput c = (UIInput) cc.findComponent("relationshipVocab");
            c.setValid(false);
            isValid = false;
            FacesMessage msg = new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Ne peux pas être vide",
                    null
            );
            context.addMessage(c.getClientId(context), msg);
        }

        // Validate targetToAdd
        if (answer.getTargetToAdd() == null) {
            UIInput c = (UIInput) cc.findComponent("selectRU");
            c.setValid(false);
            isValid = false;
            FacesMessage msg = new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Ne peux pas être vide",
                    null
            );
            context.addMessage(c.getClientId(context), msg);
        }

        if (Objects.equals(answer.getTargetToAdd().getFullIdentifier(), answer.getSourceToAdd().getFullIdentifier())) {
            UIInput c = (UIInput) cc.findComponent("selectRU");
            c.setValid(false);
            isValid = false;
            FacesMessage msg = new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Les deux UE ne peuvent être identiques",
                    null
            );
            context.addMessage(c.getClientId(context), msg);
        }

        // Check if a relationship already exists between the two nodes
        if (isValid) {
            boolean relExists = false;
            String parentLabel = answer.getConceptToAdd().getHierarchyPrefLabels() == null ?
                    answer.getConceptToAdd().getOriginalPrefLabel() :
                    answer.getConceptToAdd().getHierarchyPrefLabels();

            // Check in synchronous relationships
            for (StratigraphicRelationship rel : answer.getSynchronousRelationships()) {
                if ((rel.getUnit1().equals(answer.getSourceToAdd()) && rel.getUnit2().equals(answer.getTargetToAdd())) ||
                        (rel.getUnit1().equals(answer.getTargetToAdd()) && rel.getUnit2().equals(answer.getSourceToAdd()))) {
                    relExists = true;
                    break;
                }
            }

            // Check in posterior relationships
            if (!relExists) {
                for (StratigraphicRelationship rel : answer.getPosteriorRelationships()) {
                    if (rel.getUnit1().equals(answer.getSourceToAdd()) && rel.getUnit2().equals(answer.getTargetToAdd())) {
                        relExists = true;
                        break;
                    }
                }
            }

            // Check in anterior relationships
            if (!relExists) {
                for (StratigraphicRelationship rel : answer.getAnteriorRelationships()) {
                    if (rel.getUnit1().equals(answer.getTargetToAdd()) && rel.getUnit2().equals(answer.getSourceToAdd())) {
                        relExists = true;
                        break;
                    }
                }
            }

            // If a relationship already exists, show an error message
            if (relExists) {
                UIInput c = (UIInput) cc.findComponent("selectRU");
                c.setValid(false);
                isValid = false;
                FacesMessage msg = new FacesMessage(
                        FacesMessage.SEVERITY_ERROR,
                        "Une relation existe déjà entre ces deux unités",
                        null
                );
                context.addMessage(c.getClientId(context), msg);
            }
        }

        // If validation fails, return early
        if (!isValid) {
            context.validationFailed();
            return;
        }

        // If validation passes, proceed with adding the relationship
        StratigraphicRelationship newRel = new StratigraphicRelationship();
        String parentLabel = answer.getConceptToAdd().getHierarchyPrefLabels() == null ?
                answer.getConceptToAdd().getOriginalPrefLabel() :
                answer.getConceptToAdd().getHierarchyPrefLabels();

        if (parentLabel.equalsIgnoreCase("synchrone avec")) {
            newRel.setUnit1(answer.getSourceToAdd());
            newRel.setUnit2(answer.getTargetToAdd());
            newRel.setConcept(answer.getConceptToAdd().concept());
            newRel.setIsAsynchronous(false);
            newRel.setUncertain(answer.getIsUncertainToAdd());
            newRel.setConceptDirection(answer.getVocabularyDirectionToAdd());
            answer.getSynchronousRelationships().add(newRel);
        } else if (parentLabel.equalsIgnoreCase("postérieur à")) {
            newRel.setUnit1(answer.getSourceToAdd());
            newRel.setUnit2(answer.getTargetToAdd());
            newRel.setConcept(answer.getConceptToAdd().concept());
            newRel.setIsAsynchronous(true);
            newRel.setUncertain(answer.getIsUncertainToAdd());
            newRel.setConceptDirection(answer.getVocabularyDirectionToAdd());
            answer.getPosteriorRelationships().add(newRel);
        } else if (parentLabel.equalsIgnoreCase("antérieur à")) {
            newRel.setUnit1(answer.getTargetToAdd());
            newRel.setUnit2(answer.getSourceToAdd());
            newRel.setConcept(answer.getConceptToAdd().concept());
            newRel.setIsAsynchronous(true);
            newRel.setUncertain(answer.getIsUncertainToAdd());
            newRel.setConceptDirection(!answer.getVocabularyDirectionToAdd());
            answer.getAnteriorRelationships().add(newRel);
        }

        // Optionally, reset the form fields
        answer.setConceptToAdd(null);
        answer.setTargetToAdd(null);
        answer.setIsUncertainToAdd(false);

        PrimeFaces.current().ajax().update(
                cc.getClientId().concat(":stratigraphyGraphContainer")
        );
    }


    public String getRelationshipsAsJson(CustomFieldAnswerStratigraphy answer) {

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        RecordingUnit centralUnit = answer.getSourceToAdd();

        // anterior
        ArrayNode anteriorArray = mapper.createArrayNode();
        for (StratigraphicRelationship rel : answer.getAnteriorRelationships()) {
            ObjectNode node = mapper.createObjectNode();
            node.put("unit1Id", rel.getUnit1().getFullIdentifier());
            node.put("vocabularyLabel", formService.getLabelBean().findLabelOf(rel.getConcept()));
            node.put("vocabularyDirection", rel.getConceptDirection());
            node.put("uncertain", rel.getUncertain() != null && rel.getUncertain());
            anteriorArray.add(node);
        }
        root.set("anterior", anteriorArray);

        // posterior
        ArrayNode posteriorArray = mapper.createArrayNode();
        for (StratigraphicRelationship rel : answer.getPosteriorRelationships()) {
            ObjectNode node = mapper.createObjectNode();
            node.put("unit1Id", rel.getUnit2().getFullIdentifier());
            node.put("vocabularyLabel", formService.getLabelBean().findLabelOf(rel.getConcept()));
            node.put("vocabularyDirection", !rel.getConceptDirection());
            node.put("uncertain", rel.getUncertain() != null && rel.getUncertain());
            posteriorArray.add(node);
        }
        root.set("posterior", posteriorArray);

        // synchronous
        ArrayNode synchronousArray = mapper.createArrayNode();

        for (StratigraphicRelationship rel : answer.getSynchronousRelationships()) {
            ObjectNode node = mapper.createObjectNode();

            RecordingUnit otherUnit;
            Boolean direction;

            if (rel.getUnit1().equals(centralUnit)) {
                otherUnit = rel.getUnit2();
                direction = !rel.getConceptDirection();
            } else if (rel.getUnit2().equals(centralUnit)) {
                otherUnit = rel.getUnit1();
                direction = rel.getConceptDirection();
            } else {
                // Safety net: malformed relationship, skip
                continue;
            }

            node.put("unit1Id", otherUnit.getFullIdentifier());
            node.put("vocabularyLabel",
                    formService.getLabelBean().findLabelOf(rel.getConcept()));
            node.put("vocabularyDirection", direction);
            node.put("uncertain", Boolean.TRUE.equals(rel.getUncertain()));

            synchronousArray.add(node);
        }

        root.set("synchronous", synchronousArray);

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            e.printStackTrace();
            return "{}";
        }
    }


}
