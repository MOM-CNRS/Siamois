package fr.siamois.domain.services.form;


import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.customfieldanswer.*;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.EnabledWhenJson;
import fr.siamois.domain.models.form.customform.ValueMatcher;
import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.*;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.form.FormRepository;
import fr.siamois.infrastructure.database.repositories.form.FormScopeRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.UnitDefinitionMapper;
import fr.siamois.ui.bean.LabelBean;
import fr.siamois.ui.form.CustomFieldAnswerFactory;
import fr.siamois.ui.form.ValueMatcherFactory;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.rules.*;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Stateless service containing reusable form logic:
 * - initialize CustomFormResponse for a JPA entity
 * - bind system fields to/from the entity
 * - build enabled-when rules engines
 * <p>
 * It is agnostic of layout (single panel vs table row) thanks to FieldSource.
 */
@Service
@Getter
@RequiredArgsConstructor
public class FormService {

    private final LabelBean labelBean;
    private final FormRepository formRepository;
    private final FormScopeRepository formScopeRepository;
    private final UnitDefinitionMapper unitDefinitionMapper;


    /**
     * Find a form by its ID
     *
     * @param id The ID of the form
     * @return The form having the given ID
     */
    @Transactional(readOnly = true)
    public CustomForm findById(long id) {
        return formRepository.findById(id).orElse(null);
    }

