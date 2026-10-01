package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.ui.api.openapi.v1.request.list.ValuesLimit;
import fr.siamois.ui.api.openapi.v1.resource.form.MultiValue;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.api.openapi.v1.resource.form.SelectManyFieldAnswer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Every value of one multi-valued answer, a page at a time
 * ({@code GET /api/v1/{collection}/{id}/fields/{fieldId}/values}) — where an answer's
 * {@code _links.values} points when a list or a detail only carried a preview of it.
 * <p>
 * A {@link RelationField} is paged in the database; any other multi-valued answer is small enough
 * to be read whole — through the same one-row projection its list uses, so its values are exactly
 * the ones that list previewed — and paged here. Either way the values are in the order of their
 * label, the order every preview is taken in.
 */
@Service
@RequiredArgsConstructor
public class FieldValuesService {

    public static final String SORT_ASC = "label:asc";
    public static final String SORT_DESC = "label:desc";

    private final ProjectApiService projectApiService;
    private final SpecimenService specimenService;
    private final PhaseOpenApiService phaseOpenApiService;
    private final ContainerOpenApiService containerOpenApiService;
    private final RelationFieldService relationFieldService;
    private final RecordingUnitListProjectionService recordingUnitListProjectionService;
    private final FindListProjectionService findListProjectionService;
    private final PhaseListProjectionService phaseListProjectionService;
    private final ContainerListProjectionService containerListProjectionService;
    private final ProjectListProjectionService projectListProjectionService;
    private final CustomFieldRepository customFieldRepository;

    /** The owners an answer's values can be read from, by the API collection they are served under. */
    private static final Map<String, Class<?>> OWNER_TYPES = Map.of(
            "recording-units", RecordingUnit.class,
            "finds", Specimen.class,
            "phases", Phase.class,
            "containers", Container.class,
            "projects", ActionUnit.class);

    @Transactional(readOnly = true)
    public RelationFieldService.ValuesPage values(ProjectApiCaller caller, String collection, String ownerKey,
                                                  String fieldId, int offset, int limit, @Nullable String search,
                                                  String sort, String lang) {
        Class<?> ownerType = Optional.ofNullable(OWNER_TYPES.get(collection))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection inconnue : " + collection));
        boolean ascending = parseSort(sort);
        Owner owner = requireOwner(caller, ownerType, ownerKey, lang);

        requireMultiValued(ownerType, fieldId);
        Optional<RelationField> relation = MultiValueAnswers.relationFieldOf(ownerType, fieldId);
        if (relation.isPresent()) {
            return relationFieldService.page(relation.get(), owner.id(), offset, limit, search, ascending, lang);
        }
        return page(owner.answer(fieldId), offset, limit, search, ascending);
    }

    /** 404 for a field the owner has no such field, 400 for one that isn't multi-valued. */
    private void requireMultiValued(Class<?> ownerType, String fieldId) {
        CustomField field = MultiValueAnswers.systemFieldOf(ownerType, fieldId)
                .or(() -> parseFieldId(fieldId).flatMap(customFieldRepository::findById))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Champ inconnu : " + fieldId));
        if (!FieldAnswerWireService.answerTypeOf(field).startsWith("SELECT_MULTIPLE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce champ n'est pas multivalué : " + fieldId);
        }
    }

    private static Optional<Long> parseFieldId(String fieldId) {
        try {
            return Optional.of(Long.parseLong(fieldId.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static boolean parseSort(String sort) {
        if (sort == null || sort.isBlank() || SORT_ASC.equalsIgnoreCase(sort.trim())) return true;
        if (SORT_DESC.equalsIgnoreCase(sort.trim())) return false;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tri invalide (label:asc ou label:desc) : " + sort);
    }

    /** An owner the caller can see, and how to read one answer of it whole. */
    private interface Owner {
        long id();

        @Nullable
        Object answer(String fieldId);
    }

    private record ProjectedOwner(long id, java.util.function.Function<String, Map<String, Object>> project)
            implements Owner {
        @Override
        public Object answer(String fieldId) {
            Map<String, Object> answers = project.apply(fieldId);
            return answers == null ? null : answers.get(fieldId);
        }
    }

    private Owner requireOwner(ProjectApiCaller caller, Class<?> ownerType, String key, String lang) {
        int all = ValuesLimit.UNLIMITED;
        if (ownerType == RecordingUnit.class) {
            RecordingUnitDTO ru = projectApiService.requireViewableRecordingUnit(caller, key);
            return new ProjectedOwner(ru.getId(), fieldId ->
                    recordingUnitListProjectionService.build(List.of(ru), fieldId, lang, all).answersFor(ru.getId()));
        }
        if (ownerType == Specimen.class) {
            SpecimenDTO find = specimenService.findAccessibleByKey(key, caller.accessibleInstitutionIds())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mobilier introuvable ou hors périmètre"));
            return new ProjectedOwner(find.getId(), fieldId ->
                    findListProjectionService.build(List.of(find), fieldId, lang, all).answersFor(find.getId()));
        }
        if (ownerType == Phase.class) {
            PhaseDTO phase = phaseOpenApiService.requireAccessible(parseId(key), caller.person(), caller.accessibleInstitutionIds());
            return new ProjectedOwner(phase.getId(), fieldId ->
                    phaseListProjectionService.build(List.of(phase), fieldId, lang, all).answersFor(phase.getId()));
        }
        if (ownerType == Container.class) {
            ContainerDTO container = containerOpenApiService.requireAccessible(parseId(key), caller.person(), caller.accessibleInstitutionIds());
            return new ProjectedOwner(container.getId(), fieldId ->
                    containerListProjectionService.build(List.of(container), fieldId, lang, all).answersFor(container.getId()));
        }
        AccessibleProjectForApi project = projectApiService.requireAccessibleProject(caller, key);
        long projectId = project.actionUnit().getId();
        return new ProjectedOwner(projectId, fieldId ->
                projectListProjectionService.build(List.of(project), fieldId, lang, all).answersFor(projectId));
    }

    private static long parseId(String key) {
        try {
            return Long.parseLong(key.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Identifiant numérique attendu : " + key);
        }
    }

    /** One page of an answer read whole: filtered on its labels, in their order. */
    private static RelationFieldService.ValuesPage page(@Nullable Object answer, int offset, int limit,
                                                       @Nullable String search, boolean ascending) {
        List<ResourceRef> all = valuesOf(answer);
        String needle = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        Comparator<ResourceRef> byLabel = Comparator.comparing(
                (ResourceRef ref) -> ref.label() == null ? null : ref.label().toLowerCase(Locale.ROOT),
                Comparator.nullsLast(Comparator.naturalOrder()));
        List<ResourceRef> matching = all.stream()
                .filter(ref -> needle == null || (ref.label() != null && ref.label().toLowerCase(Locale.ROOT).contains(needle)))
                .sorted(ascending ? byLabel : byLabel.reversed())
                .toList();
        List<ResourceRef> page = matching.stream().skip(offset).limit(limit).toList();
        return new RelationFieldService.ValuesPage(page, matching.size());
    }

    /** An answer's values — none for an empty one (a multi-valued field left unanswered projects as null). */
    private static List<ResourceRef> valuesOf(@Nullable Object answer) {
        if (answer instanceof MultiValue value) return value.values();
        if (answer instanceof SelectManyFieldAnswer envelope) return envelope.values() == null ? List.of() : envelope.values();
        return List.of();
    }
}
