package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.vocabulary.Concept;
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
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing concepts in the vocabulary.
 * This service provides methods to save, retrieve, and update concepts,
 * as well as to find concepts related to spatial and action units of an institution.
 */
@Service
@RequiredArgsConstructor
public class ConceptService {

    private final ConceptRepository conceptRepository;
    private final ConceptApi conceptApi;
    private final LabelService labelService;
    private final ConceptRelationRepository conceptRelationRepository;
    private final LocalizedConceptDataRepository  localizedConceptDataRepository;

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
     * @param savedConcept
     * @param conceptDto
     */
    public void updateAllDefinitionsFromDTO(Concept savedConcept, FullInfoDTO conceptDto, Concept fieldParentConcept) {
        if (conceptDto.getDefinition() != null) {
            for (PurlInfoDTO definition : conceptDto.getDefinition()) {
                updateDefinition(savedConcept, definition.getLang(), definition.getValue(), fieldParentConcept);
            }
        }
    }

    public void saveAllSubConceptOfIfUpdated(ConceptFieldConfig config) throws ErrorProcessingExpansionException {
        Concept concept = config.getConcept();
        Vocabulary vocabulary = concept.getVocabulary();
        ConceptBranchDTO branchDTO = conceptApi.fetchDownExpansion(config);
        if (branchDTO == null) return;

        FullInfoDTO parentConcept = branchDTO.getData().values().stream()
                .filter(dto -> concept.getExternalId().equalsIgnoreCase(dto.getIdentifier()[0].getValue()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No concept found for " + concept.getExternalId()));

        if (parentConcept.getNarrower() == null) return;

        Map<String, Concept> concepts = new HashMap<>();
        for (Map.Entry<String, FullInfoDTO> entry : branchDTO.getData().entrySet()) {
            concepts.put(entry.getKey(), saveOrGetConceptFromFullDTO(vocabulary, entry.getValue(), config.getConcept()));
        }

        for (Map.Entry<String, FullInfoDTO> entry : branchDTO.getData().entrySet()) {
            for (PurlInfoDTO narrower : entry.getValue().getNarrower()) {
                Concept parent = concepts.get(entry.getKey());
                Concept child =  concepts.get(narrower.getValue());
                ConceptHierarchy relation = new ConceptHierarchy(parent,child);
                conceptRelationRepository.save(relation);
            }
        }

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

    public Optional<Concept> findByExternalId(String conceptExternalId) {
        return conceptRepository.findByExternalId(conceptExternalId);
    }
}
