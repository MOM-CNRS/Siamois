package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.form.config.VocabularyMode;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.settings.DocumentationThesaurus;
import fr.siamois.domain.models.settings.ProjectCodeSource;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptFieldConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.DocumentationThesaurusRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ProjectCodeSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Finds the concept a field code points at. A field code is a search parameter, like a branch or a
 * collection: it is looked up in a thesaurus, and which thesaurus is the question this class answers
 * when nothing names one.
 *
 * <p>A project can redirect a code ({@link ProjectCodeSource}): the types of a table are read where the project says.
 * Otherwise, by default a code is read in the SIAMOIS thesaurus if it defines it, otherwise in the thesaurus the
 * project documents its fields and types in, then the institution's. A code SIAMOIS defines never moves
 * to a project's thesaurus on its own: a field has to be pointed at another thesaurus explicitly (see
 * {@link VocabularyMode} and the field's own configuration).
 */
@Component
@RequiredArgsConstructor
public class FieldCodeResolver {

    /**
     * The field codes that stand in for another until the thesaurus defines them: each side of a unit's
     * stratigraphic relationships has a vocabulary of its own, which starts as the single list they
     * shared before the split.
     */
    private static final Map<String, String> FALLBACK_FIELD_CODES = Map.of(
            RecordingUnit.ANTERIOR_FIELD_CODE, RecordingUnit.STRATI_FIELD_CODE,
            RecordingUnit.POSTERIOR_FIELD_CODE, RecordingUnit.STRATI_FIELD_CODE,
            RecordingUnit.SYNCHRONOUS_FIELD_CODE, RecordingUnit.STRATI_FIELD_CODE);

    /** The answers of {@link #find} during a {@link #withinOneReading} on this thread; null outside one. */
    private static final ThreadLocal<Map<FindKey, Optional<ConceptFieldConfig>>> READING = new ThreadLocal<>();

    private record FindKey(Long institutionId, @Nullable Long actionUnitId, String fieldCode) {
    }

    private final SiamoisThesaurus siamoisThesaurus;
    private final DocumentationThesaurusRepository documentationThesaurusRepository;
    private final ConceptFieldConfigRepository conceptFieldConfigRepository;
    private final ProjectCodeSourceRepository projectCodeSourceRepository;

    /**
     * @param institutionId the institution whose documentation thesaurus is the last source
     * @param actionUnitId  the project whose documentation thesaurus is read before the institution's, or null
     * @return the code's configuration in the first thesaurus that defines it
     */
    @Transactional(readOnly = true)
    public Optional<ConceptFieldConfig> find(@NonNull Long institutionId, @Nullable Long actionUnitId, @NonNull String fieldCode) {
        Map<FindKey, Optional<ConceptFieldConfig>> reading = READING.get();
        if (reading == null) {
            return lookUp(institutionId, actionUnitId, fieldCode);
        }
        return reading.computeIfAbsent(new FindKey(institutionId, actionUnitId, fieldCode),
                key -> lookUp(institutionId, actionUnitId, fieldCode));
    }

    /**
     * Runs {@code action} as one reading of the configuration: within it, {@link #find} looks each
     * (institution, project, code) up once and serves the same answer to the next calls. For a
     * computation that resolves the same codes over and over without changing them, such as building
     * the configuration of every type of a table. A reading started within another one shares it.
     */
    public static <T> T withinOneReading(@NonNull Supplier<T> action) {
        if (READING.get() != null) {
            return action.get();
        }
        READING.set(new HashMap<>());
        try {
            return action.get();
        } finally {
            READING.remove();
        }
    }

    private Optional<ConceptFieldConfig> lookUp(Long institutionId, @Nullable Long actionUnitId, String fieldCode) {
        if (actionUnitId != null) {
            Optional<ConceptFieldConfig> redirected = projectCodeSourceRepository.findByActionUnitIdAndFieldCode(actionUnitId, fieldCode)
                    .flatMap(this::resolve);
            if (redirected.isPresent()) {
                return redirected;
            }
        }
        for (Vocabulary source : defaultSources(institutionId, actionUnitId)) {
            Optional<ConceptFieldConfig> config = findIn(source, fieldCode);
            if (config.isPresent()) {
                return config;
            }
        }
        return Optional.empty();
    }

    /**
     * What a project's redirection points at. A redirection to a branch has no configuration row of its own, so one is
     * made up for the branch's concept; a redirection to a code the thesaurus no longer defines resolves to nothing,
     * and the code is then read where it is by default.
     */
    private Optional<ConceptFieldConfig> resolve(ProjectCodeSource redirection) {
        if (redirection.isBranch()) {
            ConceptFieldConfig branch = new ConceptFieldConfig();
            branch.setVocabulary(redirection.getBranchTopTerm().getVocabulary());
            branch.setFieldCode(redirection.getFieldCode());
            branch.setConcept(redirection.getBranchTopTerm());
            return Optional.of(branch);
        }
        Vocabulary vocabulary = redirection.getVocabulary();
        String sourceFieldCode = redirection.getSourceFieldCode();
        if (vocabulary == null || sourceFieldCode == null) {
            return Optional.empty();
        }
        return findIn(vocabulary, sourceFieldCode);
    }

    /** The thesaurus a code is read in when nothing redirects it: the first default source that defines it. */
    @NonNull
    @Transactional(readOnly = true)
    public Optional<Vocabulary> findDefaultVocabulary(@NonNull Long institutionId, @Nullable Long actionUnitId, @NonNull String fieldCode) {
        return defaultSources(institutionId, actionUnitId).stream()
                .filter(source -> findIn(source, fieldCode).isPresent())
                .findFirst();
    }

    /** The configuration of a field code in one given thesaurus, read through its fallback code when it has none. */
    @NonNull
    public Optional<ConceptFieldConfig> findIn(@NonNull Vocabulary vocabulary, @NonNull String fieldCode) {
        Optional<ConceptFieldConfig> own = conceptFieldConfigRepository.findOneByVocabularyIdAndFieldCode(vocabulary.getId(), fieldCode);
        if (own.isPresent()) {
            return own;
        }
        String fallback = FALLBACK_FIELD_CODES.get(fieldCode);
        return fallback == null
                ? Optional.empty()
                : conceptFieldConfigRepository.findOneByVocabularyIdAndFieldCode(vocabulary.getId(), fallback);
    }

    /** The thesauri a code is read in when nothing names one, in the order they are tried. */
    @NonNull
    public List<Vocabulary> defaultSources(@NonNull Long institutionId, @Nullable Long actionUnitId) {
        List<Vocabulary> sources = new ArrayList<>();
        siamoisThesaurus.vocabulary().ifPresent(sources::add);
        if (actionUnitId != null) {
            documentationThesaurusRepository.findByActionUnitId(actionUnitId)
                    .map(DocumentationThesaurus::getVocabulary)
                    .ifPresent(sources::add);
        }
        documentationThesaurusRepository.findOfInstitution(institutionId)
                .map(DocumentationThesaurus::getVocabulary)
                .ifPresent(sources::add);
        return sources;
    }
}
