package fr.siamois.ui.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.GeoApiService;
import fr.siamois.domain.services.GeoPlatService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.rules.ColumnApplier;
import fr.siamois.ui.form.rules.EnabledRulesEngine;
import fr.siamois.ui.form.rules.ValueProvider;
import fr.siamois.ui.form.savestrategy.ActionUnitSaveStrategy;
import fr.siamois.ui.form.savestrategy.RecordingUnitSaveStrategy;
import fr.siamois.ui.form.savestrategy.SpatialUnitSaveStrategy;
import fr.siamois.ui.form.savestrategy.SpecimenSaveStrategy;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.TreeUiStateViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.*;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIComponent;
import jakarta.faces.component.UIInput;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.AjaxBehaviorEvent;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.primefaces.PrimeFaces;
import org.primefaces.event.SelectEvent;
import org.primefaces.model.TreeNode;
import org.springframework.core.convert.ConversionService;

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
@RequiredArgsConstructor
public class EntityFormContext<T extends AbstractEntityDTO> {

    public static final String UNIT_1_ID = "unit1Id";
    public static final String VOCABULARY_DIRECTION = "vocabularyDirection";
    public static final String UNCERTAIN = "uncertain";
    public static final String VOCABULARY_LABEL = "vocabularyLabel";
    public static final String SELECT_RU = "selectRU";
    public static final String DATABASE_ID = "databaseId";
    public static final String FIELD = "field";

    private T unit;

    private boolean autoSave = true;

    private final FieldSource fieldSource;
    private final FormService formService;
    private final SpatialUnitTreeService spatialUnitTreeService;
    private final SpatialUnitService spatialUnitService;
    private final SpecimenService specimenService;
    private final RecordingUnitService recordingUnitService;
    private final ActionUnitService actionUnitService;
    private final LangBean langBean;
    private final ConversionService conversionService;
    private final SessionSettingsBean sessionSettingsBean;
    private final GeoPlatService geoPlatService;
    private final GeoApiService geoApiService;
    private final ConceptService conceptService;
    private final ConceptMapper conceptMapper;
    private final FormContextServices services;


    private List<SpatialUnitSummaryDTO> options; // spatial unit options


    private CustomFormResponseViewModel formResponse;

    private boolean hasUnsavedModifications = false;

    private EnabledRulesEngine enabledEngine;

    private final BiConsumer<CustomField, ConceptDTO> formScopeChangeCallback;
    private final String formScopeValueBinding;

    // Column enabled state; if key missing, considered enabled
    private final Map<Long, Boolean> colEnabledByFieldId = new HashMap<>();

    // For multi-select spatial unit tree UI (per-answer state)
    private final Map<CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel,
            TreeUiStateViewModel> treeStates =
            new HashMap<>();

    // Rules engine plumbing
    private final ValueProvider vp = this::getFieldAnswer;
    private final ColumnApplier applier = (colField, enabled) ->
            colEnabledByFieldId.put(colField.getId(), enabled);

    // Saving methods
    private static final Map<Class<? extends AbstractEntityDTO>, EntityFormContextSaveStrategy<? extends AbstractEntityDTO>> SAVE_STRATEGIES =
            new HashMap<>();

    static {
        SAVE_STRATEGIES.put(RecordingUnitDTO.class, new RecordingUnitSaveStrategy());
        SAVE_STRATEGIES.put(ActionUnitDTO.class, new ActionUnitSaveStrategy());
        SAVE_STRATEGIES.put(SpatialUnitDTO.class, new SpatialUnitSaveStrategy());
        SAVE_STRATEGIES.put(SpecimenDTO.class, new SpecimenSaveStrategy());
    }