    /**
     * Find the form to display for a given type of recording unit in the context of an institution
     *
     * @param recordingUnitType The type of recording unit
     * @param institution       The institution
     * @return The form
     */
    @Transactional(readOnly = true)
    public CustomForm findCustomFormByRecordingUnitTypeAndInstitutionId(ConceptDTO recordingUnitType, InstitutionDTO institution) {
        Optional<CustomForm> optForm = formRepository.findEffectiveFormByTypeAndInstitution(recordingUnitType == null ? null : recordingUnitType.getId(), institution.getId());
        // If none found, try to find a form without specifying the type
        // Should we throw an error if none found?
        return optForm.orElseGet(() -> formRepository.findEffectiveFormByTypeAndInstitution(null, institution.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<Concept> findConfiguredRecordingUnitTypesByInstitution(InstitutionDTO institution) {
        return formScopeRepository.findConfiguredTypesByInstitution(institution.getId());
    }

    // --------- Answer creators

    private static final Map<Class<? extends CustomField>, Supplier<? extends CustomFieldAnswer>> ANSWER_CREATORS =
            Map.ofEntries(
                    Map.entry(CustomFieldText.class, CustomFieldAnswerText::new),
                    Map.entry(CustomFieldSelectOneFromFieldCode.class, CustomFieldAnswerSelectOneFromFieldCode::new),
                    Map.entry(CustomFieldSelectMultiplePerson.class, CustomFieldAnswerSelectMultiplePerson::new),
                    Map.entry(CustomFieldDateTime.class, CustomFieldAnswerDateTime::new),
                    Map.entry(CustomFieldSelectOneActionUnit.class, CustomFieldAnswerSelectOneActionUnit::new),
                    Map.entry(CustomFieldSelectOneSpatialUnit.class, CustomFieldAnswerSelectOneSpatialUnit::new),
                    Map.entry(CustomFieldSelectMultipleSpatialUnitTree.class, CustomFieldAnswerSelectMultipleSpatialUnitTree::new),
                    Map.entry(CustomFieldSelectOneActionCode.class, CustomFieldAnswerSelectOneActionCode::new),
                    Map.entry(CustomFieldInteger.class, CustomFieldAnswerInteger::new),
                    Map.entry(CustomFieldSelectOnePerson.class, CustomFieldAnswerSelectOnePerson::new)
            );

    /**
     * Create or reuse a CustomFormResponse for the given entity + field source.
     *
     * @param existing    existing response (may be null)
     * @param jpaEntity   entity we bind system fields against
     * @param fieldSource abstraction over fields (single panel or row)
     * @param forceInit   if true, ignore existing answers and rebuild everything
     */
    public CustomFormResponseViewModel initOrReuseResponse(CustomFormResponseViewModel existing,
                                                           Object jpaEntity,
                                                           FieldSource fieldSource,
                                                           boolean forceInit) {

        CustomFormResponseViewModel response;
        Map<CustomField, CustomFieldAnswerViewModel> answers;

        if (existing != null && !forceInit) {
            response = existing;
            answers = response.getAnswers();
            if (answers == null) {
                answers = new HashMap<>();
            }
        } else {
            response = new CustomFormResponseViewModel();
            answers = new HashMap<>();
        }

        boolean onlyInitMissing = (existing != null && !forceInit);
        List<String> bindableFields = getBindableFieldNames(jpaEntity);

        for (CustomField field : fieldSource.getAllFields()) {

            if (field == null ||
                    (onlyInitMissing && answers.containsKey(field))) {
                continue;
            }

            CustomFieldAnswerViewModel answer =
                    CustomFieldAnswerFactory.instantiateAnswerForField(field);

            if (answer != null) {
                initializeAnswer(answer, field, jpaEntity, bindableFields);
                answers.put(field, answer);
            }
        }


        response.setAnswers(answers);
        return response;
    }

    /**
     * Create or reuse a CustomFormResponse for the given entity + field source.
     *
     * @param answers   the answers
     * @param jpaEntity entity we bind system fields against
     * @param field     the fiels
     */
    public void initOneAnswer(CustomFormResponseViewModel answers,
                              Object jpaEntity,
                              CustomField field) {


        List<String> bindableFields = getBindableFieldNames(jpaEntity);

        CustomFieldAnswerViewModel answer = answers.getAnswers().get(field);

        if (answer != null) {
            initializeAnswer(answer, field, jpaEntity, bindableFields);
        }

    }

    // ------------------- Enabled rules

    /**
     * Build an EnabledRulesEngine for all fields in the given FieldSource.
     * Uses EnabledWhenJson on each field (if any).
     */
    public EnabledRulesEngine buildEnabledEngine(FieldSource fieldSource) {
        List<ColumnRule> rules = new ArrayList<>();

        for (CustomField field : fieldSource.getAllFields()) {
            EnabledWhenJson spec = fieldSource.getEnabledSpec(field);
            if (spec == null) continue;

            Condition cond = toCondition(spec, fieldSource);
            rules.add(new ColumnRule(field, cond));
        }

        return new EnabledRulesEngine(rules);
    }

    private Condition toCondition(EnabledWhenJson ew, FieldSource fieldSource) {
        // Compared field from its id
        CustomField comparedField = fieldSource.findFieldById(ew.getFieldId());
        if (comparedField == null) {
            throw new IllegalStateException("enabledWhen.fieldId=" + ew.getFieldId() + " not found in layout");
        }

        // expected values (JSON) -> generic ValueMatcher
        List<ValueMatcher> matchers = ew.getValues().stream()
                .map(this::toMatcher)
                .toList();

        return switch (ew.getOp()) {
            case EQ -> new EqCondition(comparedField, matchers.get(0));
            case NEQ -> new NeqCondition(comparedField, matchers.get(0));
            case IN -> new InCondition(comparedField, matchers);
        };
    }

    private ValueMatcher toMatcher(EnabledWhenJson.ValueJson vj) {
        String className = vj.getAnswerClass();
        return switch (className) {
            case "fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectOneFromFieldCode" ->
                    ValueMatcherFactory.forSelectOneFromFieldCode(vj);
            default -> ValueMatcherFactory.defaultMatcher();
        };
    }


    // -------------- Entity <-> answer binding

    /**
     * Apply all bindable system fields from the response back into the JPA entity.
     * This is basically your previous updateJpaEntityFromFormResponse method.
     */
    public void updateJpaEntityFromResponse(CustomFormResponseViewModel response, Object jpaEntity) {
        if (response == null || jpaEntity == null) return;

        List<String> bindableFields = getBindableFieldNames(jpaEntity);

        for (Map.Entry<CustomField, CustomFieldAnswerViewModel> entry : response.getAnswers().entrySet()) {
            CustomField field = entry.getKey();
            CustomFieldAnswerViewModel answer = entry.getValue();

            if (answer instanceof CustomFieldAnswerStratigraphyViewModel stratiAnswer &&
                    jpaEntity instanceof RecordingUnitDTO ru) {
                // Special case
                setStratigraphyFieldValue(stratiAnswer, ru);
            } else if (isBindableSystemField(field, answer, bindableFields)) {
                Object value = extractValueFromAnswer(answer);
                if (value != null) {
                    setFieldValue(jpaEntity, field.getValueBinding(), value);
                }
            }
        }
    }

    private static boolean isBindableSystemField(CustomField field,
                                                 CustomFieldAnswerViewModel answer,
                                                 List<String> bindableFields) {
        return field != null
                && answer != null
                && Boolean.TRUE.equals(field.getIsSystemField())
                && field.getValueBinding() != null
                && bindableFields.contains(field.getValueBinding());
    }

    private static Object extractValueFromAnswer(CustomFieldAnswerViewModel answer) {
        if (answer instanceof CustomFieldAnswerDateTimeViewModel a && a.getValue() != null) {
            return a.getValue().atOffset(ZoneOffset.UTC);
        } else if (answer instanceof CustomFieldAnswerTextViewModel a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectMultiplePersonViewModel a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectOnePersonViewModel a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerMeasurementViewModel a) {
            if (a.getValue() != null && a.getValue().getNumericValue() != null) {
                return a.getValue();
            }
            return null;
        } else if (answer instanceof CustomFieldAnswerSelectOneFromFieldCodeViewModel a) {
            try {
                return a.getValue().concept();
            } catch (NullPointerException e) {
                return null;
            }
        } else if (answer instanceof CustomFieldAnswerSelectOneActionUnitViewModel a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectOneSpatialUnitViewModel a) {
            if (a.getValue() != null) {
                // Convert back to place dto (single selection)
                PlaceSuggestionDTO ans = a.getValue();
                SpatialUnitSummaryDTO dto = new SpatialUnitSummaryDTO();
                dto.setId(ans.getId());
                dto.setName(ans.getName());
                dto.setCode(ans.getCode());
                dto.setCategory(ans.getCategory());
                return dto;
            } else {
                return null;
            }

        } else if (answer instanceof CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel a) {
            // Convert each PlaceSuggestionDTO in the list to SpatialUnitSummaryDTO
            List<PlaceSuggestionDTO> placeSuggestionList = a.getValue();
            return placeSuggestionList.stream()
                    .map(place -> {
                        SpatialUnitSummaryDTO dto = new SpatialUnitSummaryDTO();
                        dto.setId(place.getId());
                        dto.setName(place.getName());
                        dto.setCode(place.getCode());
                        dto.setCategory(place.getCategory());
                        return dto;
                    })
                    .collect(Collectors.toSet());
        } else if (answer instanceof CustomFieldAnswerSelectOneActionCodeViewModel a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerIntegerViewModel a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectOneAddressViewModel a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectMultipleRecordingUnitViewModel a) {
            return new HashSet<>(a.getValue());
        } else if (answer instanceof CustomFieldAnswerSelectMultipleFromFieldCodeViewModel a) {
            if (a.getValue() == null) {
                return null;
            }
            return a.getValue().stream()
                    .map(ConceptAutocompleteDTO::concept)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } else if (answer instanceof CustomFieldAnswerSelectMultipleSpecimenViewModel a) {
            if (a.getValue() == null) {
                return null;
            }
            return new HashSet<>(a.getValue());
        }
        if (answer instanceof CustomFieldAnswerSelectMultipleContainerViewModel a) {
            return a.getValue() != null ? new HashSet<>(a.getValue()) : null;
        }
        if (answer instanceof CustomFieldAnswerSelectMultiplePhaseViewModel a) {
            return a.getValue() != null ? new HashSet<>(a.getValue()) : null;
        }

        return null;
    }

    /**
     * Valeur exposable pour une API (JSON), y compris cas particulier stratigraphie.
     */
    @Nullable
    public Object readAnswerValueForApi(@Nullable CustomFieldAnswerViewModel answer) {
        if (answer == null) {
            return null;
        }
        if (answer instanceof CustomFieldAnswerStratigraphyViewModel s) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("anterior", s.getAnteriorRelationships() != null
                    ? new ArrayList<>(s.getAnteriorRelationships()) : List.of());
            out.put("posterior", s.getPosteriorRelationships() != null
                    ? new ArrayList<>(s.getPosteriorRelationships()) : List.of());
            out.put("synchronous", s.getSynchronousRelationships() != null
                    ? new ArrayList<>(s.getSynchronousRelationships()) : List.of());
            return out;
        }
        return extractValueFromAnswer(answer);
    }

    // -------------- Internal helpers

    private void initializeAnswer(CustomFieldAnswerViewModel answer,
                                  CustomField field,
                                  Object jpaEntity,
                                  List<String> bindableFields) {

        CustomFieldAnswerId answerId = new CustomFieldAnswerId();
        answerId.setField(field);
        answer.setPk(answerId);
        answer.setHasBeenModified(false);

        if (answer instanceof CustomFieldAnswerStratigraphyViewModel stratiAnswer
                && jpaEntity instanceof RecordingUnitDTO ru) {
            // Special case
            handleStratigraphyRelationships(stratiAnswer, ru);
            return;
        }

        if (Boolean.TRUE.equals(field.getIsSystemField())
                && field.getValueBinding() != null
                && bindableFields.contains(field.getValueBinding())) {

            Object value = getFieldValue(jpaEntity, field.getValueBinding());
            populateSystemFieldValue(answer, value);

            // POST INIT
            if (field instanceof CustomFieldMeasurement measField
                    && answer instanceof CustomFieldAnswerMeasurementViewModel measAnswer
                    && measAnswer.getValue().getUnit() == null) {

                measAnswer.getValue().setUnit(
                        unitDefinitionMapper.convert(measField.getUnit())
                );
            }


        }
    }


    /**
     * Applique une valeur déjà typée (comme pour {@link #readAnswerValueForApi}) sur une réponse de champ.
     * Utile pour l’API OpenAPI qui sérialise les mêmes formes que le détail formulaire.
     */
    public void applyTypedValueToAnswer(CustomFieldAnswerViewModel answer, Object value) {
        populateSystemFieldValue(answer, value);
    }

    private void populateSystemFieldValue(CustomFieldAnswerViewModel answer, Object value) {

        Map<Class<? extends CustomFieldAnswerViewModel>, BiConsumer<CustomFieldAnswerViewModel, Object>> handlers = new HashMap<>();
        handlers.put(CustomFieldAnswerDateTimeViewModel.class, this::handleDateTime);
        handlers.put(CustomFieldAnswerTextViewModel.class, this::handleString);
        handlers.put(CustomFieldAnswerSelectOnePersonViewModel.class, this::handlePerson);
        handlers.put(CustomFieldAnswerSelectMultiplePersonViewModel.class, this::handlePersonList);
        handlers.put(CustomFieldAnswerSelectOneFromFieldCodeViewModel.class, this::handleConcept);
        handlers.put(CustomFieldAnswerSelectOneActionUnitViewModel.class, this::handleActionUnit);
        handlers.put(CustomFieldAnswerSelectOneSpatialUnitViewModel.class, this::handleSpatialUnit);
        handlers.put(CustomFieldAnswerSelectOneActionCodeViewModel.class, this::handleActionCode);
        handlers.put(CustomFieldAnswerIntegerViewModel.class, this::handleInteger);
        handlers.put(CustomFieldAnswerSelectOneAddressViewModel.class, this::handleAddress);
        handlers.put(CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel.class, this::handleSpatialUnitSet);
        handlers.put(CustomFieldAnswerSelectMultipleRecordingUnitViewModel.class, this::handleRecordingUnitSet);
        handlers.put(CustomFieldAnswerMeasurementViewModel.class, this::handleMeasurement);
        handlers.put(CustomFieldAnswerSelectMultipleContainerViewModel.class, this::handleContainerSet);
        handlers.put(CustomFieldAnswerSelectMultipleSpecimenViewModel.class, this::handleSpecimenSet);
        handlers.put(CustomFieldAnswerSelectMultiplePhaseViewModel.class, this::handlePhaseSet);
        handlers.put(CustomFieldAnswerSelectMultipleFromFieldCodeViewModel.class, this::handleConceptSet);
        handlers.put(CustomFieldAnswerSelectMultipleSpecimenViewModel.class, this::handleSpecimenSet);

        Class<? extends CustomFieldAnswerViewModel> answerClass = answer.getClass();
        BiConsumer<CustomFieldAnswerViewModel, Object> handler = handlers.get(answerClass);
        if (handler != null) {
            handler.accept(answer, value);
        }
    }


    // Méthodes dédiées pour chaque type de 'value'
    private void handleDateTime(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerDateTimeViewModel dateTimeAnswer) {
            LocalDateTime dateTime = null;
            if (value != null) {
                dateTime = ((OffsetDateTime) value).toLocalDateTime();
            }
            dateTimeAnswer.setValue(dateTime);
        }
    }

    private void handleMeasurement(CustomFieldAnswerViewModel answer, Object value) {
        MeasurementAnswerDTO meas = (MeasurementAnswerDTO) value;
        if (meas == null) {
            meas = new MeasurementAnswerDTO();
        }
        ((CustomFieldAnswerMeasurementViewModel) answer).setValue(meas);
    }

    private void handleAddress(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectOneAddressViewModel addressAnswer) {
            addressAnswer.setValue((FullAddress) value);
        }
    }

