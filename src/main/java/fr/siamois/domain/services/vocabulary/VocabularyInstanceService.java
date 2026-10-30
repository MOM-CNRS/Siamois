package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyInstance;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyInstanceRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VocabularyInstanceService {

    private final VocabularyInstanceRepository vocabularyInstanceRepository;
    private final VocabularyRepository vocabularyRepository;

    /**
     * The saved instance of the given base URI, created if it does not exist yet.
     */
    @Transactional
    public VocabularyInstance findOrCreate(@NonNull String baseUri) {
        return vocabularyInstanceRepository.findFirstByBaseUriIgnoreCaseOrderByIdAsc(baseUri)
                .orElseGet(() -> vocabularyInstanceRepository.save(new VocabularyInstance(baseUri)));
    }

    /**
     * The instances of the documentation thesauri of the organisations and projects whose settings a person
     * manages, each one once, in the order of their first thesaurus.
     */
    @Transactional(readOnly = true)
    public List<VocabularyInstance> findDocumentationInstancesIn(@NonNull ProfilePermissionService.SettingsScope scope) {
        return vocabularyRepository.findDocumentationThesauriOf(scope.allInstitutions(), scope.institutionIds(),
                        scope.allProjects(), scope.projectInstitutionIds(), scope.actionUnitIds()).stream()
                .map(Vocabulary::getInstance)
                .distinct()
                .toList();
    }
}