    public EntityFormContext(T unit,
                             FieldSource fieldSource,
                             FormContextServices services,
                             ConversionService conversionService,
                             BiConsumer<CustomField, ConceptDTO> formScopeChangeCallback,
                             String formScopeValueBinding) {
        this.unit = unit;
        this.fieldSource = fieldSource;
        this.services = services;
        this.formService = services.getFormService();
        this.actionUnitService = services.getActionUnitService();
        this.geoPlatService = services.getGeoPlatService();
        this.spatialUnitTreeService = services.getSpatialUnitTreeService();
        this.specimenService = services.getSpecimenService();
        this.spatialUnitService = services.getSpatialUnitService();
        this.recordingUnitService = services.getRecordingUnitService();
        this.langBean = services.getLangBean();
        this.conversionService = conversionService;
        this.sessionSettingsBean = services.getSessionSettingsBean();
        this.geoApiService = services.getGeoApiService();
        this.formScopeChangeCallback = formScopeChangeCallback;
        this.formScopeValueBinding = formScopeValueBinding;
        this.conceptService = services.getConceptService();
        this.conceptMapper = services.getConceptMapper();
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

    }

    // -------------------------------------------------------------------------
    // Column / answer helpers
    // -------------------------------------------------------------------------

    public CustomFieldAnswerViewModel getFieldAnswer(CustomField field) {
        if (formResponse == null || formResponse.getAnswers() == null) return null;
        return formResponse.getAnswers().get(field);
    }

    public boolean isColumnEnabled(CustomField field) {
        return colEnabledByFieldId.getOrDefault(field.getId(), true) ||
                (formResponse.getAnswers().get(field) != null);
    }

    /**
     * Mark a field as modified and set global "hasUnsavedModifications".
     */
    public void markFieldModified(CustomField field) {
        CustomFieldAnswerViewModel answer = getFieldAnswer(field);
        if (answer != null) {
            answer.setHasBeenModified(true);
        }
        hasUnsavedModifications = true;
    }

    /**
     * Mark a field as not modified
     */
    public void markFieldNotModified(CustomField field) {
        CustomFieldAnswerViewModel answer = getFieldAnswer(field);
        if (answer != null) {
            answer.setHasBeenModified(false);
        }
    }

