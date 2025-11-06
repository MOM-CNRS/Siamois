package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.events.publisher.ConceptChangeEventPublisher;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.vocabulary.*;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.PurlInfoDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptHierarchyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRelatedLinkRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
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
    private final ConceptRelatedLinkRepository conceptRelatedLinkRepository;
    private final LocalizedConceptDataRepository  localizedConceptDataRepository;
    private final ConceptChangeEventPublisher conceptChangeEventPublisher;
    private final ConceptLabelRepository conceptLabelRepository;
    private final ConceptHierarchyRepository conceptHierarchyRepository;

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

        Concept concept;

        if (optConcept.isPresent()) {
            concept = optConcept.get();
            concept.setDeleted(false);
        } else {
            concept = new Concept();
            concept.setVocabulary(vocabulary);
            concept.setExternalId(conceptDTO.getIdentifier()[0].getValue());
        }

        concept = conceptRepository.save(concept);

        updateAllLabelsFromDTO(concept, conceptDTO, fieldParentConcept);
        updateAllDefinitionsFromDTO(concept, conceptDTO);

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

        if (conceptDto.getAltLabel() != null) {
            for (PurlInfoDTO altLabel : conceptDto.getAltLabel()) {
                labelService.updateAltLabel(savedConcept, altLabel.getLang(), altLabel.getValue(), fieldParentConcept);
            }
        }
    }



    private void updateDefinition(Concept savedConcept, String lang, String definition) {
        Optional<LocalizedConceptData> optData = localizedConceptDataRepository.findByConceptAndLangCode(savedConcept.getId(), lang);
        LocalizedConceptData localizedConceptData = null;
        if (optData.isPresent()) {
            localizedConceptData = optData.get();
        } else {
            localizedConceptData = new LocalizedConceptData();
            localizedConceptData.setConcept(savedConcept);
            localizedConceptData.setLangCode(lang);
        }
        localizedConceptData.setDefinition(definition);
        localizedConceptDataRepository.save(localizedConceptData);
    }

    /**
     * Find LocalizedConceptData for given concept for a given language
     * @param concept the concept
     * @param lang  the lang code
     * @return the concept data or null if not found
     */
    public LocalizedConceptData getLocalizedConceptDataByConceptAndLangCode(Concept concept, String lang) {
        return localizedConceptDataRepository.findByConceptAndLangCode(concept.getId(), lang).orElse(null);
    }

    /**
     * Updates all definitions of a saved concept from FullInfoDTO
     * @param savedConcept the concept to update
     * @param conceptDto  the FullInfoDTO containing definition information
     */
    public void updateAllDefinitionsFromDTO(Concept savedConcept, FullInfoDTO conceptDto) {
        if (conceptDto.getDefinition() != null) {
            for (PurlInfoDTO definition : conceptDto.getDefinition()) {
                updateDefinition(savedConcept, definition.getLang(), definition.getValue());
            }
        }
    }

    public void saveAllSubConceptOfIfUpdated(ConceptFieldConfig config) throws ErrorProcessingExpansionException {
        log.trace("API call to fetch down expansion for concept FieldCode : {}", config.getFieldCode());
        try {
            Concept parentSavedConcept = config.getConcept();
            Vocabulary vocabulary = parentSavedConcept.getVocabulary();
            ConceptBranchDTO branchDTO = conceptApi.fetchDownExpansion(config);
            if (branchDTO == null) {
                log.trace("No update found for concept FieldCode : {}", config.getFieldCode());
                return;
            }
            conceptChangeEventPublisher.publishEvent(config.getFieldCode());

            Map<String, Concept> urlToSavedConceptMap = new HashMap<>();
            FullInfoDTO parentConcept = findAndSetParentConceptDTO(branchDTO, parentSavedConcept);
            if (parentConcept.getNarrower() == null) return;

            saveOrGetAllConceptsFromBranchAndStoreInMap(config, branchDTO, urlToSavedConceptMap, vocabulary);
            saveAllConceptDataAndRelations(branchDTO, urlToSavedConceptMap, parentSavedConcept, vocabulary);
            processDeletedConcepts(urlToSavedConceptMap, parentSavedConcept);


        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            throw new ErrorProcessingExpansionException("Error processing expansion for concept field config id " + config.getId());
        }

    }

    private void processDeletedConcepts(Map<String, Concept> urlToSavedConceptMap, Concept parentSavedConcept) {
        Set<Concept> conceptsInBranch = new HashSet<>(urlToSavedConceptMap.values());
        Set<Concept> deletedConcepts = new HashSet<>();
        for (ConceptLabel conceptLabel : conceptLabelRepository.findAllByParentConcept(parentSavedConcept)) {
            Concept currentConcept = conceptLabel.getConcept();
            if (!currentConcept.isDeleted() && !conceptsInBranch.contains(conceptLabel.getConcept())) {
                if (!deletedConcepts.contains(currentConcept)) {
                    currentConcept.setDeleted(true);
                    conceptRepository.save(currentConcept);
                    deletedConcepts.add(currentConcept);
                }
                conceptLabel.setParentConcept(null);
                conceptLabelRepository.save(conceptLabel);
            }
        }
        log.debug("Mark as deleted {} concepts for parent concept {} in {}", deletedConcepts.size(), parentSavedConcept.getExternalId(), parentSavedConcept.getVocabulary().getExternalVocabularyId());
    }

    private void saveAllConceptDataAndRelations(ConceptBranchDTO branchDTO, Map<String, Concept> urlToSavedConceptMap, Concept parentSavedConcept, Vocabulary vocabulary) {
        Map<Concept, Concept> childAndParentMap = new HashMap<>();
        for (Map.Entry<String, FullInfoDTO> entry : branchDTO.getData().entrySet()) {

            if (Objects.nonNull(entry.getValue().getNarrower())) {
                createRelationBetweenConcepts(entry, urlToSavedConceptMap, childAndParentMap, parentSavedConcept);
            }

            createRelatedLinkAndSetRelatedConcepts(vocabulary, urlToSavedConceptMap.get(entry.getKey()), entry.getValue().getUrlOfRelated(), urlToSavedConceptMap);
        }
    }

    private void createRelatedLinkAndSetRelatedConcepts(Vocabulary vocabulary, Concept concept, List<String> relatedUrl, Map<String, Concept> urlToSavedConceptMap) {
        for (String url : relatedUrl) {
            FullInfoDTO fetchedConceptDTO = conceptApi.fetchConceptInfoByUri(vocabulary, url);
            if (fetchedConceptDTO != null) {
                Concept relatedConcept = urlToSavedConceptMap.computeIfAbsent(url, currentUrl -> saveOrGetConceptFromFullDTO(vocabulary, fetchedConceptDTO, null));
                conceptRelatedLinkRepository.save(new ConceptRelatedLink(concept, relatedConcept));
            }
        }
    }

    private void createRelationBetweenConcepts(Map.Entry<String, FullInfoDTO> entry, Map<String, Concept> concepts, Map<Concept, Concept> childToParentMap, Concept parentSavedConcept) {
        for (PurlInfoDTO narrower : entry.getValue().getNarrower()) {
            Concept parent = concepts.get(entry.getKey());
            if (parent == null) {
                throw new IllegalStateException("No concept found in cache map for URL " + entry.getKey());
            }
            Concept child =  concepts.get(narrower.getValue());
            if (child == null) {
                throw new IllegalStateException("No concept found in cache map for URL " + narrower.getValue());
            }

            if (!parent.equals(parentSavedConcept)) {
                if (!childToParentMap.containsKey(child)) {
                    ConceptHierarchy relation = new ConceptHierarchy(parent,child, parentSavedConcept);
                    conceptHierarchyRepository.save(relation);
                    childToParentMap.put(child, parent);
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

    private static FullInfoDTO findAndSetParentConceptDTO(ConceptBranchDTO branchDTO, Concept concept) {

        for (String url : branchDTO.getData().keySet()) {
            FullInfoDTO dto = branchDTO.getData().get(url);
            if (dto.getIdentifier() != null) {
                for (PurlInfoDTO identifier : dto.getIdentifier()) {
                    if (concept.getExternalId().equalsIgnoreCase(identifier.getValue())) {
                        branchDTO.setParentUrl(url);
                        return dto;
                    }
                }
            }
        }

        throw new IllegalStateException("No concept found for " + concept.getExternalId());
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

    public List<Concept> findParentsOfConceptInField(Concept concept, Concept parentFieldConcept) {
        List<Concept> result = new ArrayList<>();
        List<ConceptHierarchy> parents = conceptHierarchyRepository.findAllByChildAndParentFieldContext(concept, parentFieldConcept);
        while (!parents.isEmpty()) {
            Concept toAdd = parents.get(0).getParent();
            result.add(0, toAdd);
            concept = parents.get(0).getParent();
            parents = conceptHierarchyRepository.findAllByChildAndParentFieldContext(concept, parentFieldConcept);
        }
        return result;
    }
}
