package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.measurement.CustomFieldAnswerMeasurement;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.measurement.UnitDefinitionService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.MeasurementAnswerDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldAnswerRepository;
import fr.siamois.mapper.UnitDefinitionMapper;
import fr.siamois.ui.form.CustomFieldAnswerFactory;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerIntegerViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerMeasurementViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerTextViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomFieldAnswerService {

    private final CustomFieldAnswerRepository customFieldAnswerRepository;
    private final TableFieldConfigService tableFieldConfigService;
    private final FormConfigAnswerService formConfigAnswerService;
    private final LabelService labelService;
    private final CustomFieldMeasurementService customFieldMeasurementService;
    private final UnitDefinitionService unitDefinitionService;
    private final UnitDefinitionMapper unitDefinitionMapper;

    /**
     * Persists the answers to a recording unit's additional (non-system) fields.
     * <p>
     * Resolves the {@link FormConfig} of the unit's project/type, then re-checks every given
     * answer against the fields currently active on that type's form — an answer for a field
     * deactivated (or belonging to another type) since the form was loaded is silently dropped
     * rather than persisted.
     *
     * @param recordingUnitDTO the recording unit the answers belong to; must already be saved
     *                         (have an id), since the pivot row links to it
     * @param answers          the answers to persist, keyed by field
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveAdditionalFieldAnswers(RecordingUnitDTO recordingUnitDTO,
                                           Map<CustomField, CustomFieldAnswerViewModel> answers) {
        if (answers == null || answers.isEmpty()) {
            log.debug("No additional field answer given for recording unit {}", recordingUnitDTO.getId());
            return;
        }

        Long projectId = recordingUnitDTO.getActionUnit().getId();
        Long typeConceptId = recordingUnitDTO.getType() != null ? recordingUnitDTO.getType().getId() : null;

        Set<CustomField> activeFields = new HashSet<>(
                tableFieldConfigService.getActiveAdditionalFields(projectId, ConfigurableTable.UE, typeConceptId));
        activeFields.addAll(customFieldMeasurementService.findByRecordingUnit(recordingUnitDTO.getId()));
        Map<CustomField, CustomFieldAnswerViewModel> filteredAnswers = answers.entrySet().stream()
                .filter(entry -> activeFields.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (filteredAnswers.isEmpty()) {
            log.warn("None of the {} answers of recording unit {} is for a field active on type '{}' of project {}"
                            + " — nothing persisted. Answered fields: {}, active fields: {}",
                    answers.size(), recordingUnitDTO.getId(), typeConceptId, projectId,
                    fieldIdsOf(answers.keySet()), fieldIdsOf(activeFields));
            return;
        }
        log.trace("Persisting {} of the {} additional field answers of recording unit {}",
                filteredAnswers.size(), answers.size(), recordingUnitDTO.getId());

        // Materialized on demand: a field created straight from a unit's form gives the type answers
        // to store before anyone ever opened its settings screen, so the config may not exist yet.
        Optional<FormConfig> formConfig = tableFieldConfigService.createOrGetFormConfig(projectId, ConfigurableTable.UE, typeConceptId);
        if (formConfig.isEmpty()) {
            log.warn("No form config for type '{}' on recording unit {}; additional field answers not persisted",
                    typeConceptId, recordingUnitDTO.getId());
            return;
        }

        FormConfigAnswer formConfigAnswer = formConfigAnswerService.createOrGetFormConfigAnswer(formConfig.get(), recordingUnitDTO);
        save(new CustomFormResponseViewModel(formConfigAnswer, filteredAnswers));
    }

    /**
     * Loads a recording unit's previously saved additional-field answers, ready to drop straight
     * into a {@code CustomFormResponseViewModel}'s answers, so the form shows them again instead of
     * appearing empty on reopen. Read-only: unlike the save path, this never materializes a
     * {@link FormConfig} or {@link FormConfigAnswer}.
     *
     * @param recordingUnitDTO the recording unit to load answers for
     * @return a view model per additional field that has a saved answer of a supported type (never
     * null); fields of a type {@link CustomFieldAnswerFactory#ANSWER_ENTITY_CREATORS} can't persist
     * have none saved in the first place, so they're simply absent here
     */
    @Transactional(readOnly = true)
    public Map<CustomField, CustomFieldAnswerViewModel> loadAdditionalFieldAnswers(RecordingUnitDTO recordingUnitDTO) {
        if (recordingUnitDTO == null || recordingUnitDTO.getId() == null || recordingUnitDTO.getActionUnit() == null) {
            return Map.of();
        }

        Long projectId = recordingUnitDTO.getActionUnit().getId();
        Long typeConceptId = recordingUnitDTO.getType() != null ? recordingUnitDTO.getType().getId() : null;

        Optional<FormConfig> formConfig = tableFieldConfigService.findFormConfig(projectId, ConfigurableTable.UE, typeConceptId);
        if (formConfig.isEmpty()) return Map.of();

        Set<CustomFieldAnswer> stored = formConfigAnswerService.findFormConfigAnswer(formConfig.get(), recordingUnitDTO)
                .map(FormConfigAnswer::getAnswers)
                .orElse(Set.of());

        Map<CustomField, CustomFieldAnswerViewModel> result = new HashMap<>();
        for (CustomFieldAnswer answer : stored) {
            CustomFieldAnswerViewModel viewModel = toViewModel(answer.getCustomField(), answer);
            if (viewModel != null) {
                result.put(answer.getCustomField(), viewModel);
            }
        }
        return result;
    }

    private CustomFieldAnswerViewModel toViewModel(CustomField field, CustomFieldAnswer answer) {
        CustomFieldAnswerViewModel viewModel = CustomFieldAnswerFactory.instantiateAnswerForField(field);
        if (viewModel == null) return null;

        Object value = answer.getValue();
        if (viewModel instanceof CustomFieldAnswerTextViewModel v && value instanceof String s) {
            v.setValue(s);
        } else if (viewModel instanceof CustomFieldAnswerIntegerViewModel v && value instanceof Integer i) {
            v.setValue(i);
        } else if (viewModel instanceof CustomFieldAnswerMeasurementViewModel v
                && answer instanceof CustomFieldAnswerMeasurement stored) {
            v.setValue(MeasurementAnswerDTO.builder()
                    .numericValue(stored.getValue())
                    .comment(stored.getComment())
                    .unit(unitDefinitionMapper.convert(stored.getUnit()))
                    .build());
        } else {
            return null;
        }

        viewModel.setHasBeenModified(false);
        return viewModel;
    }

    private static String fieldIdsOf(Collection<CustomField> fields) {
        return fields.stream()
                .map(field -> field.getId() + " (" + field.getLabel() + ")")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private void createOrUpdateAnswer(FormConfigAnswer formConfigAnswer, CustomField customField, CustomFieldAnswerViewModel customFieldAnswerViewModel) {
        Optional<CustomFieldAnswer> optAnswer = customFieldAnswerRepository.findByFormConfigAnswerAndCustomField(formConfigAnswer, customField);
        CustomFieldAnswer answer;
        if(optAnswer.isPresent()) {
            answer = optAnswer.get();
        } else {
            answer = answerEntityOf(customField);
            answer.setCustomField(customField);
            answer.setFormConfigAnswer(formConfigAnswer);
        }

        if (answer instanceof CustomFieldAnswerMeasurement measurementAnswer
                && customFieldAnswerViewModel instanceof CustomFieldAnswerMeasurementViewModel measurementViewModel) {
            createOrUpdateMeasurementAnswer(measurementAnswer, measurementViewModel, customField, optAnswer.isPresent());
            return;
        }

        if(customFieldAnswerViewModel.getValue() != null) {
            answer.setValue(customFieldAnswerViewModel.getValue());
            customFieldAnswerRepository.save(answer);
        }

    }

    private void createOrUpdateMeasurementAnswer(CustomFieldAnswerMeasurement answer,
                                                 CustomFieldAnswerMeasurementViewModel viewModel,
                                                 CustomField customField,
                                                 boolean alreadyStored) {
        MeasurementAnswerDTO value = viewModel.getValue();
        Double numericValue = value != null ? value.getNumericValue() : null;
        String comment = value != null ? value.getComment() : null;

        if (!alreadyStored && numericValue == null && (comment == null || comment.isBlank())) {
            return;
        }

        answer.setValue(numericValue);
        answer.setComment(comment);
        answer.setUnit(unitOf(value, customField));
        customFieldAnswerRepository.save(answer);
    }

    private UnitDefinition unitOf(MeasurementAnswerDTO value, CustomField customField) {
        Long answerUnitId = value != null && value.getUnit() != null ? value.getUnit().getId() : null;
        Long unitId = answerUnitId != null ? answerUnitId : fieldUnitId(customField);

        return unitDefinitionService.resolveById(unitId);
    }

    private Long fieldUnitId(CustomField customField) {
        if (Hibernate.unproxy(customField) instanceof CustomFieldMeasurement measurementField
                && measurementField.getUnit() != null) {
            return measurementField.getUnit().getId();
        }
        return null;
    }

    public void save(@NonNull CustomFormResponseViewModel response) {
        for (Map.Entry<CustomField, CustomFieldAnswerViewModel> fieldAnswer : response.getAnswers().entrySet()) {
            CustomField field = fieldAnswer.getKey();
            CustomFieldAnswerViewModel answer = fieldAnswer.getValue();
            createOrUpdateAnswer(response.getFormConfig(), field, answer);
        }
    }

    private CustomFieldAnswer answerEntityOf(@NonNull CustomField field) {
        return CustomFieldAnswerFactory.ANSWER_ENTITY_CREATORS.get(Hibernate.getClass(field)).apply(null);
    }

}