    private void handleString(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerTextViewModel textAnswer) {
            textAnswer.setValue((String) value);
        }
    }

    private void handlePerson(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectOnePersonViewModel singlePersonAnswer) {
            singlePersonAnswer.setValue((PersonDTO) value);
        }
    }

    @SuppressWarnings("unchecked")
    private void handlePersonList(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectMultiplePersonViewModel multiplePersonAnswer) {
            List<?> list = (List<?>) value;
            if (list.stream().allMatch(PersonDTO.class::isInstance)) {
                multiplePersonAnswer.setValue((List<PersonDTO>) list);
            }
        }
    }

    private void handleConcept(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectOneFromFieldCodeViewModel codeAnswer) {
            ConceptDTO c = (ConceptDTO) value;
            codeAnswer.setValue(new ConceptAutocompleteDTO(
                    c,
                    labelBean.findLabelOf(c),
                    labelBean.getCurrentUserLang()
            ));
        }
    }

    private void handleActionUnit(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectOneActionUnitViewModel actionUnitAnswer) {
            actionUnitAnswer.setValue((ActionUnitSummaryDTO) value);
        }
    }

    private void handleSpatialUnit(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectOneSpatialUnitViewModel spatialUnitAnswer) {
            // Convert to place suggestion
            SpatialUnitSummaryDTO val = (SpatialUnitSummaryDTO) value;
            PlaceSuggestionDTO dto ;
            if (val != null) {
                dto = mapToPlaceSuggestion(val);
                spatialUnitAnswer.setValue(dto);
            }

        }
    }

    private void handleActionCode(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectOneActionCodeViewModel actionCodeAnswer) {
            actionCodeAnswer.setValue((ActionCodeDTO) value);
        }
    }

    private void handleInteger(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerIntegerViewModel integerAnswer) {
            integerAnswer.setValue((Integer) value);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleSpatialUnitSet(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel treeAnswer && value instanceof Collection<?> values) {

            List<PlaceSuggestionDTO> dtos = values.stream()
                    .filter(SpatialUnitSummaryDTO.class::isInstance)
                    .map(SpatialUnitSummaryDTO.class::cast)
                    .map(this::mapToPlaceSuggestion) // Utilisation d'une méthode d'aide pour la clarté
                    .toList();

            treeAnswer.setValue(new ArrayList<>(dtos));
        }
    }

    private void handleContainerSet(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectMultipleContainerViewModel containerAnswer && value instanceof Set<?> values) {
            containerAnswer.setValue(new ArrayList<>((Set<ContainerDTO>) values));
        }
    }

    private void handlePhaseSet(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectMultiplePhaseViewModel phaseAnswer && value instanceof Set<?> values) {
            phaseAnswer.setValue(new ArrayList<>((Set<PhaseDTO>) values));
        }
    }

    private PlaceSuggestionDTO mapToPlaceSuggestion(SpatialUnitSummaryDTO val) {
        PlaceSuggestionDTO dto = new PlaceSuggestionDTO();
        dto.setId(val.getId());
        dto.setName(val.getName());
        dto.setCode(val.getCode());
        dto.setSourceName("INTERNAL");
        dto.setCategory(val.getCategory());
        return dto;
    }

    private void handleRecordingUnitSet(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectMultipleRecordingUnitViewModel ans && value instanceof Set<?> values) {
            ans.setValue(new ArrayList<>((Set<RecordingUnitSummaryDTO>) values));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleConceptSet(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectMultipleFromFieldCodeViewModel multiAnswer
                && value instanceof Collection<?> concepts) {
            List<ConceptAutocompleteDTO> dtos = concepts.stream()
                    .filter(ConceptDTO.class::isInstance)
                    .map(ConceptDTO.class::cast)
                    .map(c -> new ConceptAutocompleteDTO(
                            c,
                            labelBean.findLabelOf(c),
                            labelBean.getCurrentUserLang()))
                    .toList();
            multiAnswer.setValue(new ArrayList<>(dtos));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleSpecimenSet(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectMultipleSpecimenViewModel multiAnswer
                && value instanceof Collection<?> specimens) {
            List<SpecimenSummaryDTO> list = specimens.stream()
                    .filter(SpecimenSummaryDTO.class::isInstance)
                    .map(SpecimenSummaryDTO.class::cast)
                    .toList();
            multiAnswer.setValue(new ArrayList<>(list));
        }
    }

    public void handleStratigraphyRelationships(CustomFieldAnswerStratigraphyViewModel answer, RecordingUnitDTO unit) {
        // Set the source unit for the answer
        answer.setSourceToAdd(new RecordingUnitSummaryDTO(unit));

        // Clear existing lists to avoid duplicates
        answer.getAnteriorRelationships().clear();
        answer.getPosteriorRelationships().clear();
        answer.getSynchronousRelationships().clear();

        // Process relationships where unit is unit1
        for (StratigraphicRelationshipDTO rel : unit.getRelationshipsAsUnit1()) {
            if (Boolean.FALSE.equals(rel.getIsAsynchronous())) {
                // Synchronous
                answer.getSynchronousRelationships().add(rel);
            } else {
                // Asynchronous → current unit is unit1, goes to posterior
                answer.getPosteriorRelationships().add(rel);
            }
        }

        // Process relationships where unit is unit2
        for (StratigraphicRelationshipDTO rel : unit.getRelationshipsAsUnit2()) {
            if (Boolean.FALSE.equals(rel.getIsAsynchronous())) {
                // Synchronous
                answer.getSynchronousRelationships().add(rel);
            } else {
                // Asynchronous → current unit is unit2, goes to anterior
                answer.getAnteriorRelationships().add(rel);
            }
        }
    }


    @SuppressWarnings("unchecked")
    private static List<String> getBindableFieldNames(Object entity) {
        if (entity == null) return Collections.emptyList();
        try {
            Method method = entity.getClass().getMethod("getBindableFieldNames");
            return (List<String>) method.invoke(entity);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static Object getFieldValue(Object obj, String fieldName) {
        if (obj == null || fieldName == null) return null;
        try {
            PropertyDescriptor pd = new PropertyDescriptor(fieldName, obj.getClass());
            return pd.getReadMethod().invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static void setFieldValue(Object obj, String fieldName, Object value) {
        if (obj == null || fieldName == null) return;
        try {
            PropertyDescriptor pd = new PropertyDescriptor(fieldName, obj.getClass());
            Method setter = pd.getWriteMethod();
            setter.invoke(obj, value);
        } catch (Exception e) {
            // ignored, the value won't be set
        }
    }

    public void setStratigraphyFieldValue(
            CustomFieldAnswerStratigraphyViewModel stratiAnswer,
            RecordingUnitDTO entity) {
        // Clear existing relationships to avoid duplicates
        entity.getRelationshipsAsUnit1().clear();
        entity.getRelationshipsAsUnit2().clear();

        // Helper method to add relationships to the correct set
        for (StratigraphicRelationshipDTO rel : stratiAnswer.getAnteriorRelationships()) {
            if (rel.getUnit1().getId().equals(entity.getId())) {
                entity.getRelationshipsAsUnit1().add(rel);
            } else if (rel.getUnit2().getId().equals(entity.getId())) {
                entity.getRelationshipsAsUnit2().add(rel);
            }
        }

        for (StratigraphicRelationshipDTO rel : stratiAnswer.getPosteriorRelationships()) {
            if (rel.getUnit1().getId().equals(entity.getId())) {
                entity.getRelationshipsAsUnit1().add(rel);
            } else if (rel.getUnit2().getId().equals(entity.getId())) {
                entity.getRelationshipsAsUnit2().add(rel);
            }
        }

        for (StratigraphicRelationshipDTO rel : stratiAnswer.getSynchronousRelationships()) {
            if (rel.getUnit1().getId().equals(entity.getId())) {
                entity.getRelationshipsAsUnit1().add(rel);
            } else if (rel.getUnit2().getId().equals(entity.getId())) {
                entity.getRelationshipsAsUnit2().add(rel);
            }
        }
    }


}

