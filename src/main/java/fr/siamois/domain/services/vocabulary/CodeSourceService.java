package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.settings.ProjectCodeSource;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.concept.ConceptBranchDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ProjectCodeSourceRepository;
import fr.siamois.mapper.vocabulary.VocabularyMapper;
import fr.siamois.utils.vocabulary.ConceptApiUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Where a project reads a field code when it is not where it is read by default: the source of the types of a table,
 * which no field carries, is set here, with the same two ways as a field's own source (a code looked up in a thesaurus,
 * or a branch of it).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeSourceService {

    private final ProjectCodeSourceRepository projectCodeSourceRepository;
    private final ActionUnitRepository actionUnitRepository;
    private final ConceptService conceptService;
    private final VocabularyService vocabularyService;
    private final VocabularyMapper vocabularyMapper;
    private final ConceptApi conceptApi;
    private final ApplicationContext applicationContext;

    @Transactional(readOnly = true)
    public Optional<ProjectCodeSource> find(@NonNull Long projectId, @NonNull String fieldCode) {
        return projectCodeSourceRepository.findByActionUnitIdAndFieldCode(projectId, fieldCode);
    }

    /** The project reads {@code fieldCode} as the code {@code sourceCode} of {@code vocabulary}. */
    @Transactional
    public void useFieldCode(@NonNull Long projectId, @NonNull String fieldCode,
                             @NonNull Vocabulary vocabulary, @NonNull String sourceCode) {
        ProjectCodeSource source = findOrCreate(projectId, fieldCode);
        source.setVocabulary(vocabulary);
        source.setSourceFieldCode(sourceCode);
        source.setBranchTopTerm(null);
        projectCodeSourceRepository.save(source);
    }

    /**
     * The project reads {@code fieldCode} as the subtree of a branch, which is loaded.
     *
     * @param progressWrapper where to report the branch's import, or null when nobody is watching
     */
    @Transactional(rollbackFor = Exception.class)
    public void useBranch(@NonNull Long projectId, @NonNull String fieldCode,
                          @NonNull ConceptDTO branchTopConcept, @Nullable ProgressWrapper progressWrapper) throws InvalidEndpointException {
        Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(branchTopConcept.getVocabulary().completeUri());
        branchTopConcept.setVocabulary(vocabularyMapper.convert(vocabulary));
        Concept concept = conceptService.saveOrGetConcept(branchTopConcept);
        loadDownExpansion(concept, progressWrapper);

        ProjectCodeSource source = findOrCreate(projectId, fieldCode);
        source.setBranchTopTerm(concept);
        source.setVocabulary(null);
        source.setSourceFieldCode(null);
        projectCodeSourceRepository.save(source);
    }

    /** The project reads {@code fieldCode} where it is read by default again. */
    @Transactional
    public void clear(@NonNull Long projectId, @NonNull String fieldCode) {
        projectCodeSourceRepository.findByActionUnitIdAndFieldCode(projectId, fieldCode)
                .ifPresent(projectCodeSourceRepository::delete);
    }

    private ProjectCodeSource findOrCreate(Long projectId, String fieldCode) {
        return projectCodeSourceRepository.findByActionUnitIdAndFieldCode(projectId, fieldCode).orElseGet(() -> {
            ProjectCodeSource created = new ProjectCodeSource();
            created.setActionUnit(actionUnitRepository.findById(projectId)
                    .orElseThrow(() -> new NoSuchElementException("Unknown project: " + projectId)));
            created.setFieldCode(fieldCode);
            return created;
        });
    }

    private void loadDownExpansion(Concept concept, @Nullable ProgressWrapper progressWrapper) {
        try {
            ConceptBranchDTO dto = conceptApi.fetchDownExpansion(concept.getVocabulary(), concept.getExternalId());
            ConceptApiUtils.BranchLoadComponents components = new ConceptApiUtils.BranchLoadComponents(applicationContext);
            ConceptApiUtils.saveAllConceptsOfBranch(components, concept.getVocabulary(), dto, new HashMap<>(), progressWrapper);
        } catch (ErrorProcessingExpansionException e) {
            log.error("Error while fetching down expansion for branch {}", concept.getExternalId(), e);
        }
    }
}
