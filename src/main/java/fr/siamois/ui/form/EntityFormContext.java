package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.ui.form.rules.ColumnApplier;
import fr.siamois.ui.form.EnabledRulesEngine;
import fr.siamois.ui.form.rules.ValueProvider;
import fr.siamois.ui.viewmodel.TreeUiStateViewModel;
import lombok.Data;
import lombok.Getter;
import org.primefaces.model.TreeNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Per-entity "dynamic form" context.
 *
 * Holds:
 *  - CustomFormResponse (answers)
 *  - EnabledRulesEngine (column enable/disable)
 *  - hasUnsavedModifications flag
 *  - column enabled state map
 *  - spatial unit tree UI state
 *
 * This is UI-agnostic: no JSF events, only domain/UI-model state.
 */
@Data
public class EntityFormContext<T> {

    @Getter
    private final T unit;

    private final FieldSource fieldSource;
    private final FormService formService;
    private final SpatialUnitTreeService spatialUnitTreeService;
    private final SpatialUnitService spatialUnitService;

    @Getter
    private CustomFormResponse formResponse;

    @Getter
    private boolean hasUnsavedModifications = false;

    private EnabledRulesEngine enabledEngine;

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
                             FormService formService,
                             SpatialUnitTreeService spatialUnitTreeService,
                             SpatialUnitService spatialUnitService) {
        this.unit = unit;
        this.fieldSource = fieldSource;
        this.formService = formService;
        this.spatialUnitTreeService = spatialUnitTreeService;
        this.spatialUnitService = spatialUnitService;
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
}
