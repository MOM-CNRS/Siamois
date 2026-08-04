package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.events.publisher.ConceptChangeEventPublisher;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptHierarchy;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.dto.entity.vocabulary.*;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.*;
import fr.siamois.infrastructure.api.dto.concept.ConceptAutocompleteDetachedDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptRemoteAutocompleteDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptFieldConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptHierarchyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import fr.siamois.utils.vocabulary.ConceptApiUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final LocalizedConceptDataRepository localizedConceptDataRepository;
    private final ConceptChangeEventPublisher conceptChangeEventPublisher;
    private final ConceptLabelRepository conceptLabelRepository;
    private final ConceptHierarchyRepository conceptHierarchyRepository;
    private final ConversionService conversionService;
    private final ConceptFieldConfigRepository conceptFieldConfigRepository;

    private static final String DEFAULT_LABEL_RESOLUTION_LANG = "fr";
    private final ApplicationContext applicationContext;

    /**
     * Resolves a concept by exact label (case/accent-insensitive) within the institution's
     * configured thesaurus subtree for the given field code. Used as a fallback when an import
     * column provides a label instead of a concept URI.
     *
     * @param institutionId the institution whose field configuration to use
     * @param fieldCode     the field code (e.g. RecordingUnit.TYPE_FIELD_CODE) identifying which configured thesaurus subtree to search
     * @param label         the label to match exactly
     * @return the matching concept
     * @throws IllegalStateException if no thesaurus is configured for the field, or if the label matches zero or more than one concept
     */
    @NonNull
    public Concept resolveConceptByLabel(@NonNull Long institutionId, @NonNull String fieldCode, @NonNull String label) {
        ConceptFieldConfig config = conceptFieldConfigRepository.findOneByFieldCodeForInstitution(institutionId, fieldCode)
                .orElseThrow(() -> new IllegalStateException("Aucun thésaurus configuré pour ce champ"));

        List<Concept> matches = conceptRepository.findAllByFieldContextAndExactLabel(
                config.getConcept().getId(), DEFAULT_LABEL_RESOLUTION_LANG, label);

        if (matches.isEmpty()) {
            throw new IllegalStateException("Concept '" + label + "' introuvable dans le thésaurus configuré");
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Label '" + label + "' ambigu (" + matches.size() + " concepts correspondants)");
        }
        return matches.get(0);
    }

    /**
     * Saves a concept if it does not already exist in the repository.
     *
     * @param conceptDTO the concept to save or retrieve
     * @return the saved or existing concept
     */
    @NonNull
    @Transactional
    public Concept saveOrGetConcept(@NonNull ConceptDTO conceptDTO) {
        Concept concept = conversionService.convert(conceptDTO, Concept.class);
        assert concept != null;
        Vocabulary vocabulary = concept.getVocabulary();
        Optional<Concept> optConcept = conceptRepository.findConceptByExternalIdIgnoreCase(
                vocabulary.getExternalVocabularyId(), concept.getExternalId());
        return optConcept.orElseGet(() -> conceptRepository.save(concept));
    }

    /**
     * Saves a concept if it does not already exist in the repository.
     *
     * @param concept the concept to save or retrieve
     * @return the saved or existing concept
     */
    @NonNull
    @Transactional
    public Concept saveOrGetConcept(@NonNull Concept concept) {
        Vocabulary vocabulary = concept.getVocabulary();
        Optional<Concept> optConcept = conceptRepository.findConceptByExternalIdIgnoreCase(
                vocabulary.getExternalVocabularyId(), concept.getExternalId());
        return optConcept.orElseGet(() -> conceptRepository.save(concept));
    }



    /**
     * Finds all concepts related to action units of a given institution.
     *
     * @param institution the institution for which to find concepts
     * @return a list of concepts associated with action units of the institution
     */
    @NonNull
    public List<Concept> findAllByActionUnitOfInstitution(@NonNull Institution institution) {
        return conceptRepository.findAllByActionUnitOfInstitution(institution.getId());
    }

    /**
     * Saves or retrieves a concept based on the provided FullInfoDTO.
     *
     * @param vocabulary         The vocabulary to which the concept belongs
     * @param conceptDTO         The FullInfoDTO containing concept information
     * @param fieldParentConcept The parent concept in the field context, if applicable
     * @return the saved or existing concept
     */
    @NonNull
    public Concept saveOrGetConceptFromFullDTO(@NonNull Vocabulary vocabulary, @NonNull FullInfoDTO conceptDTO, @Nullable Concept fieldParentConcept) {
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
     * Updates all labels of a saved concept from FullInfoDTO
     *
     * @param savedConcept       the concept to update
     * @param conceptDto         the FullInfoDTO containing label information
     * @param fieldParentConcept The parent concept in the field context, if applicable
     */
    public void updateAllLabelsFromDTO(@NonNull Concept savedConcept, @NonNull FullInfoDTO conceptDto, @Nullable Concept fieldParentConcept) {
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
        LocalizedConceptData localizedConceptData;
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
     * Gets localized concept data by concept and language code.
     *
     * @param concept the concept
     * @param lang    the language code
     * @return the localized concept data, or null if not found
     */
    @Nullable
    public LocalizedConceptData getLocalizedConceptDataByConceptAndLangCode(@NonNull Concept concept, @NonNull String lang) {
        return localizedConceptDataRepository.findByConceptAndLangCode(concept.getId(), lang).orElse(null);
    }

    /**
     * Updates all definitions of a saved concept from FullInfoDTO
     *
     * @param savedConcept the concept to update
     * @param conceptDto   the FullInfoDTO containing definition information
     */
    public void updateAllDefinitionsFromDTO(@NonNull Concept savedConcept, @NonNull FullInfoDTO conceptDto) {
        if (conceptDto.getDefinition() != null) {
            for (PurlInfoDTO definition : conceptDto.getDefinition()) {
                updateDefinition(savedConcept, definition.getLang(), definition.getValue());
            }
        }
    }

    public void saveAllSubConceptOfIfUpdated(@NonNull ConceptFieldConfig config) throws ErrorProcessingExpansionException {
        saveAllSubConceptOfIfUpdated(config, new ProgressWrapper());
    }


        /**
         * Saves all sub-concepts data and relations if there are updates in the down expansion of the concept field config.
         *
         * @param config          the concept field configuration
         * @param progressWrapper the progress wrapper to track progress
         * @throws ErrorProcessingExpansionException if an error occurs during processing
         */
    public void saveAllSubConceptOfIfUpdated(@NonNull ConceptFieldConfig config, @NonNull ProgressWrapper progressWrapper) throws ErrorProcessingExpansionException {
        log.trace("API call to fetch down expansion for concept FieldCode : {}", config.getFieldCode());
        try {
            Concept parentSavedConcept = config.getConcept();
            Vocabulary vocabulary = parentSavedConcept.getVocabulary();
            ConceptBranchDTO branchDTO = conceptApi.fetchDownExpansion(config);
            progressWrapper.incrementStep();
            if (branchDTO == null) {
                progressWrapper.incrementStep(4);
                log.trace("No update found for concept FieldCode : {}", config.getFieldCode());
                return;
            }
            conceptChangeEventPublisher.publishEvent(config.getFieldCode());

            Map<String, Concept> urlToSavedConceptMap = new HashMap<>();
            FullInfoDTO parentConcept = findAndSetParentConceptDTO(branchDTO, parentSavedConcept);
            progressWrapper.incrementStep();
            if (parentConcept.getNarrower() == null) {
                progressWrapper.incrementStep(3);
                return;
            }

            saveOrGetAllConceptsFromBranchAndStoreInMap(config, branchDTO, urlToSavedConceptMap, vocabulary);
            progressWrapper.incrementStep();

            ConceptApiUtils.BranchLoadComponents components = new ConceptApiUtils.BranchLoadComponents(applicationContext);
            ConceptApiUtils.saveAllConceptsOfBranch(components, vocabulary, branchDTO, urlToSavedConceptMap);
            progressWrapper.incrementStep();

            processDeletedConcepts(urlToSavedConceptMap, parentSavedConcept);
            progressWrapper.incrementStep();

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
        log.trace("Mark as deleted {} concepts for parent concept {} in {}", deletedConcepts.size(), parentSavedConcept.getExternalId(), parentSavedConcept.getVocabulary().getExternalVocabularyId());
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
     * Finds a concept by its ID.
     * @param id the ID of the concept
     * @return an Optional containing the concept if found, or empty if not found
     */
    @NonNull
    public Optional<Concept> findById(long id) {
        return conceptRepository.findById(id);
    }

    /**
     * Finds all parent concepts of a given concept within the context of a specified parent field concept.
     *
     * @param concept             the concept whose parents are to be found
     * @param parentFieldConcept  the parent field concept defining the context
     * @return a list of parent concepts, ordered from the highest level to the immediate parent
     */
    @NonNull
    public List<Concept> findParentsOfConceptInField(@NonNull Concept concept, @NonNull Concept parentFieldConcept) {
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

    /**
     * Fetches autocomplete suggestions straight from a remote thesaurus, for concepts that are not
     * imported in the local database.
     * The results carry no language, since the remote autocomplete does not tell which language each
     * label is written in, and no hierarchy, since it does not expose the ancestors of a concept.
     *
     * @param vocabularyDTO the remote vocabulary to search into
     * @param input         the input string to match against concept labels
     * @return a list of matching ConceptAutocompleteDetachedDTO, one per matching label
     */
    @NonNull
    public List<ConceptAutocompleteDetachedDTO> fetchAutocompleteFromRemoteThesaurus(VocabularyDTO vocabularyDTO, String input) {
        if (input == null || input.isBlank()) {
            return Collections.emptyList();
        }
        List<ConceptRemoteAutocompleteDTO> autocompleteDTOS = conceptApi.fetchRemoteAutocomplete(vocabularyDTO.getBaseUri(), vocabularyDTO.getExternalVocabularyId(), input);
        Map<Long, List<ConceptRemoteAutocompleteDTO>> conceptIdToResults = new LinkedHashMap<>();
        Map<Long, ConceptRemoteAutocompleteDTO> conceptIdToPrefLabel = new HashMap<>();
        for (ConceptRemoteAutocompleteDTO autocompleteDTO : autocompleteDTOS) {
            if (!conceptIdToResults.containsKey(autocompleteDTO.identifier())) {
                conceptIdToResults.put(autocompleteDTO.identifier(), new ArrayList<>());
            }
            if (!isAltLabel(autocompleteDTO)) {
                conceptIdToPrefLabel.put(autocompleteDTO.identifier(), autocompleteDTO);
            }
            conceptIdToResults.get(autocompleteDTO.identifier()).add(autocompleteDTO);
        }

        List<ConceptAutocompleteDetachedDTO> results = new ArrayList<>();
        for (Map.Entry<Long, List<ConceptRemoteAutocompleteDTO>> entry : conceptIdToResults.entrySet()) {
            results.addAll(detachedResultsOfConcept(vocabularyDTO, entry.getValue(), conceptIdToPrefLabel.get(entry.getKey())));
        }
        return results;
    }

    /**
     * Turns every label matching the input for one remote concept into its own autocomplete result.
     * All of them share the labels and the definition of that concept, so the caller displays the same
     * information whichever label was matched.
     *
     * @param vocabularyDTO   the vocabulary the concept belongs to
     * @param labelsOfConcept the matching labels of that concept, in the order the thesaurus returned them
     * @param prefLabel       the preferred label of that concept, null when only alt labels matched
     * @return one result per label, or an empty list when the concept cannot be identified
     */
    @NonNull
    private List<ConceptAutocompleteDetachedDTO> detachedResultsOfConcept(@NonNull VocabularyDTO vocabularyDTO,
                                                                         @NonNull List<ConceptRemoteAutocompleteDTO> labelsOfConcept,
                                                                         @Nullable ConceptRemoteAutocompleteDTO prefLabel) {
        // The pref label carries the display name of the concept. When it did not match the input, the
        // thesaurus only returned alt labels : the first one then stands for the concept.
        ConceptRemoteAutocompleteDTO reference = prefLabel != null ? prefLabel : labelsOfConcept.get(0);

        String externalId = ConceptApiUtils.externalIdFromUri(reference.uri());
        if (externalId == null) {
            log.warn("Ignoring remote autocomplete result '{}' : no concept id could be read from its URI {}", reference.label(), reference.uri());
            return List.of();
        }

        ConceptDTO concept = ConceptDTO.builder()
                .externalId(externalId)
                .vocabulary(vocabularyDTO)
                .deleted(false)
                .build();

        List<String> altLabels = labelsOfConcept.stream()
                .filter(ConceptService::isAltLabel)
                .map(ConceptRemoteAutocompleteDTO::label)
                .toList();

        String definition = labelsOfConcept.stream()
                .map(ConceptRemoteAutocompleteDTO::definition)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("");

        List<ConceptAutocompleteDetachedDTO> results = new ArrayList<>();
        for (ConceptRemoteAutocompleteDTO matchingLabel : labelsOfConcept) {
            results.add(new ConceptAutocompleteDetachedDTO(
                    labelToDisplay(matchingLabel, concept),
                    reference.label(),
                    altLabels,
                    definition,
                    vocabularyDTO.completeUri()));
        }
        return results;
    }

    @NonNull
    private ConceptLabelDTO labelToDisplay(@NonNull ConceptRemoteAutocompleteDTO remoteLabel, @NonNull ConceptDTO concept) {
        ConceptLabelDTO label = isAltLabel(remoteLabel) ? new ConceptAltLabelDTO() : new ConceptPrefLabelDTO();
        label.setConcept(concept);
        label.setVocabulary(concept.getVocabulary());
        label.setLabel(remoteLabel.label());
        return label;
    }

    private static boolean isAltLabel(@NonNull ConceptRemoteAutocompleteDTO remoteLabel) {
        return Boolean.TRUE.equals(remoteLabel.isAltLabel());
    }
}
