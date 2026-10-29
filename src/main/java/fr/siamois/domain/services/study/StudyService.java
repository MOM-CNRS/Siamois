package fr.siamois.domain.services.study;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.study.Study;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.identifier.EntityIdentifierGenerator;
import fr.siamois.domain.services.identifier.IdentifierGenerationSpec;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.StudyRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.infrastructure.database.repositories.specs.StudySpec;
import fr.siamois.infrastructure.database.repositories.specs.TextSearchSpec;
import fr.siamois.infrastructure.database.repositories.specs.ExactMatchFirst;
import fr.siamois.mapper.StudyMapper;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Service for managing studies (and treatments): searching, saving with their links to recording units
 * and finds, and numbering them from the identifier format of their type.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StudyService {

    private final fr.siamois.domain.services.EntityLinkGuard entityLinkGuard;

    private final StudyRepository studyRepository;
    private final RecordingUnitRepository recordingUnitRepository;
    private final SpecimenRepository specimenRepository;
    private final CustomFieldAnswerService customFieldAnswerService;
    private final StudyMapper studyMapper;
    private final EntityIdentifierGenerator identifierGenerator;
    private final ProfilePermissionService profilePermissionService;

    private Specification<Study> userFilterSpecs(FilterDTO filters) {
        // The list's per-field sort/filters (FieldQuery), then the named ones.
        Specification<Study> specs = filters.getFieldQuery().specificationFor();

        FilterDTO.FilterInfo globalFilter = filters.filterOf(ActionUnitSpec.GLOBAL_FILTER);
        FilterDTO.FilterInfo identifierFilter = filters.filterOf(StudySpec.IDENTIFIER_FILTER);

        if (identifierFilter != null && identifierFilter.getType().isTextSearch()) {
            specs = specs.and(TextSearchSpec.matching(StudySpec.IDENTIFIER_FILTER, identifierFilter));
        } else if (globalFilter != null && globalFilter.getType() == FilterDTO.FilterType.CONTAINS) {
            specs = specs.and(StudySpec.identifierContaining(globalFilter.valueAsString()));
        }

        if (filters.containsColumn(StudySpec.ACTION_UNIT_FILTER)) {
            specs = specs.and(StudySpec.isInActionUnit(filters.valueAsIdListOf(StudySpec.ACTION_UNIT_FILTER)));
        }
        return specs;
    }

    private Specification<Study> prepareSpecs(@NonNull InstitutionDTO institutionDTO, @NonNull FilterDTO filters) {
        return StudySpec.belongsToInstitution(institutionDTO.getId()).and(userFilterSpecs(filters));
    }

    @Transactional(readOnly = true)
    public Page<StudyDTO> searchStudies(InstitutionDTO institutionDTO, FilterDTO filters, Pageable pageable) {
        return ExactMatchFirst.findAll(studyRepository, prepareSpecs(institutionDTO, filters), pageable,
                        StudySpec.IDENTIFIER_FILTER, filters.searchTextOf(StudySpec.IDENTIFIER_FILTER))
                .map(studyMapper::convert);
    }

    @Transactional(readOnly = true)
    public int countSearchResults(InstitutionDTO institutionDTO, FilterDTO filters) {
        return Math.toIntExact(studyRepository.count(prepareSpecs(institutionDTO, filters)));
    }

    @Transactional(readOnly = true)
    public int countByActionContext(ActionUnitDTO actionUnit) {
        return studyRepository.countByActionUnitId(actionUnit.getId());
    }

    @Transactional(readOnly = true)
    public boolean identifierAlreadyExistInAction(StudyDTO study) {
        if (study.getActionUnit() == null) {
            return false;
        }
        return studyRepository.findByActionUnitIdAndIdentifier(
                        study.getActionUnit().getId(), study.getIdentifier()).stream()
                .anyMatch(existing -> !Objects.equals(existing.getId(), study.getId()));
    }

    /**
     * Saves the study, then its additional (non-system) field answers.
     *
     * @param dto                    the study to save
     * @param additionalFieldAnswers answers to the type's additional fields, keyed by field
     * @return the saved study
     */
    public StudyDTO save(StudyDTO dto, Map<CustomField, CustomFieldAnswerViewModel> additionalFieldAnswers) {
        StudyDTO saved = save(dto);
        customFieldAnswerService.saveAdditionalFieldAnswers(saved, additionalFieldAnswers);
        return saved;
    }

    public StudyDTO save(StudyDTO dto) {
        Study entity = studyMapper.invertConvert(dto);

        UserInfo info = ExecutionContextHolder.get();
        Long actionUnitId = entity != null && entity.getActionUnit() != null ? entity.getActionUnit().getId() : null;
        if (info == null || !profilePermissionService.hasProjectPermission(info, actionUnitId,
                PermissionConstants.INSTANCE_EDIT_STUDIES, PermissionConstants.ORGANIZATION_EDIT_STUDIES,
                PermissionConstants.PROJECT_EDIT_STUDIES)) {
            throw new ForbiddenOperationException("You are not allowed to edit this study");
        }

        resolveLinks(entity, dto);
        identifierGenerator.generateIdentifierIfRequired(entity, studyIdentifierSpec());
        entity = studyRepository.save(entity);
        return studyMapper.convert(entity);
    }

    /** The links travel as ids on the DTO: rebuild them as entities, ignoring ids that match nothing. */
    private void resolveLinks(Study entity, StudyDTO dto) {
        Set<Long> unitIds = idsOf(dto.getRecordingUnits(), RecordingUnitSummaryDTO::getId);
        Set<Long> specimenIds = idsOf(dto.getSpecimens(), SpecimenSummaryDTO::getId);
        Set<RecordingUnit> units = new HashSet<>();
        recordingUnitRepository.findAllById(unitIds).forEach(units::add);
        entity.setRecordingUnits(units);
        entity.setSpecimens(new HashSet<>(specimenRepository.findAllById(specimenIds)));
    }

    private static <T> Set<Long> idsOf(Set<T> summaries, java.util.function.Function<T, Long> id) {
        if (summaries == null) return Set.of();
        Set<Long> ids = new HashSet<>();
        summaries.stream().map(id).filter(Objects::nonNull).forEach(ids::add);
        return ids;
    }

    /**
     * The identifier a new study like {@code dto} would be given, without consuming the counter —
     * the creation form's suggestion. Nothing is written.
     */
    @Transactional(readOnly = true)
    public String suggestIdentifier(StudyDTO dto) {
        return identifierGenerator.suggestIdentifier(studyMapper.invertConvert(dto), studyIdentifierSpec()).value();
    }

    private IdentifierGenerationSpec<Study> studyIdentifierSpec() {
        return IdentifierGenerationSpec.<Study>builder()
                .table(ConfigurableTable.STUDY)
                .entityName("study")
                .generationRequired(IdentifierGenerationSpec.whenNewWithoutIdentifier(Study::getId, Study::getIdentifier))
                .actionUnit(Study::getActionUnit)
                .typeId(study -> study.getType() == null ? null : study.getType().getId())
                .displayValue("ID_UA", study -> study.getActionUnit().getFullIdentifier())
                .identifierAlreadyUsed((study, candidate) ->
                        studyRepository.existsByActionUnitIdAndIdentifier(study.getActionUnit().getId(), candidate))
                .numberSetter(Study::setGeneratedNumber)
                .identifierSetter(Study::setIdentifier)
                .build();
    }

    @Transactional(readOnly = true)
    public StudyDTO findById(Long id) {
        return studyRepository.findById(id).map(studyMapper::convert).orElse(null);
    }

    /**
     * The next study created in the same project after the given one; wraps around to the oldest one.
     */
    @Transactional(readOnly = true)
    public StudyDTO findNextByActionUnit(ActionUnitSummaryDTO actionUnit, StudyDTO current) {
        return studyRepository
                .findFirstByActionUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(
                        actionUnit.getId(), current.getCreationTime())
                .map(studyMapper::convert)
                .orElseGet(() -> studyRepository
                        .findFirstByActionUnitIdOrderByCreationTimeAsc(actionUnit.getId())
                        .map(studyMapper::convert)
                        .orElseThrow(() -> new ActionUnitNotFoundException("No study found for project " + actionUnit.getId())));
    }

    /**
     * The previous study created in the same project before the given one; wraps around to the most recent one.
     */
    @Transactional(readOnly = true)
    public StudyDTO findPreviousByActionUnit(ActionUnitSummaryDTO actionUnit, StudyDTO current) {
        return studyRepository
                .findFirstByActionUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(
                        actionUnit.getId(), current.getCreationTime())
                .map(studyMapper::convert)
                .orElseGet(() -> studyRepository
                        .findFirstByActionUnitIdOrderByCreationTimeDesc(actionUnit.getId())
                        .map(studyMapper::convert)
                        .orElseThrow(() -> new ActionUnitNotFoundException("No study found for project " + actionUnit.getId())));
    }

    /**
     * Supprime l'étude qui n'a aucune relation avec une autre entité.
     *
     * @throws IllegalStateException si l'étude a encore des relations
     */
    public void deleteById(long id) {
        Study entity = studyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Étude introuvable: " + id));

        UserInfo info = ExecutionContextHolder.get();
        Long actionUnitId = entity.getActionUnit() != null ? entity.getActionUnit().getId() : null;
        if (info == null || !profilePermissionService.hasDeleteEntitiesPermission(info, actionUnitId)) {
            throw new ForbiddenOperationException("You are not allowed to delete this study");
        }

        fr.siamois.domain.services.EntityLinkGuard.Kind kind = fr.siamois.domain.services.EntityLinkGuard.Kind.STUDY;
        entityLinkGuard.requireNoLinks(kind, id, "l'étude");

        StudyDTO idOnly = new StudyDTO();
        idOnly.setId(id);
        customFieldAnswerService.deleteAdditionalFieldAnswers(idOnly);
        studyRepository.delete(entity);
    }
}
