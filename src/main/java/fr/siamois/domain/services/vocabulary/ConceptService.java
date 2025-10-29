package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.events.publisher.ConceptChangeEventPublisher;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptHierarchy;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.PurlInfoDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRelationRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for managing concepts in the vocabulary.
 * This service provides methods to save, retrieve, and update concepts,
 * as well as to find concepts related to spatial and action units of an institution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConceptService {

    private final ConceptRepository conceptRepository;
    private final ConceptApi conceptApi;
    private final LabelService labelService;
    private final ConceptRelationRepository conceptRelationRepository;
    private final LocalizedConceptDataRepository  localizedConceptDataRepository;
    private final ConceptChangeEventPublisher conceptChangeEventPublisher;

    /**
     * Saves a concept if it does not already exist in the repository.
     *
     * @param concept the concept to save or retrieve
     * @return the saved or existing concept
     */
    public Concept saveOrGetConcept(Concept concept) {
        Vocabulary vocabulary = concept.getVocabulary();
        Optional<Concept> optConcept = conceptRepository.findConceptByExternalIdIgnoreCase(vocabulary.getExternalVocabularyId(), concept.getExternalId());
        return optConcept.orElseGet(() -> conceptRepository.save(concept));
    }

    /**
     * Finds all concepts related to spatial units of a given institution.
     *
     * @param institution the institution for which to find concepts
     * @return a list of concepts associated with spatial units of the institution
     */
    public List<Concept> findAllBySpatialUnitOfInstitution(Institution institution) {
        return conceptRepository.findAllBySpatialUnitOfInstitution(institution.getId());
    }

    /**
     * Finds all concepts related to action units of a given institution.
     *
     * @param institution the institution for which to find concepts
     * @return a list of concepts associated with action units of the institution
     */
    public List<Concept> findAllByActionUnitOfInstitution(Institution institution) {
        return conceptRepository.findAllByActionUnitOfInstitution(institution.getId());
    }

    /**
     * Saves a concept from a FullInfoDTO, or retrieves it if it already exists.
     *
     * @param vocabulary the vocabulary to which the concept belongs
     * @param conceptDTO the FullInfoDTO containing concept information
     * @return the saved or existing concept
     */
    public Concept saveOrGetConceptFromFullDTO(Vocabulary vocabulary, FullInfoDTO conceptDTO, Concept fieldParentConcept) {
        Optional<Concept> optConcept = conceptRepository
                .findConceptByExternalIdIgnoreCase(
                        vocabulary.getExternalVocabularyId(),
                        conceptDTO.getIdentifier()[0].getValue()
                );

        if (optConcept.isPresent()) {
            updateAllLabelsFromDTO(optConcept.get(), conceptDTO, fieldParentConcept);
            updateAllDefinitionsFromDTO(optConcept.get(), conceptDTO, fieldParentConcept);
            return optConcept.get();
        }

        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId(conceptDTO.getIdentifier()[0].getValue());

        concept = conceptRepository.save(concept);

        updateAllLabelsFromDTO(concept, conceptDTO, fieldParentConcept);
        updateAllDefinitionsFromDTO(concept, conceptDTO, fieldParentConcept);

        return concept;
    }

    /**
     * Updates all labels of a saved concept from a FullInfoDTO.
     *
     * @param savedConcept the concept to update
     * @param conceptDto   the FullInfoDTO containing label information
     */
    public void updateAllLabelsFromDTO(Concept savedConcept, FullInfoDTO conceptDto, Concept fieldParentConcept) {
        if (conceptDto.getPrefLabel() != null) {
            for (PurlInfoDTO label : conceptDto.getPrefLabel()) {
                labelService.updateLabel(savedConcept, label.getLang(), label.getValue(), fieldParentConcept);
            }
        }
    }

    private void updateDefinition(Concept savedConcept, String lang, String definition, Concept fieldParentConcept) {
        Optional<LocalizedConceptData> optData = localizedConceptDataRepository.findByConceptAndLangCode(savedConcept.getId(), lang);
        LocalizedConceptData localizedConceptData = null;
        if (optData.isPresent()) {
            localizedConceptData = optData.get();
        } else {
            localizedConceptData = new LocalizedConceptData();
            localizedConceptData.setConcept(savedConcept);
            localizedConceptData.setLangCode(lang);
            localizedConceptData.setParentConcept(fieldParentConcept);
        }
        localizedConceptData.setDefinition(definition);
        localizedConceptDataRepository.save(localizedConceptData);
    }

    /**
     * Updates all definitions of a saved concept from FullInfoDTO
     * @param savedConcept the concept to update
     * @param conceptDto  the FullInfoDTO containing definition information
     */
    public void updateAllDefinitionsFromDTO(Concept savedConcept, FullInfoDTO conceptDto, Concept fieldParentConcept) {
        if (conceptDto.getDefinition() != null) {
            for (PurlInfoDTO definition : conceptDto.getDefinition()) {
                updateDefinition(savedConcept, definition.getLang(), definition.getValue(), fieldParentConcept);
            }
        }
    }

    public void saveAllSubConceptOfIfUpdated(ConceptFieldConfig config) throws ErrorProcessingExpansionException {
        log.trace("API call to fetch down expansion for concept FieldCode : {}", config.getFieldCode());
        try {
            Concept concept = config.getConcept();
            Vocabulary vocabulary = concept.getVocabulary();
            ConceptBranchDTO branchDTO = conceptApi.fetchDownExpansion(config);
            if (branchDTO == null) return;
            conceptChangeEventPublisher.publishEvent(config.getFieldCode());

            Map<String, Concept> concepts = new HashMap<>();
            FullInfoDTO parentConcept = findParentConceptDTO(branchDTO, concept);
            if (parentConcept.getNarrower() == null) return;

            saveOrGetAllConceptsFromBranchAndStoreInMap(config, branchDTO, concepts, vocabulary);

            Concept parentSavedConcept = concepts.values()
                    .stream()
                    .filter(c -> concept.getExternalId().equalsIgnoreCase(parentConcept.getIdentifierStr()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No concept found for " + concept));

            Map<Concept, Concept> childAndParentMap = new HashMap<>();
            for (Map.Entry<String, FullInfoDTO> entry : branchDTO.getData().entrySet()) {
                if (Objects.nonNull(entry.getValue().getNarrower())) {
                    createRelationBetweenConcepts(entry, parentConcept, concepts, childAndParentMap, parentSavedConcept);
                }
            }

        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            throw new ErrorProcessingExpansionException("Error processing expansion for concept field config id " + config.getId());
        }

    }

    private void createRelationBetweenConcepts(Map.Entry<String, FullInfoDTO> entry, FullInfoDTO parentConcept, Map<String, Concept> concepts, Map<Concept, Concept> childAndParentMap, Concept parentSavedConcept) {
        for (PurlInfoDTO narrower : entry.getValue().getNarrower()) {
            if (narrowerIsNotParentConcept(narrower, parentConcept)) {
                Concept parent = concepts.get(entry.getKey());
                Concept child =  concepts.get(narrower.getValue());

                if (!childAndParentMap.containsKey(child)) {
                    ConceptHierarchy relation = new ConceptHierarchy(parent,child, parentSavedConcept);
                    conceptRelationRepository.save(relation);
                    childAndParentMap.put(child, parent);
                } else {
                    log.debug("Concept {} already has a parent concept, skipping relation creation for {}.", child.getExternalId(), parent.getExternalId());
                }
            }
        }
    }

    private void saveOrGetAllConceptsFromBranchAndStoreInMap(ConceptFieldConfig config, ConceptBranchDTO branchDTO, Map<String, Concept> concepts, Vocabulary vocabulary) {
        for (Map.Entry<String, FullInfoDTO> entry : branchDTO.getData().entrySet()) {
            concepts.put(entry.getKey(), saveOrGetConceptFromFullDTO(vocabulary, entry.getValue(), config.getConcept()));
        }
    }

    private static FullInfoDTO findParentConceptDTO(ConceptBranchDTO branchDTO, Concept concept) {
        return branchDTO.getData().values().stream()
                .filter(dto -> concept.getExternalId().equalsIgnoreCase(dto.getIdentifier()[0].getValue()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No concept found for " + concept.getExternalId()));
    }

    private static boolean narrowerIsNotParentConcept(PurlInfoDTO narrower, FullInfoDTO parentConcept) {
        return !narrower.getValue().equalsIgnoreCase(parentConcept.getIdentifier()[0].getValue());
    }

    /**
     * Finds all concepts by their IDs.
     *
     * @param conceptIds the list of concept IDs to find
     * @return a list of concepts corresponding to the provided IDs
     */
    public Object findAllById(List<Long> conceptIds) {
        return conceptRepository.findAllById(conceptIds);
    }


    public Optional<Concept> findById(Long id) {
        return conceptRepository.findById(id);
    }
}
