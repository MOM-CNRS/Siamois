package fr.siamois.domain.services.vocabulary;

import fr.siamois.annotations.ExecutionTimeLogger;
import fr.siamois.domain.models.UserInfo;
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
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.AutocompleteRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptFieldConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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
    private final InstitutionMapper institutionMapper;
    private final ActionUnitMapper actionUnitMapper;

    private boolean containsFieldCode(FullInfoDTO conceptDTO) {
        return conceptDTO.getFieldcode().isPresent();
    }

    /**
     * Sets up the field configuration for an institution based on the vocabulary.
     *
     * @param info       the user information containing institution details
     * @param vocabulary the vocabulary to use for configuration
     * @throws NotSiamoisThesaurusException      if the vocabulary is not a Siamois thesaurus
     * @throws ErrorProcessingExpansionException if there is an error processing the vocabulary expansion
     */
    @Transactional(rollbackFor = ErrorProcessingExpansionException.class)
    public void setupFieldConfigurationForInstitution(UserInfo info,
                                                      Vocabulary vocabulary,
                                                      ProgressWrapper progressWrapper) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        setupFieldConfigurationForInstitution(info.getInstitution(), vocabulary, progressWrapper);
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
    public Optional<FeedbackFieldConfig> setupFieldConfigurationForInstitution(InstitutionDTO institution, Vocabulary vocabulary, ProgressWrapper progressWrapper) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        return setupFieldConfiguration(institution, vocabulary, progressWrapper);
    }

    /**
     * Wrapper to call {@link #setupFieldConfigurationForInstitution(InstitutionDTO, Vocabulary, ProgressWrapper)} without progress tracking.
     *
     * @param info       the user information containing institution details
     * @param vocabulary the vocabulary to use for configuration
     * @return an Optional containing GlobalFieldConfig if the configuration is wrong, otherwise empty
     * @throws NotSiamoisThesaurusException      if the vocabulary is not a Siamois thesaurus
     * @throws ErrorProcessingExpansionException if there is an error processing the vocabulary expansion
     */
    @NonNull
    @Transactional(rollbackFor = ErrorProcessingExpansionException.class)
    public Optional<FeedbackFieldConfig> setupFieldConfigurationForInstitution(@NonNull UserInfo info, @NonNull Vocabulary vocabulary) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        return setupFieldConfigurationForInstitution(info.getInstitution(), vocabulary, new ProgressWrapper());
    }

    /**
     * Wrapper to call {@link #setupFieldConfigurationForInstitution(InstitutionDTO, Vocabulary, ProgressWrapper)} without progress tracking.
     *
     * @param institution the institution
     * @param vocabulary  the vocabulary to use for configuration
     * @return an Optional containing GlobalFieldConfig if the configuration is wrong, otherwise empty
     * @throws NotSiamoisThesaurusException      if the vocabulary is not a Siamois thesaurus
     * @throws ErrorProcessingExpansionException if there is an error processing the vocabulary expansion
     */
    @NonNull
    public Optional<FeedbackFieldConfig> setupFieldConfigurationForInstitution(@NonNull InstitutionDTO institution, @NonNull Vocabulary vocabulary) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        return setupFieldConfiguration(institution, vocabulary, new ProgressWrapper());
    }

    /**
     * Sets up the field configuration for a project (action unit) based on the vocabulary.
     * The created configurations override the institution ones for this action unit only.
     *
     * @param actionUnit      the action unit to configure
     * @param vocabulary      the vocabulary to use for configuration
     * @param progressWrapper the progress tracker
     * @return an Optional containing GlobalFieldConfig if the configuration is wrong, otherwise empty
     * @throws NotSiamoisThesaurusException      if the vocabulary is not a Siamois thesaurus
     * @throws ErrorProcessingExpansionException if there is an error processing the vocabulary expansion
     */
    @NonNull
    @Transactional(rollbackFor = ErrorProcessingExpansionException.class)
    public Optional<FeedbackFieldConfig> setupFieldConfigurationForActionUnit(@NonNull ActionUnitDTO actionUnit,
                                                                             @NonNull Vocabulary vocabulary,
                                                                             @NonNull ProgressWrapper progressWrapper) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        return setupFieldConfiguration(actionUnit.getCreatedByInstitution(), actionUnit, vocabulary, progressWrapper);
    }

    /**
     * Wrapper to call setupFieldConfigurationForActionUnit without progress tracking.
     *
     * @param actionUnit the action unit to configure
     * @param vocabulary the vocabulary to use for configuration
     * @return an Optional containing GlobalFieldConfig if the configuration is wrong, otherwise empty
     * @throws NotSiamoisThesaurusException      if the vocabulary is not a Siamois thesaurus
     * @throws ErrorProcessingExpansionException if there is an error processing the vocabulary expansion
     */
    @NonNull
    @Transactional(rollbackFor = ErrorProcessingExpansionException.class)
    public Optional<FeedbackFieldConfig> setupFieldConfigurationForActionUnit(@NonNull ActionUnitDTO actionUnit,
                                                                             @NonNull Vocabulary vocabulary) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        return setupFieldConfiguration(actionUnit.getCreatedByInstitution(), actionUnit, vocabulary, new ProgressWrapper());
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

    private Optional<FeedbackFieldConfig> setupFieldConfiguration(@NonNull InstitutionDTO institutionDTO,
                                                                  @NonNull Vocabulary vocabulary,
                                                                  @NonNull ProgressWrapper progressWrapper) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        return setupFieldConfiguration(institutionDTO, null, vocabulary, progressWrapper);
    }

    private Optional<FeedbackFieldConfig> setupFieldConfiguration(@NonNull InstitutionDTO institutionDTO,
                                                                  @Nullable ActionUnitDTO actionUnitDTO,
                                                                  @NonNull Vocabulary vocabulary,
                                                                  @NonNull ProgressWrapper progressWrapper) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        ConceptBranchDTO conceptBranchDTO = conceptApi.fetchFieldsBranch(vocabulary);
        FeedbackFieldConfig config = createConfigOfThesaurus(conceptBranchDTO);
        if (config.isWrongConfig()) log.warn("Missing code in thesaurus configuration: "+config.missingFieldCode());

        progressWrapper.setTotalSteps((config.conceptWithValidFieldCode().size() * (NUMBER_OF_STEPS_IN_FIELD_CONFIG + NUMBER_OF_STEPS_IN_CONCEPT_UPDATE)));

        for (FullInfoDTO conceptDTO : config.conceptWithValidFieldCode()) {
            String fieldCode = conceptDTO.getFieldcode().orElseThrow(() -> FIELD_CODE_NOT_FOUND);

            Concept concept = conceptService.saveOrGetConceptFromFullDTO(vocabulary, conceptDTO, null);
            ConceptFieldConfig fieldConfig;
            Optional<ConceptFieldConfig> optConfig;

            if (Objects.isNull(actionUnitDTO)) {
                optConfig = conceptFieldConfigRepository.findOneByFieldCodeForInstitution(institutionDTO.getId(), fieldCode);
            } else {
                optConfig = conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(fieldCode, actionUnitDTO.getId());
            }

            if (optConfig.isEmpty()) {
                Institution institution = institutionMapper.invertConvert(institutionDTO);
                fieldConfig = new ConceptFieldConfig();
                fieldConfig.setInstitution(institution);
                if (actionUnitDTO != null) fieldConfig.setActionUnit(actionUnitMapper.invertConvert(actionUnitDTO));
                fieldConfig.setFieldCode(fieldCode);
                fieldConfig.setConcept(concept);
            } else {
                fieldConfig = optConfig.get();
                fieldConfig.setConcept(concept); // update concept also if there is already a field config
            }
            fieldConfig = conceptFieldConfigRepository.save(fieldConfig);



            progressWrapper.incrementStep();
            conceptService.saveAllSubConceptOfIfUpdated(fieldConfig, progressWrapper);
            progressWrapper.incrementStep();
        }


        if (config.isWrongConfig()) return Optional.of(config);
        return Optional.empty();
    }

    /**
     * Finds the vocabulary URL for a given institution.
     *
     * @param institutionId the institution for which to find the vocabulary URL
     * @return an Optional containing the vocabulary URL if found, otherwise empty
     */
    @NonNull
    public Optional<String> findVocabularyUrlOfInstitutionId(@NonNull Long institutionId) {
        Optional<Concept> optConcept = conceptRepository
                .findTopTermConfigForFieldCodeOfInstitution(institutionId, SpatialUnit.CATEGORY_FIELD_CODE);
        if (optConcept.isEmpty()) return Optional.empty();
        Vocabulary vocabulary = optConcept.get().getVocabulary();
        return Optional.of(vocabulary.getBaseUri() + "/?idt=" + vocabulary.getExternalVocabularyId());
    }

    /**
     * Finds the vocabulary URL for a given action unit (project).
     *
     * @param actionUnitId the action unit for which to find the vocabulary URL
     * @return an Optional containing the vocabulary URL if found, otherwise empty
     */
    @NonNull
    public Optional<String> findVocabularyUrlOfActionUnitId(@NonNull Long actionUnitId) {
        Optional<ConceptFieldConfig> optConfig = conceptFieldConfigRepository
                .findOneByFieldCodeAndActionUnitId(SpatialUnit.CATEGORY_FIELD_CODE, actionUnitId);
        if (optConfig.isEmpty()) return Optional.empty();
        Concept concept = optConfig.get().getConcept();
        Hibernate.initialize(concept.getVocabulary());
        Vocabulary vocabulary = concept.getVocabulary();
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
     * Finds the configuration for a specific field code for an institution.
     *
     * @param info      the user information containing institution and user details
     * @param fieldCode the field code for which to find the configuration
     * @return The institution configuration
     * @throws NoConfigForFieldException if no configuration is found for the field code
     */
    @NonNull
    @ExecutionTimeLogger
    public ConceptFieldConfig findConfigurationForFieldCode(@NonNull UserInfo info, @NonNull String fieldCode) throws NoConfigForFieldException {
        Optional<ConceptFieldConfig> institutionConfig = conceptFieldConfigRepository.findOneByFieldCodeForInstitution(info.getInstitution().getId(), fieldCode);
        if (institutionConfig.isEmpty()) {
            throw new NoConfigForFieldException(fieldCode);
        }
        return institutionConfig.get();
    }

    /**
     * Searchs the configuration for a specific field for a project. If none set, falls back to the institution configuration
     * @param info the user information containing institution and user details
     * @param fieldCode the field code for which to find the configuration
     * @param actionUnitDTO The project to find the config for
     * @return The project configuration if exists, otherwise the institution configuration
     * @throws NoConfigForFieldException if no configuration is found for the field code
     */
    @NonNull
    public ConceptFieldConfig findConfigurationForFieldCode(@NonNull UserInfo info, @NonNull String fieldCode, @NonNull ActionUnitDTO actionUnitDTO) throws NoConfigForFieldException {
        Optional<ConceptFieldConfig> projectConfig = conceptFieldConfigRepository.findOneByFieldCodeAndActionUnitId(fieldCode, actionUnitDTO.getId());
        if (projectConfig.isEmpty()) {
            return findConfigurationForFieldCode(info, fieldCode);
        }
        return projectConfig.get();
    }

        /**
         * Fetches autocomplete suggestions for a given field code and input string.
         *
         * @param info      the user information containing institution and user details
         * @param fieldCode the field code for which to fetch autocomplete suggestions
         * @param input     the input string to match against concept labels. Can be null or empty.
         * @return a list of matching ConceptAutocompleteDTO objects
         * @throws NoConfigForFieldException if no configuration is found for the field code
         */
    @NonNull
    @ExecutionTimeLogger
    public List<ConceptAutocompleteDTO> fetchAutocomplete(@NonNull UserInfo info, @NonNull String fieldCode, @Nullable String input) throws NoConfigForFieldException {
        ConceptFieldConfig config = findConfigurationForFieldCode(info, fieldCode);
        return autocompleteRepository.findMatchingConceptsFor(config.getConcept(), info.getLang(), input, LIMIT_RESULTS);
    }

    /**
     * Fetches autocomplete suggestions depending on the base value.
     * This autocompletion is used when the field is dependant of the value of another field.
     * In that case, the autocompletion values are the related concepts of the selected previous value.
     *
     * @param info      the user information containing institution and user details
     * @param fieldCode the field code of the current field, used to restrict the candidates to the concepts
     *                  configured for that field
     * @param baseValue the concept as the value of the field from which the current field is dependant of
     * @param input     the input string to match against concept labels. Can be null or empty.
     * @return a list of matching ConceptAutocompleteDTO objects
     * @throws NoConfigForFieldException if no configuration is found for the field code
     */
    @NonNull
    @ExecutionTimeLogger
    public List<ConceptAutocompleteDTO> fetchAutocompleteRelated(@NonNull UserInfo info, @NonNull String fieldCode, @Nullable Concept baseValue, @Nullable String input) throws NoConfigForFieldException {
        ConceptFieldConfig config = findConfigurationForFieldCode(info, fieldCode);
        return autocompleteRepository.findMatchingConceptsFromRelatedFor(config.getConcept(), baseValue, info.getLang(), input, LIMIT_RESULTS);
    }

    public int resultLimit() {
        return LIMIT_RESULTS;
    }

    /**
     * Tous les vocabulaires (field_code → concepts) configurés pour l'institution et l'utilisateur courants.
     * Chaque liste est limitée à {@link #LIMIT_RESULTS} concepts (même règle que l'autocomplete).
     */
    @NonNull
    @Transactional(readOnly = true)
    public Map<String, List<ConceptAutocompleteDTO>> fetchAllConfiguredVocabularies(@NonNull UserInfo info) {
        Long institutionId = info.getInstitution().getId();
        List<String> fieldCodes = conceptFieldConfigRepository.findDistinctFieldCodesForInstitution(institutionId);
        Map<String, List<ConceptAutocompleteDTO>> out = new LinkedHashMap<>();
        for (String fieldCode : fieldCodes) {
            try {
                out.put(fieldCode, fetchAutocomplete(info, fieldCode, null));
            } catch (NoConfigForFieldException e) {
                out.put(fieldCode, List.of());
            }
        }
        return out;
    }

}
