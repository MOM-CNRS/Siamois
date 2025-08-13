package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.GlobalFieldConfig;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.database.repositories.FieldRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing field configurations of the vocabulary.
 */
@Slf4j
@Service
public class FieldConfigurationService {

    private static final IllegalStateException FIELD_CODE_NOT_FOUND = new IllegalStateException("Field code not found");
    private final ConceptApi conceptApi;
    private final FieldService fieldService;
    private final FieldRepository fieldRepository;
    private final ConceptRepository conceptRepository;
    private final ConceptService conceptService;

    private final LabelService labelService;


    public FieldConfigurationService(ConceptApi conceptApi,
                                     FieldService fieldService,
                                     FieldRepository fieldRepository,
                                     ConceptRepository conceptRepository,
                                     ConceptService conceptService, LabelService labelService) {
        this.conceptApi = conceptApi;
        this.fieldService = fieldService;
        this.fieldRepository = fieldRepository;
        this.conceptRepository = conceptRepository;
        this.conceptService = conceptService;
        this.labelService = labelService;
    }

    private boolean containsFieldCode(FullInfoDTO conceptDTO) {
        return conceptDTO.getFieldcode().isPresent();
    }

    /**
     * Sets up the field configuration for an institution based on the vocabulary.
     *
     * @param info       the user information containing institution details
     * @param vocabulary the vocabulary to use for configuration
     * @return an Optional containing GlobalFieldConfig if the configuration is wrong, otherwise empty
     * @throws NotSiamoisThesaurusException      if the vocabulary is not a Siamois thesaurus
     * @throws ErrorProcessingExpansionException if there is an error processing the vocabulary expansion
     */
    public Optional<GlobalFieldConfig> setupFieldConfigurationForInstitution(UserInfo info, Vocabulary vocabulary) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        return setupFieldConfigurationForInstitution(info.getInstitution(), vocabulary);
    }

    /**
     * Sets up the field configuration for an institution based on the vocabulary.
     *
     * @param institution       the institution
     * @param vocabulary the vocabulary to use for configuration
     * @return an Optional containing GlobalFieldConfig if the configuration is wrong, otherwise empty
     * @throws NotSiamoisThesaurusException      if the vocabulary is not a Siamois thesaurus
     * @throws ErrorProcessingExpansionException if there is an error processing the vocabulary expansion
     */
    public Optional<GlobalFieldConfig> setupFieldConfigurationForInstitution(Institution institution, Vocabulary vocabulary) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        ConceptBranchDTO conceptBranchDTO = conceptApi.fetchFieldsBranch(vocabulary);
        GlobalFieldConfig config = createConfigOfThesaurus(conceptBranchDTO);
        if (config.isWrongConfig()) return Optional.of(config);

        for (FullInfoDTO conceptDTO : config.conceptWithValidFieldCode()) {
            Concept concept = conceptService.saveOrGetConceptFromFullDTO(vocabulary, conceptDTO);
            String fieldCode = conceptDTO.getFieldcode().orElseThrow(() -> FIELD_CODE_NOT_FOUND);

            int rowAffected = fieldRepository.updateConfigForFieldOfInstitution(institution.getId(), fieldCode, concept.getId());
            if (rowAffected == 0) {
                fieldRepository.saveConceptForFieldOfInstitution(institution.getId(), fieldCode, concept.getId());
            }
        }

        return Optional.empty();
    }

    private GlobalFieldConfig createConfigOfThesaurus(ConceptBranchDTO conceptBranchDTO) {
        final List<String> existingFieldCodes = fieldService.searchAllFieldCodes();
        final List<FullInfoDTO> allConceptsWithPotentialFieldCode = conceptBranchDTO.getData().values().stream()
                .filter(this::containsFieldCode)
                .toList();

        final List<String> missingFieldCode = existingFieldCodes.stream()
                .filter(fieldCode -> allConceptsWithPotentialFieldCode.stream()
                        .map(concept -> concept.getFieldcode().orElseThrow(() -> FIELD_CODE_NOT_FOUND).toUpperCase())
                        .noneMatch(fieldCode::equals))
                .toList();

        final List<FullInfoDTO> validConcept = allConceptsWithPotentialFieldCode.stream()
                .filter(concept -> {
                    String fieldCode = concept.getFieldcode().orElseThrow(() -> FIELD_CODE_NOT_FOUND).toUpperCase();
                    return existingFieldCodes.contains(fieldCode);
                })
                .toList();

        return new GlobalFieldConfig(missingFieldCode, validConcept);
    }

    /**
     * Sets up the field configuration for a user based on the vocabulary.
     *
     * @param info       the user information containing institution and user details
     * @param vocabulary the vocabulary to use for configuration
     * @return an Optional containing GlobalFieldConfig if the configuration is wrong, otherwise empty
     * @throws NotSiamoisThesaurusException      if the vocabulary is not a Siamois thesaurus
     * @throws ErrorProcessingExpansionException if there is an error processing the vocabulary expansion
     */
    public Optional<GlobalFieldConfig> setupFieldConfigurationForUser(UserInfo info, Vocabulary vocabulary) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        ConceptBranchDTO conceptBranchDTO = conceptApi.fetchFieldsBranch(vocabulary);
        GlobalFieldConfig config = createConfigOfThesaurus(conceptBranchDTO);
        if (config.isWrongConfig()) return Optional.of(config);

        for (FullInfoDTO conceptDTO : config.conceptWithValidFieldCode()) {
            Concept concept = conceptService.saveOrGetConceptFromFullDTO(vocabulary, conceptDTO);
            String fieldCode = conceptDTO.getFieldcode().orElseThrow(() -> FIELD_CODE_NOT_FOUND);

            int rowAffected = fieldRepository.updateConfigForFieldOfUser(info.getInstitution().getId(),
                    info.getUser().getId(),
                    fieldCode,
                    concept.getId());
            if (rowAffected == 0) {
                fieldRepository.saveConceptForFieldOfUser(info.getInstitution().getId(),
                        info.getUser().getId(),
                        fieldCode,
                        concept.getId());
            }
        }

        return Optional.empty();
    }

    /**
     * Finds the vocabulary URL for a given institution.
     *
     * @param institution the institution for which to find the vocabulary URL
     * @return an Optional containing the vocabulary URL if found, otherwise empty
     */
    public Optional<String> findVocabularyUrlOfInstitution(Institution institution) {
        Optional<Concept> optConcept = conceptRepository
                .findTopTermConfigForFieldCodeOfInstitution(institution.getId(), SpatialUnit.CATEGORY_FIELD_CODE);
        if (optConcept.isEmpty()) return Optional.empty();
        Vocabulary vocabulary = optConcept.get().getVocabulary();
        return Optional.of(vocabulary.getBaseUri() + "/?idt=" + vocabulary.getExternalVocabularyId());
    }

    /**
     * Finds the configuration for a specific field code for a user.
     *
     * @param info      the user information containing institution and user details
     * @param fieldCode the field code for which to find the configuration
     * @return the Concept associated with the field code
     * @throws NoConfigForFieldException if no configuration is found for the field code
     */
    public Concept findConfigurationForFieldCode(UserInfo info, String fieldCode) throws NoConfigForFieldException {
        Optional<Concept> optConcept = conceptRepository
                .findTopTermConfigForFieldCodeOfUser(info.getInstitution().getId(),
                        info.getUser().getId(),
                        fieldCode);

        if (optConcept.isPresent()) return optConcept.get();

        optConcept = conceptRepository
                .findTopTermConfigForFieldCodeOfInstitution(info.getInstitution().getId(), fieldCode);

        if (optConcept.isEmpty())
            throw new NoConfigForFieldException(info, fieldCode);

        return optConcept.get();
    }

    /**
     * Gets the URL of a concept based on its vocabulary and external ID.
     *
     * @param c the Concept for which to get the URL
     * @return the URL of the concept
     */
    public String getUrlOfConcept(Concept c) {
        return c.getVocabulary().getBaseUri() + "/?idc=" + c.getExternalId() + "&idt=" + c.getVocabulary().getExternalVocabularyId();
    }

    /**
     * Gets the URL for a specific field code for a user.
     *
     * @param info      the user information containing institution and user details
     * @param fieldCode the field code for which to get the URL
     * @return the URL of the concept associated with the field code, or null if no configuration is found
     */
    public String getUrlForFieldCode(UserInfo info, String fieldCode) {
        try {
            return getUrlOfConcept(findConfigurationForFieldCode(info, fieldCode));
        } catch (NoConfigForFieldException e) {
            return null;
        }
    }

    /**
     * Fetches autocomplete suggestions for concepts under a given parent concept based on user input.
     *
     * @param info          the user information containing language settings
     * @param parentConcept the parent concept under which to search for children concepts
     * @param input         the user input to filter concepts
     * @return a list of concepts that match the input
     */
    public List<Concept> fetchAutocomplete(UserInfo info, Concept parentConcept, String input) {
        try {
            List<Concept> candidates = conceptService.findDirectSubConceptOf(parentConcept);
            if (StringUtils.isEmpty(input)) return candidates;

            return candidates
                    .stream()
                    .filter(c -> labelContainsInputIgnoreCase(info, input, c))
                    .toList();

        } catch (ErrorProcessingExpansionException e) {
            log.debug("Error fetching children concepts for autocomplete", e);
            return List.of();
        }
    }

    private boolean labelContainsInputIgnoreCase(UserInfo info, String input, Concept c) {
        return labelService.findLabelOf(c, info.getLang()).getValue().toLowerCase().contains(input.toLowerCase());
    }

    /**
     * Fetches autocomplete suggestions for a specific field code based on user input.
     *
     * @param info      the user information containing institution and user details
     * @param fieldCode the field code for which to fetch autocomplete suggestions
     * @param input     the user input to filter concepts
     * @return a list of concepts that match the input
     * @throws NoConfigForFieldException if no configuration is found for the field code
     */
    public List<Concept> fetchAutocomplete(UserInfo info, String fieldCode, String input) throws NoConfigForFieldException {
        Concept parentConcept = findConfigurationForFieldCode(info, fieldCode);
        return fetchAutocomplete(info, parentConcept, input);
    }


}