    /**
     * Notify that a Concept answer changed on the given field – triggers enabled rules re-eval.
     */
    public void onConceptChanged(CustomField field, ConceptAutocompleteDTO newVal) {
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



    /**
     * Returns the root TreeNode for a given spatial-unit-tree answer.
     */
    public TreeNode<SpatialUnitSummaryDTO> getRoot(CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel answer) {
        TreeUiStateViewModel ui = treeStates.get(answer);
        return ui != null ? ui.getRoot() : null;
    }

    /**
     * Returns normalized selected spatial units (business-level "chips").
     */
    public List<SpatialUnitSummaryDTO> getNormalizedSpatialUnits(CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel answer) {
        TreeUiStateViewModel ui = treeStates.get(answer);
        if (ui == null) return Collections.emptyList();
        return getNormalizedSelectedUnits(ui.getSelection());
    }



    public boolean removeSpatialUnit(CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel answer, SpatialUnitSummaryDTO su) {
        TreeUiStateViewModel ui = treeStates.get(answer);
        if (ui == null || ui.getSelection() == null) return false;
        boolean removed = ui.getSelection().remove(su);
        if (removed) {
            markTreeAnswerModified(answer);
        }
        return removed;
    }

    private void markTreeAnswerModified(CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel answer) {
        answer.setHasBeenModified(true);
        hasUnsavedModifications = true;
    }

    /**
     * Normalize the selection for "chips" at business level (multi-parent graph).
     * <p>
     * Keeps a minimal set where no selected node is a descendant of another selected node.
     */
    public List<SpatialUnitSummaryDTO> getNormalizedSelectedUnits(Set<SpatialUnitSummaryDTO> selectedNodes) {
        if (selectedNodes == null || selectedNodes.isEmpty()) return Collections.emptyList();

        Map<Long, SpatialUnitSummaryDTO> byId = new HashMap<>();
        Set<Long> selectedIds = new LinkedHashSet<>();
        for (SpatialUnitSummaryDTO u : selectedNodes) {
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

        List<SpatialUnitSummaryDTO> chips = selectedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        chips.sort(Comparator.comparing(SpatialUnitSummaryDTO::getName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return chips;
    }

    /**
     * Returns all ancestor IDs in the business graph (transitive), with cycle detection.
     */
    private Set<Long> getAllAncestorIds(long id) {
        Set<Long> res = new HashSet<>();

        Deque<Long> stack = spatialUnitService.findDirectParentsOf(id).stream()
                .map(SpatialUnitDTO::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(ArrayDeque::new));

        while (!stack.isEmpty()) {
            long cur = stack.pop();
            if (res.add(cur)) {
                List<Long> parents = spatialUnitService.findDirectParentsOf(cur).stream()
                        .map(SpatialUnitDTO::getId)
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

    public void handleConceptChange(CustomField field, ConceptAutocompleteDTO newValue) {

        CustomFieldAnswerSelectOneFromFieldCodeViewModel ans = (CustomFieldAnswerSelectOneFromFieldCodeViewModel) formResponse.getAnswers().get(field);
        ans.setValue(newValue);

        if (autoSave) {
            // Save the change
            boolean status = save();
            if (status) {
                markFieldNotModified(field);
            } else {
                setFieldAnswerHasBeenModified(field);
            }
        }


        // Apply concept change logic
        onConceptChanged(field, newValue);

        // If it's the field defining the form, change form
        if (isFormScopeField(field) && formScopeChangeCallback != null) {
            formScopeChangeCallback.accept(field, newValue.getConceptLabelToDisplay().getConcept());
        }


    }

    private boolean isFormScopeField(CustomField field) {
        return field != null
                && Boolean.TRUE.equals(field.getIsSystemField())
                && formScopeValueBinding != null
                && formScopeValueBinding.equals(field.getValueBinding());
    }

    public String getAutocompleteClass() {
        if (unit instanceof RecordingUnitDTO) return "recording-unit-autocomplete";
        if (unit instanceof SpatialUnitDTO) return "spatial-unit-autocomplete";
        return "";
    }

    /**
     * Returns all the spatial units a recording unit can be attached to
     *
     * @return The list of spatial unit
     */
    public List<PlaceSuggestionDTO> getSpatialUnitOptions(String query) {

        List<PlaceSuggestionDTO> results = new ArrayList<>();

        String source = "";

        UIComponent component = UIComponent.getCurrentComponent(FacesContext.getCurrentInstance());
        Object attr = component.getAttributes().get(FIELD);

        if (attr instanceof CustomFieldSelectOneSpatialUnit f) {
            source = f.getSource();
        } else if (attr instanceof CustomFieldSelectMultipleSpatialUnitTree f) {
            source = f.getSource();
        }

        if (unit instanceof ActionUnitDTO au) {

            // 1. Priorité Base de données (Interne)
            List<PlaceSuggestionDTO> internal = spatialUnitService.findTop3ByInstitutionIdBySimilarity(
                    au.getCreatedByInstitution().getId(),
                    query);

            results.addAll(internal);

            // 2. Appel API
            List<PlaceSuggestionDTO> external = new ArrayList<>();
            if (Objects.equals(source, "INSEE")) {
                external = geoApiService.fetchCommunes(query);
            }
            else if (Objects.equals(source, "GEOPLAT")) {
                Concept addressConcept = conceptService.findById(418).orElse(new Concept());
                ConceptDTO conceptDTO = conceptMapper.convert(addressConcept);
                List<FullAddress> fullAddressList = geoPlatService.search(query);
                external = fullAddressList.stream()
                        .map(r -> {
                            PlaceSuggestionDTO dto = new PlaceSuggestionDTO();
                            dto.setName(r.getLabel());
                            dto.setCategory(conceptDTO);
                            dto.setCode(r.getLabel());
                            dto.setSourceName("GEOPLAT");
                            return dto;
                        })
                        .toList();
            }


            // On ajoute les externes qui ne sont pas déjà en base (par code)
            Set<String> internalCodes = internal.stream()
                    .map(PlaceSuggestionDTO::getCode)
                    .collect(Collectors.toSet());

            external.stream()
                    .filter(e -> !internalCodes.contains(e.getCode()))
                    .limit(7) // Pour garder un total raisonnable de ~10
                    .forEach(results::add);

            return results;
        }

        if (unit instanceof RecordingUnitDTO ru) {
            return spatialUnitService.getSpatialUnitOptionsFor(ru).stream()
                    .map(this::mapSummaryToSuggestion)
                    .toList();
        }

        return Collections.emptyList();

    }


    private PlaceSuggestionDTO mapSummaryToSuggestion(SpatialUnitSummaryDTO summary) {
        PlaceSuggestionDTO dto = new PlaceSuggestionDTO();
        dto.setId(summary.getId());
        dto.setName(summary.getName());
        dto.setCode(summary.getCode());
        dto.setCategory(summary.getCategory());
        dto.setSourceName("SIAMOIS");
        return dto;
    }

    public void setFieldAnswerHasBeenModified(CustomField field) {
        markFieldModified(field);

    }

    public void saveNewPlaceFromField(CustomFieldAnswerSelectOneSpatialUnitViewModel answer) {
        try {
            SpatialUnitDTO toSave = new SpatialUnitDTO();
            toSave.setName(answer.getNewName());
            toSave.setCategory(answer.getNewType().concept());
            toSave = spatialUnitService.save(sessionSettingsBean.getUserInfo(), toSave);
            answer.setValue(services.getPlaceSuggestionMapper().convert(toSave));
            if (unit.getId() != null) {
                this.save();
            }

        } catch (Exception e) {
            MessageUtils.displayErrorMessage(langBean, "dialog.unsaved.error", e.getMessage());
        }

    }

    public void saveNewPlaceFromField(CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel answer) {

        try {
            SpatialUnitDTO toSave = new SpatialUnitDTO();
            toSave.setName(answer.getNewName());
            toSave.setCategory(answer.getNewType().concept());
            toSave = spatialUnitService.save(sessionSettingsBean.getUserInfo(), toSave);
            answer.getValue().add(services.getPlaceSuggestionMapper().convert(toSave));
            if (unit.getId() != null) {
                this.save();
            }
        } catch (Exception e) {
            MessageUtils.displayErrorMessage(langBean, "dialog.unsaved.error", e.getMessage());
        }
    }

    public void onFieldAnswerModifiedListener(AjaxBehaviorEvent event) {
        CustomField field = (CustomField) event.getComponent().getAttributes().get(FIELD);
        if (autoSave) {
            // Save the change
            boolean status = save();
            if (status) {
                markFieldNotModified(field);
            } else {
                setFieldAnswerHasBeenModified(field);
            }
        }
    }

    public void setFieldConceptAnswerHasBeenModified(SelectEvent<ConceptAutocompleteDTO> event) {
        UIComponent component = event.getComponent();
        CustomField field = (CustomField) component.getAttributes().get(FIELD);

        handleConceptChange(field, event.getObject());
    }

    /**
     * Get all recording units of the same scope (action unit) as the current unit.
     *
     * @return The list of recording units
     */
    public List<RecordingUnitSummaryDTO> getRecordingUnitOptions() {
        if (unit instanceof RecordingUnitDTO recordingUnit) {
            return recordingUnitService.findAllByActionUnit(recordingUnit.getActionUnit().getId());
        }
        return Collections.emptyList();
    }


    public void addStratigraphicRelationship(CustomFieldAnswerStratigraphyViewModel answer,
                                             FacesContext context,
                                             UIComponent cc) {

        if (!validateInputs(answer, context, cc)) {
            context.validationFailed();
            return;
        }

        if (relationshipExists(answer)) {
            markAsInvalid(context, cc, SELECT_RU, "Une relation existe déjà entre ces deux unités");
            context.validationFailed();
            return;
        }

        addNewStratigraphicRelationship(answer);

        // Optionally, reset the form fields
        answer.setConceptToAdd(null);
        answer.setTargetToAdd(null);
        answer.setIsUncertainToAdd(false);

        PrimeFaces.current().ajax().update(cc.getClientId().concat(":stratigraphyGraphContainer"));
    }

    private boolean validateInputs(CustomFieldAnswerStratigraphyViewModel answer, FacesContext context, UIComponent cc) {
        boolean isValid = true;

        if (answer.getConceptToAdd() == null) {
            markAsInvalid(context, cc, "relationshipVocab", "Ne peux pas être vide");
            isValid = false;
        }

        if (answer.getTargetToAdd() == null) {
            markAsInvalid(context, cc, SELECT_RU, "Ne peux pas être vide");
            isValid = false;
        } else if (Objects.equals(answer.getTargetToAdd().getFullIdentifier(), answer.getSourceToAdd().getFullIdentifier())) {
            markAsInvalid(context, cc, SELECT_RU, "Les deux UE ne peuvent être identiques");
            isValid = false;
        }

        return isValid;
    }

    private void markAsInvalid(FacesContext context, UIComponent cc, String componentId, String message) {
        UIInput c = (UIInput) cc.findComponent(componentId);
        c.setValid(false);
        FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_ERROR, message, null);
        context.addMessage(c.getClientId(context), msg);
    }

    private boolean relationshipExists(CustomFieldAnswerStratigraphyViewModel answer) {
        return checkSynchronousRelationships(answer) ||
                checkPosteriorRelationships(answer) ||
                checkAnteriorRelationships(answer);
    }

    private boolean checkSynchronousRelationships(CustomFieldAnswerStratigraphyViewModel answer) {
        for (StratigraphicRelationshipDTO rel : answer.getSynchronousRelationships()) {
            if ((rel.getUnit1().equals(answer.getSourceToAdd()) && rel.getUnit2().equals(answer.getTargetToAdd())) ||
                    (rel.getUnit1().equals(answer.getTargetToAdd()) && rel.getUnit2().equals(answer.getSourceToAdd()))) {
                return true;
            }
        }
        return false;
    }

    private boolean checkPosteriorRelationships(CustomFieldAnswerStratigraphyViewModel answer) {
        for (StratigraphicRelationshipDTO rel : answer.getPosteriorRelationships()) {
            if (rel.getUnit1().equals(answer.getSourceToAdd()) && rel.getUnit2().equals(answer.getTargetToAdd())) {
                return true;
            }
        }
        return false;
    }

    private boolean checkAnteriorRelationships(CustomFieldAnswerStratigraphyViewModel answer) {
        for (StratigraphicRelationshipDTO rel : answer.getAnteriorRelationships()) {
            if (rel.getUnit1().equals(answer.getTargetToAdd()) && rel.getUnit2().equals(answer.getSourceToAdd())) {
                return true;
            }
        }
        return false;
    }

    private void addNewStratigraphicRelationship(CustomFieldAnswerStratigraphyViewModel answer) {
        StratigraphicRelationshipDTO newRel = new StratigraphicRelationshipDTO();
        String parentLabel = getParentLabel(answer);

        if (parentLabel.equalsIgnoreCase("synchrone avec")) {
            setupSynchronousRelationship(answer, newRel);
        } else if (parentLabel.equalsIgnoreCase("postérieur à")) {
            setupPosteriorRelationship(answer, newRel);
        } else if (parentLabel.equalsIgnoreCase("antérieur à")) {
            setupAnteriorRelationship(answer, newRel);
        }
    }

    private String getParentLabel(CustomFieldAnswerStratigraphyViewModel answer) {
        return answer.getConceptToAdd().getHierarchyPrefLabels() == null ?
                answer.getConceptToAdd().getOriginalPrefLabel() :
                answer.getConceptToAdd().getHierarchyPrefLabels();
    }

    private void setupSynchronousRelationship(CustomFieldAnswerStratigraphyViewModel answer,
                                              StratigraphicRelationshipDTO newRel) {
        newRel.setUnit1(answer.getSourceToAdd());
        newRel.setUnit2(answer.getTargetToAdd());
        newRel.setConcept(answer.getConceptToAdd().concept());
        newRel.setIsAsynchronous(false);
        newRel.setUncertain(answer.getIsUncertainToAdd());
        newRel.setConceptDirection(answer.getVocabularyDirectionToAdd());
        answer.getSynchronousRelationships().add(newRel);
    }

    private void setupPosteriorRelationship(CustomFieldAnswerStratigraphyViewModel answer,
                                            StratigraphicRelationshipDTO newRel) {
        newRel.setUnit1(answer.getSourceToAdd());
        newRel.setUnit2(answer.getTargetToAdd());
        newRel.setConcept(answer.getConceptToAdd().concept());
        newRel.setIsAsynchronous(true);
        newRel.setUncertain(answer.getIsUncertainToAdd());
        newRel.setConceptDirection(answer.getVocabularyDirectionToAdd());
        answer.getPosteriorRelationships().add(newRel);
    }

    private void setupAnteriorRelationship(CustomFieldAnswerStratigraphyViewModel answer,
                                           StratigraphicRelationshipDTO newRel) {
        newRel.setUnit1(answer.getTargetToAdd());
        newRel.setUnit2(answer.getSourceToAdd());
        newRel.setConcept(answer.getConceptToAdd().concept());
        newRel.setIsAsynchronous(true);
        newRel.setUncertain(answer.getIsUncertainToAdd());
        newRel.setConceptDirection(!answer.getVocabularyDirectionToAdd());
        answer.getAnteriorRelationships().add(newRel);
    }


    public String getRelationshipsAsJson(CustomFieldAnswerStratigraphyViewModel answer) {

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        RecordingUnitSummaryDTO centralUnit = answer.getSourceToAdd();

        // anterior
        ArrayNode anteriorArray = mapper.createArrayNode();
        for (StratigraphicRelationshipDTO rel : answer.getAnteriorRelationships()) {
            ObjectNode node = mapper.createObjectNode();
            node.put(UNIT_1_ID, rel.getUnit1().getFullIdentifier());
            node.put(DATABASE_ID, rel.getUnit1().getId());
            node.put(VOCABULARY_LABEL, formService.getLabelBean().findLabelOf(rel.getConcept()));
            node.put(VOCABULARY_DIRECTION, rel.getConceptDirection());
            node.put(UNCERTAIN, rel.getUncertain() != null && rel.getUncertain());
            anteriorArray.add(node);
        }
        root.set("anterior", anteriorArray);

        // posterior
        ArrayNode posteriorArray = mapper.createArrayNode();
        for (StratigraphicRelationshipDTO rel : answer.getPosteriorRelationships()) {
            ObjectNode node = mapper.createObjectNode();
            node.put(UNIT_1_ID, rel.getUnit2().getFullIdentifier());
            node.put(DATABASE_ID, rel.getUnit2().getId());
            node.put(VOCABULARY_LABEL, formService.getLabelBean().findLabelOf(rel.getConcept()));
            node.put(VOCABULARY_DIRECTION, !rel.getConceptDirection());
            node.put(UNCERTAIN, rel.getUncertain() != null && rel.getUncertain());
            posteriorArray.add(node);
        }
        root.set("posterior", posteriorArray);

        // synchronous
        ArrayNode synchronousArray = mapper.createArrayNode();

        for (StratigraphicRelationshipDTO rel : answer.getSynchronousRelationships()) {
            ObjectNode node = mapper.createObjectNode();

            RecordingUnitSummaryDTO otherUnit;
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

            node.put(UNIT_1_ID, otherUnit.getFullIdentifier());
            node.put(DATABASE_ID, otherUnit.getId());
            node.put(VOCABULARY_LABEL,
                    formService.getLabelBean().findLabelOf(rel.getConcept()));
            node.put(VOCABULARY_DIRECTION, direction);
            node.put(UNCERTAIN, Boolean.TRUE.equals(rel.getUncertain()));

            synchronousArray.add(node);
        }

        root.set("synchronous", synchronousArray);

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    public boolean save() {
        EntityFormContextSaveStrategy<T> strategy = (EntityFormContextSaveStrategy<T>) SAVE_STRATEGIES.get(unit.getClass());
        if (strategy != null) {
            return strategy.save(this);
        } else {
            throw new UnsupportedOperationException(
                    "No save strategy defined for type: " + unit.getClass().getSimpleName()
            );
        }
    }

    public List<FullAddress> completeAdresse(String query) {
        return geoPlatService.search(query);
    }

}
