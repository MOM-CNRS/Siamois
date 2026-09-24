package fr.siamois.domain.services.form;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.customfieldanswer.actionunit.CustomFieldAnswerActionCode;
import fr.siamois.domain.models.form.customfieldanswer.actionunit.CustomFieldAnswerActionUnit;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerDateTime;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerDecimal;
import fr.siamois.domain.models.form.customfieldanswer.person.CustomFieldAnswerSelectPerson;
import fr.siamois.domain.models.form.customfieldanswer.spatialunit.CustomFieldAnswerSpatialUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.ActionCodeDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionCodeRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.mapper.ActionCodeMapper;
import fr.siamois.mapper.ActionUnitSummaryMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.PlaceSuggestionMapper;
import fr.siamois.mapper.SpatialUnitMapper;
import fr.siamois.ui.viewmodel.fieldanswer.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.function.Supplier;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.measurement.CustomFieldAnswerMeasurement;
import fr.siamois.domain.models.form.customfieldanswer.vocabulary.CustomFieldAnswerSelectConcept;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.measurement.UnitDefinitionService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.MeasurementAnswerDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldAnswerRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.UnitDefinitionMapper;
import fr.siamois.ui.form.CustomFieldAnswerFactory;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerIntegerViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerMeasurementViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerSelectMultipleFromFieldCodeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerSelectOneFromFieldCodeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerTextViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomFieldAnswerService {

    /** Locale the app defaults to; matches the French-first UI. */
    private static final String DEFAULT_LANG = "fr";

    private final CustomFieldAnswerRepository customFieldAnswerRepository;
    private final TableFieldConfigService tableFieldConfigService;
    private final FormConfigAnswerService formConfigAnswerService;
    private final LabelService labelService;
    private final CustomFieldMeasurementService customFieldMeasurementService;
    private final UnitDefinitionService unitDefinitionService;
    private final UnitDefinitionMapper unitDefinitionMapper;
    private final ConceptMapper conceptMapper;
    private final ConceptRepository conceptRepository;
    private final PersonRepository personRepository;
    private final PersonMapper personMapper;
    private final SpatialUnitRepository spatialUnitRepository;
    private final SpatialUnitMapper spatialUnitMapper;
    private final PlaceSuggestionMapper placeSuggestionMapper;
    private final ActionUnitRepository actionUnitRepository;
    private final ActionUnitSummaryMapper actionUnitSummaryMapper;
    private final ActionCodeRepository actionCodeRepository;
    private final ActionCodeMapper actionCodeMapper;
    private final RecordingUnitRepository recordingUnitRepository;

    // ========== Owners ==========

    /**
     * The entity additional answers belong to — one of the four configurable tables. Everything the
     * save and load paths need differs only in these few lookups, so they are resolved once here.
     */
    private record AnswerOwner(String description,
                               Long projectId,
                               ConfigurableTable table,
                               Long typeConceptId,
                               Function<FormConfig, Optional<FormConfigAnswer>> find,
                               Function<FormConfig, FormConfigAnswer> createOrGet,
                               Supplier<List<FormConfigAnswer>> allSets,
                               Supplier<Collection<? extends CustomField>> extraActiveFields) {
    }

    private AnswerOwner ownerOf(RecordingUnitDTO dto) {
        if (dto == null || dto.getId() == null || dto.getActionUnit() == null) return null;
        return new AnswerOwner("recording unit " + dto.getId(), dto.getActionUnit().getId(), ConfigurableTable.UE,
                idOf(dto.getType()),
                config -> formConfigAnswerService.findFormConfigAnswer(config, dto),
                config -> formConfigAnswerService.createOrGetFormConfigAnswer(config, dto),
                () -> formConfigAnswerService.findAllFormConfigAnswers(dto),
                // measurement fields created on the fly from this unit's own form
                () -> customFieldMeasurementService.findByRecordingUnit(dto.getId()));
    }

    private AnswerOwner ownerOf(SpecimenDTO dto) {
        if (dto == null || dto.getId() == null) return null;
        Long projectId = specimenProjectId(dto);
        if (projectId == null) return null;
        // A find's form config is keyed by its category (Specimen.CAT_FIELD), not by its type.
        return new AnswerOwner("find " + dto.getId(), projectId, ConfigurableTable.MOBILIER,
                idOf(dto.getCategory()),
                config -> formConfigAnswerService.findFormConfigAnswer(config, dto),
                config -> formConfigAnswerService.createOrGetFormConfigAnswer(config, dto),
                () -> formConfigAnswerService.findAllFormConfigAnswers(dto),
                List::of);
    }

    private AnswerOwner ownerOf(PhaseDTO dto) {
        if (dto == null || dto.getId() == null || dto.getActionUnit() == null) return null;
        return new AnswerOwner("phase " + dto.getId(), dto.getActionUnit().getId(), ConfigurableTable.PHASE,
                idOf(dto.getType()),
                config -> formConfigAnswerService.findFormConfigAnswer(config, dto),
                config -> formConfigAnswerService.createOrGetFormConfigAnswer(config, dto),
                () -> formConfigAnswerService.findAllFormConfigAnswers(dto),
                List::of);
    }

    private AnswerOwner ownerOf(ContainerDTO dto) {
        if (dto == null || dto.getId() == null || dto.getActionUnit() == null) return null;
        return new AnswerOwner("container " + dto.getId(), dto.getActionUnit().getId(), ConfigurableTable.CONTENANT,
                idOf(dto.getType()),
                config -> formConfigAnswerService.findFormConfigAnswer(config, dto),
                config -> formConfigAnswerService.createOrGetFormConfigAnswer(config, dto),
                () -> formConfigAnswerService.findAllFormConfigAnswers(dto),
                List::of);
    }

    /** A find carries its project directly, or only through its recording unit (as SpecimenPanel resolves it). */
    private Long specimenProjectId(SpecimenDTO dto) {
        if (dto.getActionUnit() != null) return dto.getActionUnit().getId();
        if (dto.getRecordingUnit() == null || dto.getRecordingUnit().getId() == null) return null;
        return recordingUnitRepository.findById(dto.getRecordingUnit().getId())
                .map(RecordingUnit::getActionUnit)
                .map(ActionUnit::getId)
                .orElse(null);
    }

    private static Long idOf(ConceptDTO concept) {
        return concept != null ? concept.getId() : null;
    }

    // ========== Save ==========

    /**
     * Persists the answers to a recording unit's additional (non-system) fields.
     * <p>
     * Resolves the {@link FormConfig} of the unit's project/type, then re-checks every given
     * answer against the fields currently active on that type's form — an answer for a field
     * deactivated (or belonging to another type) since the form was loaded is silently dropped
     * rather than persisted. An answer left empty deletes the stored one.
     *
     * @param recordingUnitDTO the recording unit the answers belong to; must already be saved
     *                         (have an id), since the pivot row links to it
     * @param answers          the answers to persist, keyed by field
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveAdditionalFieldAnswers(RecordingUnitDTO recordingUnitDTO,
                                           Map<CustomField, CustomFieldAnswerViewModel> answers) {
        save(ownerOf(recordingUnitDTO), answers);
    }

    /** Same as {@link #saveAdditionalFieldAnswers(RecordingUnitDTO, Map)}, for a find. */
    @Transactional(rollbackFor = Exception.class)
    public void saveAdditionalFieldAnswers(SpecimenDTO specimenDTO, Map<CustomField, CustomFieldAnswerViewModel> answers) {
        save(ownerOf(specimenDTO), answers);
    }

    /** Same as {@link #saveAdditionalFieldAnswers(RecordingUnitDTO, Map)}, for a phase. */
    @Transactional(rollbackFor = Exception.class)
    public void saveAdditionalFieldAnswers(PhaseDTO phaseDTO, Map<CustomField, CustomFieldAnswerViewModel> answers) {
        save(ownerOf(phaseDTO), answers);
    }

    /** Same as {@link #saveAdditionalFieldAnswers(RecordingUnitDTO, Map)}, for a container. */
    @Transactional(rollbackFor = Exception.class)
    public void saveAdditionalFieldAnswers(ContainerDTO containerDTO, Map<CustomField, CustomFieldAnswerViewModel> answers) {
        save(ownerOf(containerDTO), answers);
    }

    private void save(AnswerOwner owner, Map<CustomField, CustomFieldAnswerViewModel> answers) {
        if (owner == null) {
            if (answers != null && !answers.isEmpty()) {
                log.warn("{} additional field answers given for an entity without id or project — nothing persisted", answers.size());
            }
            return;
        }
        // Every save of the entity, answers or not: a type change alone must move the answers too.
        reconcileTypeChange(owner);
        if (answers == null || answers.isEmpty()) {
            log.debug("No additional field answer given for {}", owner.description());
            return;
        }

        Set<CustomField> activeFields = activeFieldsOf(owner);
        Map<CustomField, CustomFieldAnswerViewModel> filteredAnswers = answers.entrySet().stream()
                .filter(entry -> activeFields.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (filteredAnswers.isEmpty()) {
            log.warn("None of the {} answers of {} is for a field active on type '{}' of project {}"
                            + " — nothing persisted. Answered fields: {}, active fields: {}",
                    answers.size(), owner.description(), owner.typeConceptId(), owner.projectId(),
                    fieldIdsOf(answers.keySet()), fieldIdsOf(activeFields));
            return;
        }
        log.trace("Persisting {} of the {} additional field answers of {}",
                filteredAnswers.size(), answers.size(), owner.description());

        // Materialized on demand: a field created straight from a unit's form gives the type answers
        // to store before anyone ever opened its settings screen, so the config may not exist yet.
        Optional<FormConfig> formConfig = tableFieldConfigService.createOrGetFormConfig(owner.projectId(), owner.table(), owner.typeConceptId());
        if (formConfig.isEmpty()) {
            log.warn("No form config for type '{}' on {}; additional field answers not persisted",
                    owner.typeConceptId(), owner.description());
            return;
        }

        FormConfigAnswer formConfigAnswer = owner.createOrGet().apply(formConfig.get());
        save(new CustomFormResponseViewModel(formConfigAnswer, filteredAnswers));
    }

    private Set<CustomField> activeFieldsOf(AnswerOwner owner) {
        Set<CustomField> activeFields = new HashSet<>(
                tableFieldConfigService.getActiveAdditionalFields(owner.projectId(), owner.table(), owner.typeConceptId()));
        activeFields.addAll(owner.extraActiveFields().get());
        return activeFields;
    }

    /**
     * Answers belong to the form config of the entity's type. When the type changed, the sets saved
     * under a former type's config are resolved here: the answers to fields the current type also
     * has are carried over to the current set (unless it already answers them — and the most recent
     * former set wins between two), then every former set is deleted, answers included.
     */
    private void reconcileTypeChange(AnswerOwner owner) {
        List<FormConfigAnswer> sets = owner.allSets().get();
        if (sets == null || sets.isEmpty()) return;

        Optional<FormConfig> current = tableFieldConfigService.findFormConfig(owner.projectId(), owner.table(), owner.typeConceptId());
        Long currentConfigId = current.map(FormConfig::getId).orElse(null);
        List<FormConfigAnswer> former = sets.stream()
                .filter(set -> set.getFormConfig() == null || !Objects.equals(set.getFormConfig().getId(), currentConfigId))
                .sorted(Comparator.comparing(FormConfigAnswer::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (former.isEmpty()) return;

        Set<CustomField> activeFields = activeFieldsOf(owner);
        Map<CustomField, CustomFieldAnswer> carried = new LinkedHashMap<>();
        for (FormConfigAnswer set : former) {
            for (CustomFieldAnswer answer : answersOf(set)) {
                CustomField field = answer.getCustomField();
                if (activeFields.contains(field) && !carried.containsKey(field)) {
                    carried.put(field, answer);
                }
            }
        }

        if (!carried.isEmpty()) {
            Optional<FormConfig> target = current.isPresent() ? current
                    : tableFieldConfigService.createOrGetFormConfig(owner.projectId(), owner.table(), owner.typeConceptId());
            if (target.isPresent()) {
                FormConfigAnswer currentSet = owner.createOrGet().apply(target.get());
                carried.forEach((field, answer) -> carryOver(currentSet, field, answer));
            }
        }

        for (FormConfigAnswer set : former) {
            customFieldAnswerRepository.deleteAll(answersOf(set));
            formConfigAnswerService.delete(set);
        }
        log.debug("{}: {} answer set(s) of a former type resolved, {} answer(s) carried over",
                owner.description(), former.size(), carried.size());
    }

    private static Set<CustomFieldAnswer> answersOf(FormConfigAnswer set) {
        return set.getAnswers() != null ? new HashSet<>(set.getAnswers()) : Set.of();
    }

    /** Copies a former type's answer into the current set — a new row, the answer's key includes its set. */
    private void carryOver(FormConfigAnswer currentSet, CustomField field, CustomFieldAnswer answer) {
        if (customFieldAnswerRepository.findByFormConfigAnswerAndCustomField(currentSet, field).isPresent()) return;
        Object value = answer.getValue();
        if (isEmptyValue(value) && !(answer instanceof CustomFieldAnswerMeasurement)) return;

        CustomFieldAnswer copy = answerEntityOf(field);
        if (copy == null) return;
        copy.setCustomField(field);
        copy.setFormConfigAnswer(currentSet);
        if (answer instanceof CustomFieldAnswerMeasurement measurement && copy instanceof CustomFieldAnswerMeasurement target) {
            target.setValue(measurement.getValue());
            target.setComment(measurement.getComment());
            target.setUnit(measurement.getUnit());
        } else {
            copy.setValue(value instanceof Collection<?> collection ? new ArrayList<>(collection) : value);
        }
        customFieldAnswerRepository.save(copy);
    }

    // ========== Load ==========

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
        return load(ownerOf(recordingUnitDTO));
    }

    @Transactional(readOnly = true)
    public Map<CustomField, CustomFieldAnswerViewModel> loadAdditionalFieldAnswers(SpecimenDTO specimenDTO) {
        return load(ownerOf(specimenDTO));
    }

    @Transactional(readOnly = true)
    public Map<CustomField, CustomFieldAnswerViewModel> loadAdditionalFieldAnswers(PhaseDTO phaseDTO) {
        return load(ownerOf(phaseDTO));
    }

    @Transactional(readOnly = true)
    public Map<CustomField, CustomFieldAnswerViewModel> loadAdditionalFieldAnswers(ContainerDTO containerDTO) {
        return load(ownerOf(containerDTO));
    }

    /** The entities a list can show additional-field answers for. */
    public enum ListOwner { RECORDING_UNIT, SPECIMEN, PHASE, CONTAINER }

    /**
     * The answers of a whole page of entities to some of their additional fields, in one query —
     * what a list's dynamic columns show. Keyed by entity id, then field. Should an entity still
     * hold answer sets from a former type (they are merged into the current one on its next save),
     * the most recent set wins.
     */
    @Transactional(readOnly = true)
    public Map<Long, Map<CustomField, CustomFieldAnswerViewModel>> loadAdditionalFieldAnswers(
            ListOwner owner, Collection<Long> ownerIds, Collection<Long> fieldIds) {
        if (ownerIds == null || ownerIds.isEmpty() || fieldIds == null || fieldIds.isEmpty()) return Map.of();
        List<CustomFieldAnswer> answers = switch (owner) {
            case RECORDING_UNIT -> customFieldAnswerRepository.findAnswersOfRecordingUnits(ownerIds, fieldIds);
            case SPECIMEN -> customFieldAnswerRepository.findAnswersOfSpecimens(ownerIds, fieldIds);
            case PHASE -> customFieldAnswerRepository.findAnswersOfPhases(ownerIds, fieldIds);
            case CONTAINER -> customFieldAnswerRepository.findAnswersOfContainers(ownerIds, fieldIds);
        };
        Map<Long, Map<CustomField, CustomFieldAnswerViewModel>> result = new HashMap<>();
        answers.stream()
                .sorted(Comparator.comparing((CustomFieldAnswer a) -> a.getFormConfigAnswer().getId()))
                .forEach(answer -> {
                    Long ownerId = ownerIdOf(owner, answer.getFormConfigAnswer());
                    CustomFieldAnswerViewModel viewModel = toViewModel(answer.getCustomField(), answer);
                    if (ownerId != null && viewModel != null) {
                        result.computeIfAbsent(ownerId, k -> new HashMap<>()).put(answer.getCustomField(), viewModel);
                    }
                });
        return result;
    }

    private static Long ownerIdOf(ListOwner owner, FormConfigAnswer set) {
        Object entity = switch (owner) {
            case RECORDING_UNIT -> set.getRecordingUnit();
            case SPECIMEN -> set.getSpecimen();
            case PHASE -> set.getPhase();
            case CONTAINER -> set.getContainer();
        };
        if (entity instanceof fr.siamois.domain.models.recordingunit.RecordingUnit ru) return ru.getId();
        if (entity instanceof fr.siamois.domain.models.specimen.Specimen sp) return sp.getId();
        if (entity instanceof fr.siamois.domain.models.phase.Phase ph) return ph.getId();
        if (entity instanceof fr.siamois.domain.models.container.Container c) return c.getId();
        return null;
    }

    private Map<CustomField, CustomFieldAnswerViewModel> load(AnswerOwner owner) {
        if (owner == null) return Map.of();

        Optional<FormConfig> formConfig = tableFieldConfigService.findFormConfig(owner.projectId(), owner.table(), owner.typeConceptId());
        if (formConfig.isEmpty()) return Map.of();

        Set<CustomFieldAnswer> stored = owner.find().apply(formConfig.get())
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
        } else if (viewModel instanceof CustomFieldAnswerDecimalViewModel v && value instanceof Number n) {
            v.setValue(n.doubleValue());
        } else if (viewModel instanceof CustomFieldAnswerDateTimeViewModel v && value instanceof LocalDateTime d) {
            v.setValue(d);
        } else if (viewModel instanceof CustomFieldAnswerMeasurementViewModel v
                && answer instanceof CustomFieldAnswerMeasurement stored) {
            v.setValue(MeasurementAnswerDTO.builder()
                    .numericValue(stored.getValue())
                    .comment(stored.getComment())
                    .unit(unitDefinitionMapper.convert(stored.getUnit()))
                    .build());
        } else if (viewModel instanceof CustomFieldAnswerSelectOneFromFieldCodeViewModel v
                && answer instanceof CustomFieldAnswerSelectConcept stored) {
            v.setValue(storedConcepts(stored).stream().findFirst().orElse(null));
        } else if (viewModel instanceof CustomFieldAnswerSelectMultipleFromFieldCodeViewModel v
                && answer instanceof CustomFieldAnswerSelectConcept stored) {
            v.setValue(storedConcepts(stored));
        } else if (viewModel instanceof CustomFieldAnswerSelectOnePersonViewModel v) {
            v.setValue(storedEntities(value, Person.class).stream().map(personMapper::convert).findFirst().orElse(null));
        } else if (viewModel instanceof CustomFieldAnswerSelectMultiplePersonViewModel v) {
            v.setValue(storedEntities(value, Person.class).stream().map(personMapper::convert)
                    .collect(Collectors.toCollection(ArrayList::new)));
        } else if (viewModel instanceof CustomFieldAnswerSelectOneSpatialUnitViewModel v) {
            v.setValue(storedEntities(value, SpatialUnit.class).stream().map(this::toPlaceSuggestion).findFirst().orElse(null));
        } else if (viewModel instanceof CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel v) {
            v.setValue(storedEntities(value, SpatialUnit.class).stream().map(this::toPlaceSuggestion)
                    .collect(Collectors.toCollection(ArrayList::new)));
        } else if (viewModel instanceof CustomFieldAnswerSelectOneActionUnitViewModel v) {
            v.setValue(storedEntities(value, ActionUnit.class).stream().map(actionUnitSummaryMapper::convert).findFirst().orElse(null));
        } else if (viewModel instanceof CustomFieldAnswerSelectOneActionCodeViewModel v) {
            v.setValue(storedEntities(value, ActionCode.class).stream().map(actionCodeMapper::convert).findFirst().orElse(null));
        } else {
            return null;
        }

        viewModel.setHasBeenModified(false);
        return viewModel;
    }

    private PlaceSuggestionDTO toPlaceSuggestion(SpatialUnit spatialUnit) {
        return placeSuggestionMapper.convert(spatialUnitMapper.convert(spatialUnit));
    }

    /** A stored reference answer's value is one entity or a list of them, depending on the answer's cardinality. */
    private static <E> List<E> storedEntities(Object value, Class<E> type) {
        if (type.isInstance(value)) return List.of(type.cast(value));
        if (value instanceof Collection<?> collection) {
            return collection.stream().filter(type::isInstance).map(type::cast).toList();
        }
        return List.of();
    }

    /**
     * Rebuilds the autocomplete DTOs a vocabulary field's components display from the concepts
     * linked to its stored answer. The returned list is mutable: the multi-value field appends to it
     * as the user picks further concepts.
     */
    private List<ConceptAutocompleteDTO> storedConcepts(CustomFieldAnswerSelectConcept stored) {
        Object value = stored.getValue();
        List<Concept> concepts;
        if (value instanceof Concept concept) {
            concepts = List.of(concept);
        } else if (value instanceof Collection<?> collection) {
            concepts = collection.stream()
                    .filter(Concept.class::isInstance)
                    .map(Concept.class::cast)
                    .toList();
        } else {
            return new ArrayList<>();
        }

        String lang = currentLang();
        return concepts.stream()
                .map(concept -> toAutocompleteDTO(concept, lang))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ConceptAutocompleteDTO toAutocompleteDTO(Concept concept, String lang) {
        return new ConceptAutocompleteDTO(
                conceptMapper.convert(concept),
                labelService.findLabelOf(concept, lang).getLabel(),
                lang);
    }

    private String currentLang() {
        UserInfo info = ExecutionContextHolder.get();
        return info != null && info.getLang() != null ? info.getLang() : DEFAULT_LANG;
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
            if (answer == null) {
                log.warn("Field {} ({}) of type {} cannot be stored as an additional answer — skipped",
                        customField.getId(), customField.getLabel(), Hibernate.getClass(customField).getSimpleName());
                return;
            }
            answer.setCustomField(customField);
            answer.setFormConfigAnswer(formConfigAnswer);
        }

        if (answer instanceof CustomFieldAnswerMeasurement measurementAnswer
                && customFieldAnswerViewModel instanceof CustomFieldAnswerMeasurementViewModel measurementViewModel) {
            createOrUpdateMeasurementAnswer(measurementAnswer, measurementViewModel, customField, optAnswer.isPresent());
            return;
        }

        Object value = entityValueOf(answer, customFieldAnswerViewModel);
        if (isEmptyValue(value)) {
            // Emptied field: the stored answer goes; an answer never stored stays unmaterialized.
            optAnswer.ifPresent(customFieldAnswerRepository::delete);
            return;
        }
        answer.setValue(value);
        customFieldAnswerRepository.save(answer);
    }

    /**
     * Converts what a view model holds into what the answer entity stores. Reference answers link
     * JPA entities, while the form components (and the REST coercion) hand DTOs — so they are
     * re-read from the database by id, keeping the picked order. Scalars pass through, except a
     * date-time that arrives offset-qualified or as a bare date.
     */
    private Object entityValueOf(CustomFieldAnswer answer, CustomFieldAnswerViewModel viewModel) {
        Object raw = viewModel.getValue();
        boolean multiple = raw instanceof Collection<?>;
        if (answer instanceof CustomFieldAnswerSelectConcept) {
            List<Concept> concepts = pickedConcepts(viewModel);
            return concepts.isEmpty() ? null : new ArrayList<>(concepts);
        }
        if (answer instanceof CustomFieldAnswerSelectPerson) {
            return shaped(references(raw, Person.class, CustomFieldAnswerService::idOfEntity, keys -> personRepository.findAllById(keys), Person::getId), multiple);
        }
        if (answer instanceof CustomFieldAnswerSpatialUnit) {
            return shaped(references(raw, SpatialUnit.class, CustomFieldAnswerService::idOfSpatialUnit, keys -> spatialUnitRepository.findAllById(keys), SpatialUnit::getId), multiple);
        }
        if (answer instanceof CustomFieldAnswerActionUnit) {
            return shaped(references(raw, ActionUnit.class, CustomFieldAnswerService::idOfEntity, keys -> actionUnitRepository.findAllById(keys), ActionUnit::getId), multiple);
        }
        if (answer instanceof CustomFieldAnswerActionCode) {
            return shaped(references(raw, ActionCode.class, CustomFieldAnswerService::codeOfActionCode, keys -> actionCodeRepository.findAllById(keys), ActionCode::getCode), multiple);
        }
        if (answer instanceof CustomFieldAnswerDateTime) {
            if (raw instanceof OffsetDateTime offset) return offset.toLocalDateTime();
            if (raw instanceof LocalDate date) return date.atStartOfDay();
        }
        if (answer instanceof CustomFieldAnswerDecimal && raw instanceof Number number) {
            return number.doubleValue();
        }
        return raw;
    }

    private static boolean isEmptyValue(Object value) {
        return value == null
                || (value instanceof String s && s.isBlank())
                || (value instanceof Collection<?> c && c.isEmpty());
    }

    /** Single-value answer entities take the entity itself, multi-value ones the list. */
    private static <E> Object shaped(List<E> entities, boolean multiple) {
        if (multiple) return entities.isEmpty() ? null : new ArrayList<>(entities);
        return entities.isEmpty() ? null : entities.get(0);
    }

    /**
     * The entities a reference answer's view model points at, in the picked order. Values that
     * already are entities are kept as they are; DTOs are re-read from the database by key.
     */
    private static <E, K> List<E> references(Object raw,
                                             Class<E> entityClass,
                                             Function<Object, K> keyOf,
                                             Function<List<K>, Iterable<E>> loadAll,
                                             Function<E, K> keyOfEntity) {
        List<Object> picked = raw == null ? List.of()
                : raw instanceof Collection<?> collection ? new ArrayList<>(collection) : List.of(raw);

        Map<K, E> resolved = new HashMap<>();
        List<K> keys = new ArrayList<>();
        List<K> toLoad = new ArrayList<>();
        for (Object value : picked) {
            K key = entityClass.isInstance(value) ? keyOfEntity.apply(entityClass.cast(value)) : keyOf.apply(value);
            if (key == null || keys.contains(key)) continue;
            keys.add(key);
            if (entityClass.isInstance(value)) {
                resolved.put(key, entityClass.cast(value));
            } else {
                toLoad.add(key);
            }
        }
        if (!toLoad.isEmpty()) {
            loadAll.apply(toLoad).forEach(entity -> resolved.put(keyOfEntity.apply(entity), entity));
        }
        return keys.stream().map(resolved::get).filter(Objects::nonNull).toList();
    }

    private static Long idOfEntity(Object picked) {
        if (picked instanceof AbstractEntityDTO dto) return dto.getId();
        if (picked instanceof Person person) return person.getId();
        if (picked instanceof ActionUnit actionUnit) return actionUnit.getId();
        if (picked instanceof Number number) return number.longValue();
        return null;
    }

    private static Long idOfSpatialUnit(Object picked) {
        if (picked instanceof PlaceSuggestionDTO suggestion) return suggestion.getId();
        if (picked instanceof SpatialUnitSummaryDTO summary) return summary.getId();
        if (picked instanceof SpatialUnit spatialUnit) return spatialUnit.getId();
        return idOfEntity(picked);
    }

    private static String codeOfActionCode(Object picked) {
        if (picked instanceof ActionCodeDTO dto) return dto.getCode();
        if (picked instanceof ActionCode code) return code.getCode();
        return picked instanceof String code ? code : null;
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

    /**
     * Persists the concept(s) picked on an additional vocabulary field. The view model holds
     * {@link ConceptAutocompleteDTO}s (what the autocomplete produces) while the answer entity links
     * {@link Concept} rows, so the picked concepts are re-read from the database by id — they always
     * exist there already, the autocomplete only ever suggests locally stored concepts.
     * <p>
     * An answer never stored and left empty is not created, mirroring the measurement path: an
     * untouched field shouldn't materialize a row.
     */
    private void createOrUpdateConceptAnswer(CustomFieldAnswerSelectConcept answer,
                                             CustomFieldAnswerViewModel viewModel,
                                             boolean alreadyStored) {
        List<Concept> concepts = pickedConcepts(viewModel);
        if (!alreadyStored && concepts.isEmpty()) {
            return;
        }

        answer.setValue(new ArrayList<>(concepts));
        customFieldAnswerRepository.save(answer);
    }

    /**
     * The concepts a vocabulary answer view model currently holds, as {@link Concept} entities.
     * <p>
     * The concept components hand the view model detached {@link ConceptAutocompleteDTO}s, so those
     * are re-read from the database by id — the autocomplete only ever suggests locally stored
     * concepts, so they are always found. Values that already are entities are kept as they are.
     */
    private List<Concept> pickedConcepts(CustomFieldAnswerViewModel viewModel) {
        List<Object> picked = pickedValues(viewModel);

        List<Long> detachedIds = picked.stream()
                .map(CustomFieldAnswerService::idOfDetachedConcept)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Concept> loaded = new HashMap<>();
        if (!detachedIds.isEmpty()) {
            conceptRepository.findAllById(detachedIds).forEach(concept -> loaded.put(concept.getId(), concept));
        }

        // built by iterating the picked values so the stored order is the one the user picked
        List<Concept> concepts = new ArrayList<>();
        for (Object value : picked) {
            Concept concept = value instanceof Concept alreadyAnEntity
                    ? alreadyAnEntity
                    : loaded.get(idOfDetachedConcept(value));
            if (concept != null) {
                concepts.add(concept);
            }
        }
        return concepts;
    }

    private static List<Object> pickedValues(CustomFieldAnswerViewModel viewModel) {
        Object value = viewModel.getValue();
        if (value == null) {
            return List.of();
        }
        return value instanceof Collection<?> collection ? new ArrayList<>(collection) : List.of(value);
    }

    private static Long idOfDetachedConcept(Object picked) {
        if (picked instanceof ConceptAutocompleteDTO autocompleteDTO) {
            return autocompleteDTO.concept() == null ? null : autocompleteDTO.concept().getId();
        }
        if (picked instanceof ConceptDTO conceptDTO) {
            return conceptDTO.getId();
        }
        return null;
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
        Class<?> fieldClass = Hibernate.getClass(field);
        Function<Void, ? extends CustomFieldAnswer> creator =
                CustomFieldAnswerFactory.ANSWER_ENTITY_CREATORS.get(fieldClass);
        return creator == null ? null : creator.apply(null);
    }

}
