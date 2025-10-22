package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.PurlInfoDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRelationRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for managing concepts in the vocabulary.
 * This service provides methods to save, retrieve, and update concepts,
 * as well as to find concepts related to spatial and action units of an institution.
 */
@Service
public class ConceptService {

    private final ConceptRepository conceptRepository;
    private final ConceptApi conceptApi;
    private final LabelService labelService;
    private final ConceptRelationRepository conceptRelationRepository;

    public ConceptService(ConceptRepository conceptRepository, ConceptApi conceptApi, LabelService labelService, ConceptRelationRepository conceptRelationRepository) {
        this.conceptRepository = conceptRepository;
        this.conceptApi = conceptApi;
        this.labelService = labelService;
        this.conceptRelationRepository = conceptRelationRepository;
    }

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
    public Concept saveOrGetConceptFromFullDTO(Vocabulary vocabulary, FullInfoDTO conceptDTO) {
        Optional<Concept> optConcept = conceptRepository
                .findConceptByExternalIdIgnoreCase(
                        vocabulary.getExternalVocabularyId(),
                        conceptDTO.getIdentifier()[0].getValue()
                );

        if (optConcept.isPresent()) {
            updateAllLabelsFromDTO(optConcept.get(), conceptDTO);
            updateAllDefinitionsFromDTO(optConcept.get(), conceptDTO);
            return optConcept.get();
        }

        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId(conceptDTO.getIdentifier()[0].getValue());

        concept = conceptRepository.save(concept);

        updateAllLabelsFromDTO(concept, conceptDTO);
        updateAllDefinitionsFromDTO(concept, conceptDTO);

        return concept;
    }

    /**
     * Updates all labels of a saved concept from a FullInfoDTO.
     *
     * @param savedConcept the concept to update
     * @param conceptDto   the FullInfoDTO containing label information
     */
    public void updateAllLabelsFromDTO(Concept savedConcept, FullInfoDTO conceptDto) {
        if (conceptDto.getPrefLabel() != null) {
            for (PurlInfoDTO label : conceptDto.getPrefLabel()) {
                labelService.updateLabel(savedConcept, label.getLang(), label.getValue());
            }
        }
    }

    /**
     * Updates all definitions of a saved concept from FullInfoDTO
     * @param savedConcept
     * @param conceptDto
     */
    public void updateAllDefinitionsFromDTO(Concept savedConcept, FullInfoDTO conceptDto) {
        // TODO: Implement definition LOADING
    }

    /**
     * Finds all direct sub-concepts of a given concept.
     *
     * @param concept the concept for which to find direct sub-concepts
     * @return a list of direct sub-concepts
     * @throws ErrorProcessingExpansionException if there is an error processing the expansion of concepts
     */
    @Deprecated(forRemoval = true)
    public List<Concept> findDirectSubConceptOf(Concept concept) throws ErrorProcessingExpansionException {
        ConceptBranchDTO branch = conceptApi.fetchDownExpansion(concept.getVocabulary(), concept.getExternalId());
        List<Concept> result = new ArrayList<>();
        if (branch.isEmpty()) {
            return result;
        }

        FullInfoDTO parentConcept = branch.getData().values().stream()
                .filter(dto -> concept.getExternalId().equalsIgnoreCase(dto.getIdentifier()[0].getValue()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No concept found for " + concept.getExternalId()));

        if (parentConcept.getNarrower() == null) return result;

        List<FullInfoDTO> childs = Arrays.stream(parentConcept.getNarrower())
                .filter((purlInfoDTO -> branch.getData().containsKey(purlInfoDTO.getValue())))
                .map((purlInfoDTO -> branch.getData().get(purlInfoDTO.getValue())))
                .toList();

        for (FullInfoDTO child : childs) {
            Concept newConcept = saveOrGetConceptFromFullDTO(concept.getVocabulary(), child);
            result.add(newConcept);
        }

        return result;
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
            concepts.put(entry.getKey(), saveOrGetConceptFromFullDTO(vocabulary, entry.getValue()));
        }

        for (Map.Entry<String, FullInfoDTO> entry : branchDTO.getData().entrySet()) {
            for (PurlInfoDTO narrower : entry.getValue().getNarrower()) {
                Concept parent = concepts.get(entry.getKey());
                Concept child =  concepts.get(narrower.getValue());
                ConceptRelation relation = new  ConceptRelation(parent,child);
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
}
