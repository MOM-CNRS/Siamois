package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.vocabulary.VocabularyNotFoundException;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.dto.entity.vocabulary.ConceptCollectionDTO;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptCollectionRepository;
import fr.siamois.utils.vocabulary.ConceptApiUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConceptCollectionService {

    private final ConceptCollectionRepository conceptCollectionRepository;
    private final VocabularyService vocabularyService;
    private final ConceptApi conceptApi;
    private final ApplicationContext applicationContext;


    /**
     * Creates the JPA ConceptCollection or retrieve it and load all concepts associated
     * @param collection The transient collection
     */
    @Transactional
    public ConceptCollection createOrUpdateConceptCollection(@NonNull ConceptCollectionDTO collection) {
        try {
            Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(collection.getVocabulary().completeUri());
            Optional<ConceptCollection> opt = conceptCollectionRepository.findByVocabularyAndExternalId(vocabulary, collection.getExternalId());
            ConceptCollection savedCollection;
            if(opt.isPresent()) {
                savedCollection = opt.get();
            } else {
                savedCollection = new ConceptCollection();
                savedCollection.setExternalId(collection.getExternalId());
                savedCollection.setVocabulary(vocabulary);
                savedCollection = conceptCollectionRepository.save(savedCollection);
            }
            saveAllThesaurusInfoOfCollection(savedCollection);
            return savedCollection;
        } catch (InvalidEndpointException e) {
            log.error("Vocabulary could not be found", e);
            throw new VocabularyNotFoundException("Vocabulary could not be found");
        }
    }

    private void saveAllThesaurusInfoOfCollection(@NonNull ConceptCollection savedCollection) {
        ConceptBranchDTO branchDTO;
        try {
            branchDTO = conceptApi.fetchCollectionBranch(savedCollection.getVocabulary(), savedCollection);
        } catch (ErrorProcessingExpansionException e) {
            log.error("Error while fetching collection branch", e);
            return;
        }
        if (Objects.isNull(branchDTO)) {
            return;
        }
        ConceptApiUtils.BranchLoadComponents components = new ConceptApiUtils.BranchLoadComponents(applicationContext);
        Map<String, Concept> savedConcepts = ConceptApiUtils.saveAllConceptsOfBranch(components, savedCollection.getVocabulary(), branchDTO);
        for (Concept concept : savedConcepts.values()) {
            savedCollection.getConcepts().add(concept);
        }
        conceptCollectionRepository.save(savedCollection);
    }

}
