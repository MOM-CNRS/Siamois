package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.FeedbackFieldConfig;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.AutocompleteRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptFieldConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing field configurations of the vocabulary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldConfigurationService {

    private static final IllegalStateException FIELD_CODE_NOT_FOUND = new IllegalStateException("Field code not found");
    public static final int LIMIT_RESULTS = 200;
    public static final int NUMBER_OF_STEPS_IN_FIELD_CONFIG = 2;
    public static final int NUMBER_OF_STEPS_IN_CONCEPT_UPDATE = 5;
    private final ConceptApi conceptApi;
    private final FieldService fieldService;
    private final ConceptRepository conceptRepository;
    private final ConceptService conceptService;

    private final ConceptFieldConfigRepository conceptFieldConfigRepository;
    private final AutocompleteRepository autocompleteRepository;

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
    public Optional<FeedbackFieldConfig> setupFieldConfigurationForInstitution(UserInfo info, Vocabulary vocabulary, ProgressWrapper progressWrapper) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        return setupFieldConfigurationForInstitution(info.getInstitution(), vocabulary, progressWrapper);
    }

    /**
     * Sets up the field configuration for an institution based on the vocabulary.
     *
     * @param institution the institution
     * @param vocabulary  the vocabulary to use for configuration
     * @return an Optional containing GlobalFieldConfig if the configuration is wrong, otherwise empty
     * @throws NotSiamoisThesaurusException      if the vocabulary is not a Siamois thesaurus
     * @throws ErrorProcessingExpansionException if there is an error processing the vocabulary expansion
     */
    public Optional<FeedbackFieldConfig> setupFieldConfigurationForInstitution(Institution institution, Vocabulary vocabulary, ProgressWrapper progressWrapper) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        return setupFieldConfiguration(institution, null, vocabulary, progressWrapper);
    }

    /**
     * Wrapper to call {@link #setupFieldConfigurationForInstitution(Institution, Vocabulary, ProgressWrapper)} without progress tracking.
     *
     * @param info       the user information containing institution details
     * @param vocabulary the vocabulary to use for configuration
     * @return an Optional containing GlobalFieldConfig if the configuration is wrong, otherwise empty
     * @throws NotSiamoisThesaurusException      if the vocabulary is not a Siamois thesaurus
     * @throws ErrorProcessingExpansionException if there is an error processing the vocabulary expansion
     */
    @NonNull
    public Optional<FeedbackFieldConfig> setupFieldConfigurationForInstitution(@NonNull UserInfo info, @NonNull Vocabulary vocabulary) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        return setupFieldConfigurationForInstitution(info.getInstitution(), vocabulary, new ProgressWrapper());
    }

    /**
     * Wrapper to call {@link #setupFieldConfigurationForInstitution(Institution, Vocabulary, ProgressWrapper)} without progress tracking.
     *
     * @param institution the institution
     * @param vocabulary  the vocabulary to use for configuration
     * @return an Optional containing GlobalFieldConfig if the configuration is wrong, otherwise empty
     * @throws NotSiamoisThesaurusException      if the vocabulary is not a Siamois thesaurus
     * @throws ErrorProcessingExpansionException if there is an error processing the vocabulary expansion
     */
    @NonNull
    public Optional<FeedbackFieldConfig> setupFieldConfigurationForInstitution(@NonNull Institution institution, @NonNull Vocabulary vocabulary) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        return setupFieldConfiguration(institution, null, vocabulary, new ProgressWrapper());
    }

    private FeedbackFieldConfig createConfigOfThesaurus(ConceptBranchDTO conceptBranchDTO) {
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

        return new FeedbackFieldConfig(missingFieldCode, validConcept);
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
    @NonNull
    public Optional<FeedbackFieldConfig> setupFieldConfigurationForUser(@NonNull UserInfo info, @NonNull Vocabulary vocabulary) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        return setupFieldConfiguration(info.getInstitution(), info.getUser(), vocabulary, new ProgressWrapper());
    }

    /**
     * Sets up the field configuration for a user based on the vocabulary.
     *
     * @param info            the user information containing institution and user details
     * @param vocabulary      the vocabulary to use for configuration
     * @param progressWrapper the progress wrapper to track progress
     * @return an Optional containing GlobalFieldConfig if the configuration is wrong, otherwise empty
     * @throws NotSiamoisThesaurusException      if the vocabulary is not a Siamois thesaurus
     * @throws ErrorProcessingExpansionException if there is an error processing the vocabulary expansion
     */
    @NonNull
    public Optional<FeedbackFieldConfig> setupFieldConfigurationForUser(@NonNull UserInfo info, @NonNull Vocabulary vocabulary, ProgressWrapper progressWrapper) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        return setupFieldConfiguration(info.getInstitution(), info.getUser(), vocabulary, progressWrapper);
    }

    private Optional<FeedbackFieldConfig> setupFieldConfiguration(@NonNull Institution institution,
                                                                  @Nullable Person user,
                                                                  @NonNull Vocabulary vocabulary,
                                                                  @NonNull ProgressWrapper progressWrapper) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        ConceptBranchDTO conceptBranchDTO = conceptApi.fetchFieldsBranch(vocabulary);
        FeedbackFieldConfig config = createConfigOfThesaurus(conceptBranchDTO);
        if (config.isWrongConfig()) return Optional.of(config);

        progressWrapper.setTotalSteps((config.conceptWithValidFieldCode().size() * (NUMBER_OF_STEPS_IN_FIELD_CONFIG + NUMBER_OF_STEPS_IN_CONCEPT_UPDATE)));

        for (FullInfoDTO conceptDTO : config.conceptWithValidFieldCode()) {
            String fieldCode = conceptDTO.getFieldcode().orElseThrow(() -> FIELD_CODE_NOT_FOUND);

            Concept concept = conceptService.saveOrGetConceptFromFullDTO(vocabulary, conceptDTO, null);
            ConceptFieldConfig fieldConfig;
            Optional<ConceptFieldConfig> optConfig;

            if (user == null) { // TODO FIX HERE ITS FETCHING ONLY ON CONCEPT ID AND ITS NOT UNIQUE !!!
                optConfig = conceptFieldConfigRepository.findOneByFieldCodeForInstitution(institution.getId(), fieldCode);
            } else {
                optConfig = conceptFieldConfigRepository.findOneByFieldCodeForUser(user.getId(), institution.getId(), fieldCode);
            }

            if (optConfig.isEmpty()) {
                fieldConfig = new ConceptFieldConfig();
                fieldConfig.setInstitution(institution);
                fieldConfig.setUser(user);
                fieldConfig.setFieldCode(fieldCode);

                fieldConfig = conceptFieldConfigRepository.save(fieldConfig);
            } else {
                fieldConfig = optConfig.get();
            }

            fieldConfig.setConcept(concept); // update concept also if there is already a field config

            progressWrapper.incrementStep();
            conceptService.saveAllSubConceptOfIfUpdated(fieldConfig, progressWrapper);
            progressWrapper.incrementStep();
        }

        return Optional.empty();
    }

    /**
     * Finds the vocabulary URL for a given institution.
     *
     * @param institution the institution for which to find the vocabulary URL
     * @return an Optional containing the vocabulary URL if found, otherwise empty
     */
    @NonNull
    public Optional<String> findVocabularyUrlOfInstitution(@NonNull Institution institution) {
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
    @NonNull
    public Concept findParentConceptForFieldcode(@NonNull UserInfo info, @NonNull String fieldCode) throws NoConfigForFieldException {
        Concept concept = findConfigurationForFieldCode(info, fieldCode).getConcept();
        Hibernate.initialize(concept.getVocabulary());
        return concept;
    }

    /**
     * Gets the URL of a concept based on its vocabulary and external ID.
     *
     * @param c the Concept for which to get the URL
     * @return the URL of the concept
     */
    @NonNull
    public String getUrlOfConcept(@NonNull Concept c) {
        return c.getVocabulary().getBaseUri() + "/?idc=" + c.getExternalId() + "&idt=" + c.getVocabulary().getExternalVocabularyId();
    }

    /**
     * Gets the URL for a specific field code for a user.
     *
     * @param info      the user information containing institution and user details
     * @param fieldCode the field code for which to get the URL
     * @return the URL of the concept associated with the field code, or null if no configuration is found
     */
    @Nullable
    public String getUrlForFieldCode(@NonNull UserInfo info, @NonNull String fieldCode) {
        try {
            return getUrlOfConcept(findParentConceptForFieldcode(info, fieldCode));
        } catch (NoConfigForFieldException e) {
            return null;
        }
    }

    /**
     * Finds the configuration for a specific field code for a user.
     *
     * @param info      the user information containing institution and user details
     * @param fieldCode the field code for which to find the configuration
     * @return The user configuration if exists, otherwise the institution configuration
     * @throws NoConfigForFieldException if no configuration is found for the field code
     */
    @NonNull
    public ConceptFieldConfig findConfigurationForFieldCode(@NonNull UserInfo info, @NonNull String fieldCode) throws NoConfigForFieldException {
        Optional<ConceptFieldConfig> personConfig = conceptFieldConfigRepository.findOneByFieldCodeForUser(info.getUser().getId(), info.getInstitution().getId(), fieldCode);
        if (personConfig.isEmpty()) {
            Optional<ConceptFieldConfig> institutionConfig = conceptFieldConfigRepository.findOneByFieldCodeForInstitution(info.getInstitution().getId(), fieldCode);
            if (institutionConfig.isEmpty()) {
                throw new NoConfigForFieldException(fieldCode);
            }
            return institutionConfig.get();
        }
        return personConfig.get();
    }

    /**
     * Fetches autocomplete suggestions for a given field code and input string.
     *
     * @param info      the user information containing institution and user details
     * @param fieldCode the field code for which to fetch autocomplete suggestions
     * @param input     the input string to match against concept labels. Can be null or empty.
     * @return a list of matching ConceptLabel objects
     * @throws NoConfigForFieldException if no configuration is found for the field code
     */
    @NonNull
    public List<ConceptAutocompleteDTO> fetchAutocomplete(@NonNull UserInfo info, @NonNull String fieldCode, @Nullable String input) throws NoConfigForFieldException {
        ConceptFieldConfig config = findConfigurationForFieldCode(info, fieldCode);
        return autocompleteRepository.findMatchingConceptsFor(config.getConcept(), info.getLang(), input, LIMIT_RESULTS);
    }

    public int resultLimit() {
        return LIMIT_RESULTS;
    }

}
