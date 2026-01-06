package fr.siamois.domain.services.form;


import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.customfieldanswer.*;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.EnabledWhenJson;
import fr.siamois.domain.models.form.customform.ValueMatcher;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.form.FormRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.bean.LabelBean;
import fr.siamois.ui.form.CustomFieldAnswerFactory;
import fr.siamois.ui.form.EnabledRulesEngine;
import fr.siamois.ui.form.FieldSource;
import fr.siamois.ui.form.rules.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
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
    public CustomForm findCustomFormByRecordingUnitTypeAndInstitutionId(Concept recordingUnitType, Institution institution) {
        Optional<CustomForm> optForm = formRepository.findEffectiveFormByTypeAndInstitution(recordingUnitType.getId(), institution.getId());
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
    public CustomFormResponse initOrReuseResponse(CustomFormResponse existing,
                                                  Object jpaEntity,
                                                  FieldSource fieldSource,
                                                  boolean forceInit) {

        CustomFormResponse response;
        Map<CustomField, CustomFieldAnswer> answers;

        if (existing != null && !forceInit) {
            response = existing;
            answers = response.getAnswers();
            if (answers == null) {
                answers = new HashMap<>();
            }
        } else {
            response = new CustomFormResponse();
            answers = new HashMap<>();
        }

        boolean onlyInitMissing = (existing != null && !forceInit);
        List<String> bindableFields = getBindableFieldNames(jpaEntity);

        for (CustomField field : fieldSource.getAllFields()) {
            if (field == null) continue;

            if (onlyInitMissing && answers.containsKey(field)) {
                continue;
            }

            CustomFieldAnswer answer = CustomFieldAnswerFactory.instantiateAnswerForField(field);
            if (answer == null) continue;

            initializeAnswer(answer, field, jpaEntity, bindableFields);
            answers.put(field, answer);
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
        JsonNode node = vj.getValue();

        return switch (className) {
            case "fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectOneFromFieldCode" ->
                    new ValueMatcher() {
                        @Override
                        public boolean matches(CustomFieldAnswer cur) {
                            if (!(cur instanceof CustomFieldAnswerSelectOneFromFieldCode a)) return false;
                            // compare by external ids (vocabularyExtId + conceptExtId) in node
                            String ev = node != null && node.has("vocabularyExtId")
                                    ? node.get("vocabularyExtId").asText(null) : null;
                            String ec = node != null && node.has("conceptExtId")
                                    ? node.get("conceptExtId").asText(null) : null;

                            Concept val = a.getValue();
                            if (val == null) {
                                return ev == null && ec == null;
                            }
                            return Objects.equals(val.getVocabulary().getExternalVocabularyId(), ev)
                                    && Objects.equals(val.getExternalId(), ec);
                        }

                        @Override
                        public Class<?> expectedAnswerClass() {
                            return CustomFieldAnswerSelectOneFromFieldCode.class;
                        }
                    };
            // other cases can be added as you support more types
            default -> new ValueMatcher() {
                @Override
                public boolean matches(CustomFieldAnswer cur) {
                    return false; // unknown -> never equal
                }

                @Override
                public Class<?> expectedAnswerClass() {
                    return Object.class;
                }
            };
        };
    }

    // -------------- Entity <-> answer binding

    /**
     * Apply all bindable system fields from the response back into the JPA entity.
     * This is basically your previous updateJpaEntityFromFormResponse method.
     */
    public void updateJpaEntityFromResponse(CustomFormResponse response, Object jpaEntity) {
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

    private static boolean isBindableSystemField(CustomField field,
                                                 CustomFieldAnswer answer,
                                                 List<String> bindableFields) {
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
        } else if (answer instanceof CustomFieldAnswerSelectOnePerson a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectOneFromFieldCode a) {
            try {
                return a.getUiVal().concept();
            } catch (NullPointerException e) {
                return null;
            }
        } else if (answer instanceof CustomFieldAnswerSelectOneActionUnit a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectOneSpatialUnit a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectMultipleSpatialUnitTree a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerSelectOneActionCode a) {
            return a.getValue();
        } else if (answer instanceof CustomFieldAnswerInteger a) {
            return a.getValue();
        }

        return null;
    }

    // -------------- Internal helpers

    private void initializeAnswer(CustomFieldAnswer answer,
                                  CustomField field,
                                  Object jpaEntity,
                                  List<String> bindableFields) {

        CustomFieldAnswerId answerId = new CustomFieldAnswerId();
        answerId.setField(field);
        answer.setPk(answerId);
        answer.setHasBeenModified(false);

        if (Boolean.TRUE.equals(field.getIsSystemField())
                && field.getValueBinding() != null
                && bindableFields.contains(field.getValueBinding())) {

            Object value = getFieldValue(jpaEntity, field.getValueBinding());
            populateSystemFieldValue(answer, field, value);
        }
    }

    /**
     * Initialization for system fields from the entity value.
     * This is basically your previous populateSystemFieldValue,
     * but without any UI-specific state (no treeStates here).
     */
    @SuppressWarnings("unchecked")
    private void populateSystemFieldValue(CustomFieldAnswer answer,
                                          CustomField field,
                                          Object value) {

        if (value instanceof OffsetDateTime odt && answer instanceof CustomFieldAnswerDateTime dateTimeAnswer) {
            dateTimeAnswer.setValue(odt.toLocalDateTime());
        } else if (value instanceof String str && answer instanceof CustomFieldAnswerText textAnswer) {
            textAnswer.setValue(str);
        } else if (value instanceof Person p && answer instanceof CustomFieldAnswerSelectOnePerson singlePersonAnswer) {
            singlePersonAnswer.setValue(p);
        } else if (value instanceof List<?> list
                && answer instanceof CustomFieldAnswerSelectMultiplePerson multiplePersonAnswer
                && list.stream().allMatch(Person.class::isInstance)) {
            multiplePersonAnswer.setValue((List<Person>) list);
        } else if (value instanceof Concept c) {
            if (answer instanceof CustomFieldAnswerSelectOneFromFieldCode codeAnswer) {
                codeAnswer.setValue(c);
                codeAnswer.setUiVal(new ConceptAutocompleteDTO(
                        c,
                        labelBean.findLabelOf(c),
                        labelBean.getCurrentUserLang()
                ));
            }
        } else if (value instanceof ActionUnit a && answer instanceof CustomFieldAnswerSelectOneActionUnit actionUnitAnswer) {
            actionUnitAnswer.setValue(a);
        } else if (value instanceof SpatialUnit s && answer instanceof CustomFieldAnswerSelectOneSpatialUnit spatialUnitAnswer) {
            spatialUnitAnswer.setValue(s);
        } else if (value instanceof ActionCode code && answer instanceof CustomFieldAnswerSelectOneActionCode actionCodeAnswer) {
            actionCodeAnswer.setValue(code);
        } else if (value instanceof Integer val && answer instanceof CustomFieldAnswerInteger integerAnswer) {
            integerAnswer.setValue(val);
        } else if (value instanceof Set<?> set && answer instanceof CustomFieldAnswerSelectMultipleSpatialUnitTree treeAnswer) {
            treeAnswer.setValue((Set<SpatialUnit>) set);
            // NOTE: UI (TreeUiStateViewModel) should be built in the bean/context, not here.
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


}

