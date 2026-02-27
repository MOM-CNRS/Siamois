package fr.siamois.domain.services.form;


import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.customfieldanswer.*;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.EnabledWhenJson;
import fr.siamois.domain.models.form.customform.ValueMatcher;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.form.FormRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.bean.LabelBean;
import fr.siamois.ui.form.CustomFieldAnswerFactory;
import fr.siamois.ui.form.rules.EnabledRulesEngine;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.ValueMatcherFactory;
import fr.siamois.ui.form.rules.*;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Stateless service containing reusable form logic:
 *  - initialize CustomFormResponse for a JPA entity
 *  - bind system fields to/from the entity
 *  - build enabled-when rules engines
 *
 * It is agnostic of layout (single panel vs table row) thanks to FieldSource.
 */
@Service
@Getter
@RequiredArgsConstructor
public class FormService {

    private final LabelBean labelBean;
    private final FormRepository formRepository;


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
     * @param institution The institution
     * @return The form
     */
    @Transactional(readOnly = true)
    public CustomForm findCustomFormByRecordingUnitTypeAndInstitutionId(ConceptDTO recordingUnitType, InstitutionDTO institution) {
        Optional<CustomForm> optForm = formRepository.findEffectiveFormByTypeAndInstitution(recordingUnitType == null ? null : recordingUnitType.getId(), institution.getId());
        // If none found, try to find a form without specifying the type
        // Should we throw an error if none found?
        return optForm.orElseGet(() -> formRepository.findEffectiveFormByTypeAndInstitution(null, institution.getId()).orElse(null));
    }

    // --------- Answer creators (same mapping as in AbstractSingleEntity)

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
        } else if (answer instanceof CustomFieldAnswerSelectOneFromFieldCodeViewModel a) {
            try {
                return a.getValue().concept();
            } catch (NullPointerException e) {
                return null;
            }
        } else if (answer instanceof CustomFieldAnswerSelectOneActionUnitViewModel a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectOneSpatialUnitViewModel a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectOneActionCodeViewModel a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerIntegerViewModel a) {
            return a.getValue();
        }

        return null;
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

        if(answer instanceof CustomFieldAnswerStratigraphyViewModel stratiAnswer && jpaEntity instanceof RecordingUnitDTO ru) {
            // Special case
            handleStratigraphyRelationships(stratiAnswer, ru);
            return;
        }

        if (Boolean.TRUE.equals(field.getIsSystemField())
                && field.getValueBinding() != null
                && bindableFields.contains(field.getValueBinding())) {

            Object value = getFieldValue(jpaEntity, field.getValueBinding());
            populateSystemFieldValue(answer, value);
        }
    }




    private void populateSystemFieldValue(CustomFieldAnswerViewModel answer, Object value) {

        Map<Class<?>, BiConsumer<CustomFieldAnswerViewModel, Object>> handlers = new HashMap<>();
        handlers.put(OffsetDateTime.class, this::handleDateTime);
        handlers.put(String.class, this::handleString);
        handlers.put(Person.class, this::handlePerson);
        handlers.put(List.class, this::handlePersonList);
        handlers.put(Concept.class, this::handleConcept);
        handlers.put(ActionUnit.class, this::handleActionUnit);
        handlers.put(SpatialUnit.class, this::handleSpatialUnit);
        handlers.put(ActionCode.class, this::handleActionCode);
        handlers.put(Integer.class, this::handleInteger);
        handlers.put(Set.class, this::handleSpatialUnitSet);

        // Execute appropriate handler
        handlers.entrySet().stream()
                .filter(entry -> entry.getKey().isInstance(value))
                .findFirst()
                .ifPresent(entry -> entry.getValue().accept(answer, value));
    }

    // Méthodes dédiées pour chaque type de 'value'
    private void handleDateTime(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerDateTimeViewModel dateTimeAnswer) {
            dateTimeAnswer.setValue(((OffsetDateTime) value).toLocalDateTime());
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
            if (list.stream().allMatch(Person.class::isInstance)) {
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
            actionUnitAnswer.setValue((ActionUnitDTO) value);
        }
    }

    private void handleSpatialUnit(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectOneSpatialUnitViewModel spatialUnitAnswer) {
            spatialUnitAnswer.setValue((SpatialUnitDTO) value);
        }
    }

    private void handleActionCode(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectOneActionCodeViewModel actionCodeAnswer) {
            actionCodeAnswer.setValue((ActionCode) value);
        }
    }

    private void handleInteger(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerIntegerViewModel integerAnswer) {
            integerAnswer.setValue((Integer) value);
        }
    }
    @SuppressWarnings("unchecked")
    private void handleSpatialUnitSet(CustomFieldAnswerViewModel answer, Object value) {
        if (answer instanceof CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel treeAnswer) {
            treeAnswer.setValue((Set<SpatialUnitDTO>) value);
        }
    }

    private void handleStratigraphyRelationships(CustomFieldAnswerStratigraphyViewModel answer, RecordingUnitDTO unit) {
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

    private void setStratigraphyFieldValue(CustomFieldAnswerStratigraphyViewModel stratiAnswer, RecordingUnitDTO entity) {
        // Clear existing relationships to avoid duplicates
        entity.getRelationshipsAsUnit1().clear();
        entity.getRelationshipsAsUnit2().clear();

        // Helper method to add relationships to the correct set
        // TODO : check equality method for recordingunit and recordingunitsummary
        for (StratigraphicRelationshipDTO rel : stratiAnswer.getAnteriorRelationships()) {
            if (rel.getUnit1().equals(entity)) {
                entity.getRelationshipsAsUnit1().add(rel);
            } else if (rel.getUnit2().equals(entity)) {
                entity.getRelationshipsAsUnit2().add(rel);
            }
        }

        for (StratigraphicRelationshipDTO rel : stratiAnswer.getPosteriorRelationships()) {
            if (rel.getUnit1().equals(entity)) {
                entity.getRelationshipsAsUnit1().add(rel);
            } else if (rel.getUnit2().equals(entity)) {
                entity.getRelationshipsAsUnit2().add(rel);
            }
        }

        for (StratigraphicRelationshipDTO rel : stratiAnswer.getSynchronousRelationships()) {
            if (rel.getUnit1().equals(entity)) {
                entity.getRelationshipsAsUnit1().add(rel);
            } else if (rel.getUnit2().equals(entity)) {
                entity.getRelationshipsAsUnit2().add(rel);
            }
        }
    }



}

