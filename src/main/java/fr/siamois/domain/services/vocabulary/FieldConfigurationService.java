package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.GlobalFieldConfig;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.database.repositories.FieldRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptFieldConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.model.LazyDataModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for managing field configurations of the vocabulary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldConfigurationService {

    private static final IllegalStateException FIELD_CODE_NOT_FOUND = new IllegalStateException("Field code not found");
    private final ConceptApi conceptApi;
    private final FieldService fieldService;
    private final FieldRepository fieldRepository;
    private final ConceptRepository conceptRepository;
    private final ConceptService conceptService;

    private final LabelService labelService;
    private final ConceptFieldConfigRepository conceptFieldConfigRepository;
    private final LocalizedConceptDataRepository localizedConceptDataRepository;

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
            String fieldCode = conceptDTO.getFieldcode().orElseThrow(() -> FIELD_CODE_NOT_FOUND);
            Concept concept = conceptService.saveOrGetConceptFromFullDTO(vocabulary, conceptDTO, null);
            ConceptFieldConfig fieldConfig;
            Optional<ConceptFieldConfig> optConfig = conceptFieldConfigRepository.findByFieldCodeForInstitution(institution.getId(), fieldCode);
            if (optConfig.isEmpty()) {
                fieldConfig = new ConceptFieldConfig();
                fieldConfig.setInstitution(institution);
                fieldConfig.setFieldCode(fieldCode);
                fieldConfig.setConcept(concept);
                fieldConfig = conceptFieldConfigRepository.save(fieldConfig);
            } else {
                fieldConfig = optConfig.get();
            }
            conceptService.saveAllSubConceptOfIfUpdated(fieldConfig);
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
            String fieldCode = conceptDTO.getFieldcode().orElseThrow(() -> FIELD_CODE_NOT_FOUND);

            Concept concept = conceptService.saveOrGetConceptFromFullDTO(vocabulary, conceptDTO, null);
            ConceptFieldConfig fieldConfig;
            Optional<ConceptFieldConfig> optConfig = conceptFieldConfigRepository.findByFieldCodeForUser(info.getUser().getId(), fieldCode);
            if (optConfig.isEmpty()) {
                fieldConfig = new ConceptFieldConfig();
                fieldConfig.setInstitution(info.getInstitution());
                fieldConfig.setUser(info.getUser());
                fieldConfig.setFieldCode(fieldCode);
                fieldConfig.setConcept(concept);
                fieldConfig = conceptFieldConfigRepository.save(fieldConfig);
            } else {
                fieldConfig = optConfig.get();
            }
            conceptService.saveAllSubConceptOfIfUpdated(fieldConfig);
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
    public Concept findParentConceptForFieldcode(UserInfo info, String fieldCode) throws NoConfigForFieldException {
        return findConfigurationForFieldCode(info, fieldCode).getConcept();
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
            return getUrlOfConcept(findParentConceptForFieldcode(info, fieldCode));
        } catch (NoConfigForFieldException e) {
            return null;
        }
    }

    public ConceptFieldConfig findConfigurationForFieldCode(UserInfo info, String fieldCode) throws NoConfigForFieldException {
        Optional<ConceptFieldConfig> institutionConfig = conceptFieldConfigRepository.findByFieldCodeForInstitution(info.getInstitution().getId(), fieldCode);
        if (institutionConfig.isEmpty()) {
            Optional<ConceptFieldConfig> personConfig = conceptFieldConfigRepository.findByFieldCodeForUser(info.getUser().getId(),  fieldCode);
            if (personConfig.isEmpty()) {
                throw new NoConfigForFieldException(fieldCode);
            }
            return personConfig.get();
        }
        return institutionConfig.get();
    }

    public Set<Concept> fetchAutocomplete(UserInfo info, String fieldCode, String input) throws NoConfigForFieldException {
        try {
            ConceptFieldConfig config = findConfigurationForFieldCode(info, fieldCode);
            conceptService.saveAllSubConceptOfIfUpdated(config);
            return labelService.findMatchingConcepts(config.getConcept(), info.getLang(), input);
        } catch (ErrorProcessingExpansionException e) {
            log.error(e.getMessage());
            return Set.of();
        }
    }

}
