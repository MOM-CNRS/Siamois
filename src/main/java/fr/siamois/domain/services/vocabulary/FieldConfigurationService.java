package fr.siamois.domain.services.vocabulary;

import fr.siamois.annotations.ExecutionTimeLogger;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.ConceptFieldState;
import fr.siamois.domain.models.form.config.ConceptState;
import fr.siamois.domain.models.form.config.VocabularyMode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConceptFromFieldCode;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.settings.DocumentationThesaurus;
import fr.siamois.domain.models.vocabulary.*;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptBranchDTO;
import fr.siamois.infrastructure.database.repositories.form.config.ConceptFieldStateRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.AutocompleteRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptFieldConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.DocumentationThesaurusRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.ObjectProvider;
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

    /**
     * How many candidates a frozen list reads from its source before keeping the enabled ones: the
     * enabled concepts are not necessarily among the first {@link #LIMIT_RESULTS} the source returns.
     */
    static final int FROZEN_SCAN_LIMIT = 10_000;
    public static final int NUMBER_OF_STEPS_IN_FIELD_CONFIG = 2;
    public static final int NUMBER_OF_STEPS_IN_CONCEPT_UPDATE = 5;
    private final ConceptApi conceptApi;
    private final FieldService fieldService;
    private final ConceptService conceptService;

    private final ConceptFieldConfigRepository conceptFieldConfigRepository;
    private final AutocompleteRepository autocompleteRepository;
    private final InstitutionMapper institutionMapper;
    private final ActionUnitMapper actionUnitMapper;
    private final FieldFormConfigRepository fieldFormConfigRepository;
    private final ConceptFieldStateRepository conceptFieldStateRepository;
    private final DocumentationThesaurusRepository documentationThesaurusRepository;
    private final SiamoisThesaurus siamoisThesaurus;
    private final FieldCodeResolver fieldCodeResolver;
    private final CustomFieldCandidateService customFieldCandidateService;
    private final ObjectProvider<FieldConfigurationService> selfProvider;

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
        selfProvider.getObject().setupFieldConfigurationForInstitution(info.getInstitution(), vocabulary, progressWrapper);
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
    @Transactional(rollbackFor = ErrorProcessingExpansionException.class)
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
        return selfProvider.getObject().setupFieldConfigurationForInstitution(info.getInstitution(), vocabulary, new ProgressWrapper());
    }

    /**
     * Wrapper to call {@link #setupFieldConfigurationForInstitution(InstitutionDTO, Vocabulary, ProgressWrapper)} without progress tracking.
     *
     * @param institution the institution
     * @param vocabulary  the vocabulary to use for configuration
     * @throws NotSiamoisThesaurusException      if the vocabulary is not a Siamois thesaurus
     * @throws ErrorProcessingExpansionException if there is an error processing the vocabulary expansion
     */
    @Transactional(rollbackFor = ErrorProcessingExpansionException.class)
    public void setupFieldConfigurationForInstitution(@NonNull InstitutionDTO institution, @NonNull Vocabulary vocabulary) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        setupFieldConfiguration(institution, vocabulary, new ProgressWrapper());
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
        Optional<FeedbackFieldConfig> feedback = selfProvider.getObject().loadFieldCodes(vocabulary, progressWrapper);
        saveDocumentationThesaurus(institutionDTO, actionUnitDTO, vocabulary);
        return feedback;
    }

    /**
     * Makes {@code vocabulary} the thesaurus where the institution documents its fields and types, unless it already
     * has one: a thesaurus an administrator chose is never replaced. The field codes of the vocabulary are not loaded.
     */
    @Transactional
    public void setDocumentationThesaurusIfAbsent(@NonNull InstitutionDTO institution, @NonNull Vocabulary vocabulary) {
        if (documentationThesaurusRepository.findOfInstitution(institution.getId()).isEmpty()) {
            saveDocumentationThesaurus(institution, null, vocabulary);
        }
    }

    /** Makes {@code vocabulary} the thesaurus where the institution, or the project, documents its fields and types. */
    private void saveDocumentationThesaurus(InstitutionDTO institutionDTO, @Nullable ActionUnitDTO actionUnitDTO, Vocabulary vocabulary) {
        Optional<DocumentationThesaurus> existing = actionUnitDTO == null
                ? documentationThesaurusRepository.findOfInstitution(institutionDTO.getId())
                : documentationThesaurusRepository.findByActionUnitId(actionUnitDTO.getId());
        DocumentationThesaurus thesaurus = existing.orElseGet(() -> {
            DocumentationThesaurus created = new DocumentationThesaurus();
            created.setInstitution(institutionMapper.invertConvert(institutionDTO));
            if (actionUnitDTO != null) created.setActionUnit(actionUnitMapper.invertConvert(actionUnitDTO));
            return created;
        });
        thesaurus.setVocabulary(vocabulary);
        documentationThesaurusRepository.save(thesaurus);
    }

    /**
     * Loads the field codes of a thesaurus: for each concept of its fields branch that carries a code SIAMOIS
     * knows, the concept and its subtree. Shared by everyone who reads the thesaurus, so a thesaurus is loaded once.
     * The additional fields whose candidate is pending in the thesaurus are then searched for again.
     *
     * @return the feedback when the thesaurus misses codes SIAMOIS needs, otherwise empty
     */
    @Transactional(rollbackFor = ErrorProcessingExpansionException.class)
    public Optional<FeedbackFieldConfig> loadFieldCodes(@NonNull Vocabulary vocabulary,
                                                        @NonNull ProgressWrapper progressWrapper) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        ConceptBranchDTO conceptBranchDTO = conceptApi.fetchFieldsBranch(vocabulary);
        FeedbackFieldConfig config = createConfigOfThesaurus(conceptBranchDTO);
        if (config.isWrongConfig()) log.warn("Missing code in thesaurus configuration: {}", config.missingFieldCode());

        progressWrapper.setTotalSteps((config.conceptWithValidFieldCode().size() * (NUMBER_OF_STEPS_IN_FIELD_CONFIG + NUMBER_OF_STEPS_IN_CONCEPT_UPDATE)));

        for (FullInfoDTO conceptDTO : config.conceptWithValidFieldCode()) {
            String fieldCode = conceptDTO.getFieldcode().orElseThrow(() -> FIELD_CODE_NOT_FOUND);

            Concept concept = conceptService.saveOrGetConceptFromFullDTO(vocabulary, conceptDTO, null);
            ConceptFieldConfig fieldConfig = conceptFieldConfigRepository
                    .findOneByVocabularyIdAndFieldCode(vocabulary.getId(), fieldCode)
                    .orElseGet(() -> {
                        ConceptFieldConfig created = new ConceptFieldConfig();
                        created.setVocabulary(vocabulary);
                        created.setFieldCode(fieldCode);
                        return created;
                    });
            fieldConfig.setConcept(concept); // update concept also if there is already a field config
            fieldConfig = conceptFieldConfigRepository.save(fieldConfig);

            progressWrapper.incrementStep();
            conceptService.saveAllSubConceptOfIfUpdated(fieldConfig, progressWrapper);
            progressWrapper.incrementStep();
        }

        customFieldCandidateService.synchronizePendingOf(vocabulary);

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
    @Transactional(readOnly = true)
    public Optional<String> findVocabularyUrlOfInstitutionId(@NonNull Long institutionId) {
        return documentationThesaurusRepository.findOfInstitution(institutionId)
                .map(DocumentationThesaurus::getVocabulary)
                .map(FieldConfigurationService::urlOf);
    }

    /**
     * Finds the vocabulary URL for a given action unit (project).
     *
     * @param actionUnitId the action unit for which to find the vocabulary URL
     * @return an Optional containing the vocabulary URL if found, otherwise empty
     */
    @NonNull
    @Transactional(readOnly = true)
    public Optional<String> findVocabularyUrlOfActionUnitId(@NonNull Long actionUnitId) {
        return documentationThesaurusRepository.findByActionUnitId(actionUnitId)
                .map(DocumentationThesaurus::getVocabulary)
                .map(FieldConfigurationService::urlOf);
    }

    private static String urlOf(Vocabulary vocabulary) {
        return vocabulary.getBaseUri() + "/?idt=" + vocabulary.getExternalVocabularyId();
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
        return findParentConceptForFieldcode(info, fieldCode, null);
    }

    /**
     * Finds the configuration for a specific field code, scoped to a project (Action Unit) if one is given.
     * Falls back to the institution configuration when no action unit is given, or it has no override.
     *
     * @param info         the user information containing institution and user details
     * @param fieldCode    the field code for which to find the configuration
     * @param actionUnitId the action unit (project) to check for an override, or null for institution-only
     * @return the Concept associated with the field code
     * @throws NoConfigForFieldException if no configuration is found for the field code
     */
    @NonNull
    public Concept findParentConceptForFieldcode(@NonNull UserInfo info, @NonNull String fieldCode, @Nullable Long actionUnitId) throws NoConfigForFieldException {
        Concept concept = selfProvider.getObject().findConfigurationForFieldCode(info, fieldCode, actionUnitId).getConcept();
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
     * Gets the URL of a concept collection based on its vocabulary and external ID.
     *
     * @param collection the ConceptCollection for which to get the URL
     * @return the URL of the collection
     */
    @NonNull
    public String getUrlOfCollection(@NonNull ConceptCollection collection) {
        Hibernate.initialize(collection.getVocabulary());
        Vocabulary vocabulary = collection.getVocabulary();
        return vocabulary.getBaseUri() + "/?idg=" + collection.getExternalId() + "&idt=" + vocabulary.getExternalVocabularyId();
    }

    /**
     * Resolves the field's own restriction (branch/collection) for a project, scoped to one
     * specific value of the entity's "scope" field (e.g. Type) when given, falling back to the
     * project's default configuration (the one with no value concept) otherwise or when the
     * value-specific one doesn't exist. A {@link fr.siamois.domain.models.form.config.FormConfig}
     * exists per (actionUnit, fieldConcept, valueConcept) triple, so the same field can legitimately
     * be configured differently under different value concepts of the same project — this must be
     * scoped by valueConceptId, not just field + actionUnit, or two such configurations would
     * either collide (NonUniqueResultException) or get silently applied to every value.
     *
     * @param conceptField   the concept field to look the restriction up for
     * @param actionUnitId   the project the configuration is scoped to, or null for institution-only
     * @param valueConceptId the concept of the entity's current scope-field value, or null when unknown
     *                       (e.g. a new entity whose type hasn't been set yet)
     * @return the resolved configuration, or empty if the field has none at all
     */
    private Optional<ConceptFieldFormConfig> findFieldFormConfig(@NonNull CustomFieldConcept conceptField, @Nullable Long actionUnitId, @Nullable Long valueConceptId) {
        if (valueConceptId != null) {
            Optional<ConceptFieldFormConfig> specific = fieldFormConfigRepository.findByFieldAndActionUnitAndValue(conceptField, actionUnitId, valueConceptId);
            if (specific.isPresent()) {
                return specific;
            }
        }
        return fieldFormConfigRepository.findDefaultByFieldAndActionUnit(conceptField, actionUnitId);
    }

    /**
     * Where the list of a vocabulary field comes from for a project and a type, resolved the way
     * {@link #fetchAutocomplete(CustomFieldConcept, String, Long, Long)} resolves it: the field's own
     * branch/collection first, then its field code. What a client needs to rebuild that list offline.
     *
     * @param conceptField   the vocabulary field
     * @param actionUnitId   the project the field is displayed in, or null for institution-only
     * @param valueConceptId the concept of the entity's type, or null for the default configuration
     * @return the field's vocabulary, or null when it has neither a restriction nor a field code
     */
    @Nullable
    @Transactional(readOnly = true)
    public FieldVocabularyConfig describeVocabulary(@NonNull CustomFieldConcept conceptField, @Nullable Long actionUnitId, @Nullable Long valueConceptId) {
        Optional<ConceptFieldFormConfig> opt = findFieldFormConfig(conceptField, actionUnitId, valueConceptId);
        if (opt.isPresent() && !opt.get().isNotValid()) {
            ConceptFieldFormConfig config = opt.get();
            Map<Long, ConceptState> states = statesOf(conceptField, config);
            if (config.isBranchConfig()) {
                return new FieldVocabularyConfig(FieldVocabularyConfig.Kind.BRANCH, null, config.getBranchTopTerm().getId(), null, config.getVocabularyMode(), states);
            }
            if (config.isFieldCodeConfig()) {
                return new FieldVocabularyConfig(FieldVocabularyConfig.Kind.FIELD_CODE, config.getFieldCode(), null, null, config.getVocabularyMode(), states);
            }
            return new FieldVocabularyConfig(FieldVocabularyConfig.Kind.COLLECTION, null, null, config.getCollection().getId(), config.getVocabularyMode(), states);
        }
        if (conceptField instanceof CustomFieldConceptFromFieldCode fromFieldCode) {
            return new FieldVocabularyConfig(FieldVocabularyConfig.Kind.FIELD_CODE, fromFieldCode.getFieldCode(), null, null, VocabularyMode.FOLLOW, Map.of());
        }
        return null;
    }

    /**
     * Same as {@link #getUrlForConceptField(CustomFieldConcept, Long, Long)}, without scoping to a
     * specific value of the entity's scope field.
     *
     * @param conceptField the concept field to get the URL for
     * @param actionUnitId the action unit (project) the field is displayed in, or null for institution-only
     * @return the OpenTheso URL to display, or null if the field has no usable configuration
     */
    @Nullable
    public String getUrlForConceptField(@NonNull CustomFieldConcept conceptField, @Nullable Long actionUnitId) {
        // Routed through selfProvider rather than a direct 'this' call: getUrlForConceptField(3-arg)
        // is @Transactional, and a same-class call bypasses the Spring proxy that advice runs on.
        return selfProvider.getObject().getUrlForConceptField(conceptField, actionUnitId, null);
    }

    /**
     * Gets the OpenTheso URL to display for a concept field, following the same configuration priority
     * as {@link #fetchAutocomplete(CustomFieldConcept, String, Long, Long)} : the field's own branch/collection
     * restriction for the given project (Action Unit) and scope value takes priority over its field-code
     * configuration.
     *
     * @param conceptField   the concept field to get the URL for
     * @param actionUnitId   the action unit (project) the field is displayed in, or null for institution-only
     * @param valueConceptId the concept of the entity's current scope-field value, or null when unknown
     * @return the OpenTheso URL to display, or null if the field has no usable configuration
     */
    @Nullable
    @Transactional(readOnly = true)
    public String getUrlForConceptField(@NonNull CustomFieldConcept conceptField, @Nullable Long actionUnitId, @Nullable Long valueConceptId) {
        Optional<ConceptFieldFormConfig> opt = findFieldFormConfig(conceptField, actionUnitId, valueConceptId);
        if (opt.isPresent() && !opt.get().isNotValid()) {
            ConceptFieldFormConfig config = opt.get();
            if (config.isBranchConfig()) {
                Concept branchTopTerm = config.getBranchTopTerm();
                Hibernate.initialize(branchTopTerm.getVocabulary());
                return getUrlOfConcept(branchTopTerm);
            }
            if (config.isFieldCodeConfig()) {
                return findConfigurationInVocabulary(config.getVocabulary(), config.getFieldCode())
                        .map(found -> {
                            Hibernate.initialize(found.getConcept().getVocabulary());
                            return getUrlOfConcept(found.getConcept());
                        })
                        .orElse(null);
            }
            return getUrlOfCollection(config.getCollection());
        }
        if (conceptField instanceof CustomFieldConceptFromFieldCode fromFieldCode) {
            UserInfo info = ExecutionContextHolder.get();
            if (info != null) {
                return getUrlForFieldCode(info, fromFieldCode.getFieldCode(), actionUnitId);
            }
        }
        return null;
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
        return getUrlForFieldCode(info, fieldCode, null);
    }

    /**
     * Gets the URL for a specific field code, scoped to a project (Action Unit) if one is given.
     *
     * @param info         the user information containing institution and user details
     * @param fieldCode    the field code for which to get the URL
     * @param actionUnitId the action unit (project) to check for an override, or null for institution-only
     * @return the URL of the concept associated with the field code, or null if no configuration is found
     */
    @Nullable
    public String getUrlForFieldCode(@NonNull UserInfo info, @NonNull String fieldCode, @Nullable Long actionUnitId) {
        try {
            return getUrlOfConcept(findParentConceptForFieldcode(info, fieldCode, actionUnitId));
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
        return selfProvider.getObject().findConfigurationForFieldCode(info, fieldCode, (Long) null);
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
        return selfProvider.getObject().findConfigurationForFieldCode(info, fieldCode, actionUnitDTO.getId());
    }

    /**
     * Where a field code is read when nothing names a thesaurus for it: in the SIAMOIS thesaurus if it
     * defines the code, otherwise in the thesaurus the project documents its fields and types in, then
     * the institution's. A code SIAMOIS defines therefore never moves to a project's thesaurus on its own:
     * a field has to be pointed at another thesaurus explicitly.*
     * @param info         the user information containing institution and user details
     * @param fieldCode    the field code for which to find the configuration
     * @param actionUnitId the action unit (project) whose documentation thesaurus is read, or null for institution-only
     * @return the configuration of the code in the thesaurus it is read from
     * @throws NoConfigForFieldException if no thesaurus defines the field code
     */
    @NonNull
    @Transactional(readOnly = true)
    public ConceptFieldConfig findConfigurationForFieldCode(@NonNull UserInfo info, @NonNull String fieldCode, @Nullable Long actionUnitId) throws NoConfigForFieldException {
        return fieldCodeResolver.find(info.getInstitution().getId(), actionUnitId, fieldCode)
                .orElseThrow(() -> new NoConfigForFieldException(fieldCode));
    }

    /**
     * The field codes a thesaurus defines, loading them first when the thesaurus has never been read: that is the
     * list a field can pick its source from.
     *
     * @throws NotSiamoisThesaurusException      if the thesaurus has no fields branch, so it defines no code
     * @throws ErrorProcessingExpansionException if the thesaurus could not be read
     */
    @NonNull
    @Transactional(rollbackFor = ErrorProcessingExpansionException.class)
    public List<String> findFieldCodesOf(@NonNull Vocabulary vocabulary) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        if (conceptFieldConfigRepository.findAllByVocabularyId(vocabulary.getId()).isEmpty()) {
            selfProvider.getObject().loadFieldCodes(vocabulary, new ProgressWrapper());
        }
        return conceptFieldConfigRepository.findDistinctFieldCodesOf(List.of(vocabulary.getId()));
    }

    /** The thesaurus a field code is read in when nothing redirects it, for a project of an institution. */
    @NonNull
    public Optional<Vocabulary> findDefaultVocabularyOfFieldCode(@NonNull Long institutionId, @Nullable Long actionUnitId, @NonNull String fieldCode) {
        return fieldCodeResolver.findDefaultVocabulary(institutionId, actionUnitId, fieldCode);
    }

    /** The configuration of a field code in one given thesaurus, read through its fallback code when it has none. */
    @NonNull
    public Optional<ConceptFieldConfig> findConfigurationInVocabulary(@NonNull Vocabulary vocabulary, @NonNull String fieldCode) {
        return fieldCodeResolver.findIn(vocabulary, fieldCode);
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
        return fetchAutocomplete(info, fieldCode, input, null);
    }

    /**
     * Fetches autocomplete suggestions for a given field code and input string, scoped to a project
     * (Action Unit) if one is given.
     *
     * @param info         the user information containing institution and user details
     * @param fieldCode    the field code for which to fetch autocomplete suggestions
     * @param input        the input string to match against concept labels. Can be null or empty.
     * @param actionUnitId the action unit (project) to check for an override, or null for institution-only
     * @return a list of matching ConceptAutocompleteDTO objects
     * @throws NoConfigForFieldException if no configuration is found for the field code
     */
    @NonNull
    @ExecutionTimeLogger
    public List<ConceptAutocompleteDTO> fetchAutocomplete(@NonNull UserInfo info, @NonNull String fieldCode, @Nullable String input, @Nullable Long actionUnitId) throws NoConfigForFieldException {
        ConceptFieldConfig config = selfProvider.getObject().findConfigurationForFieldCode(info, fieldCode, actionUnitId);
        return autocompleteRepository.findMatchingConceptsFor(config.getConcept(), info.getLang(), input, LIMIT_RESULTS);
    }

    /**
     * Same as {@link #fetchAutocomplete(CustomFieldConcept, String, Long, Long)}, without scoping
     * to a specific value of the entity's scope field.
     */
    @NonNull
    public List<ConceptAutocompleteDTO> fetchAutocomplete(CustomFieldConcept conceptField, @Nullable String input, @Nullable Long actionUnitId) throws NoConfigForFieldException {
        // Routed through selfProvider rather than a direct 'this' call: fetchAutocomplete(4-arg) is
        // @Transactional, and a same-class call bypasses the Spring proxy that advice runs on.
        return selfProvider.getObject().fetchAutocomplete(conceptField, input, actionUnitId, null);
    }

    /**
     * Fetches autocomplete suggestions for a concept field, following the configuration set up for that
     * field on the given project (Action Unit) and scope value: a thesaurus branch or a thesaurus collection.
     * When the field has no such configuration and is driven by a field code, falls back on the
     * institution or project configuration of that field code.
     *
     * @param conceptField   the concept field for which to fetch autocomplete suggestions
     * @param input          the input string to match against concept labels. Can be null or empty.
     * @param actionUnitId   the action unit (project) the field is displayed in, or null for institution-only
     * @param valueConceptId the concept of the entity's current scope-field value (e.g. its Type), or null
     *                       when unknown — see {@link #findFieldFormConfig}
     * @return a list of matching ConceptAutocompleteDTO objects
     * @throws NoConfigForFieldException if the field falls back on a field code that has no configuration
     * @throws NoConfigForFieldException if the field has neither a branch/collection nor a field code
     * @throws IllegalStateException     if the field's restriction is saved without a branch nor a collection
     */
    @NonNull
    @ExecutionTimeLogger
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public List<ConceptAutocompleteDTO> fetchAutocomplete(CustomFieldConcept conceptField, @Nullable String input, @Nullable Long actionUnitId, @Nullable Long valueConceptId) throws NoConfigForFieldException {
        UserInfo info = ExecutionContextHolder.get();
        if (info == null) {
            throw new IllegalStateException(String.format("No execution context bound to fetch the autocomplete of field %s", conceptField.getId()));
        }

        if (!StringUtils.isBlank(input)) {
            input = input.trim();
        }

        Optional<ConceptFieldFormConfig> opt = findFieldFormConfig(conceptField, actionUnitId, valueConceptId);
        if (opt.isEmpty()) {
            if (conceptField instanceof CustomFieldConceptFromFieldCode conceptFromFieldCode) {
                return fetchAutocomplete(info, conceptFromFieldCode.getFieldCode(), input, actionUnitId);
            }
            throw new NoConfigForFieldException(String.format("CustomFieldConcept %s has no configuration", conceptField.getId()));
        }
        ConceptFieldFormConfig conceptFieldFormConfig = opt.get();
        if (conceptFieldFormConfig.isNotValid()) {
            throw new IllegalStateException(String.format("ConceptFieldFormConfig %s should not be saved without any configurations", conceptFieldFormConfig.getId()));
        }
        Map<Long, ConceptState> states = statesOf(conceptField, conceptFieldFormConfig);
        int limit = scanLimit(conceptFieldFormConfig, states);
        List<ConceptAutocompleteDTO> candidates = findCandidates(conceptFieldFormConfig, info.getLang(), input, limit);
        return applyStates(candidates, conceptFieldFormConfig, states);
    }

    /**
     * One page of the values a configured field offers, how many it offers in all (a lower bound when
     * {@code totalCapped}: the read stops at {@link #FROZEN_SCAN_LIMIT}) and whether more follow the page.
     */
    public record ValuesPage(List<ConceptAutocompleteDTO> items, int total, boolean totalCapped, boolean hasNext) {
    }

    /**
     * One page of the values a saved branch/collection configuration offers, read from the database with the
     * same states as {@link #fetchAutocomplete(CustomFieldConcept, String, Long, Long)} applies, to browse them.
     *
     * @param offset   how many offered values to skip
     * @param pageSize how many to return
     */
    @NonNull
    @Transactional(readOnly = true)
    public ValuesPage fetchValuesPage(@NonNull CustomFieldConcept conceptField, @NonNull ConceptFieldFormConfig config,
                                      @NonNull String lang, @Nullable String input, int offset, int pageSize) {
        if (config.isNotValid()) {
            return new ValuesPage(List.of(), 0, false, false);
        }
        String text = StringUtils.isBlank(input) ? null : input.trim();
        Map<Long, ConceptState> states = statesOf(conceptField, config);
        int limit = FROZEN_SCAN_LIMIT;
        List<ConceptAutocompleteDTO> candidates = findCandidates(config, lang, text, limit);
        List<ConceptAutocompleteDTO> offered = candidates.stream()
                .filter(candidate -> isOffered(candidate, config, states))
                .toList();
        int from = Math.min(offset, offered.size());
        int to = Math.min(from + pageSize, offered.size());
        return new ValuesPage(offered.subList(from, to), offered.size(), candidates.size() >= limit, offered.size() > to);
    }

    /**
     * The candidates a saved configuration reads from its source: the subtree of its branch, of the concept
     * that carries its field code in its thesaurus, or the concepts of its collection. A field code the
     * thesaurus does not define offers nothing.
     */
    private List<ConceptAutocompleteDTO> findCandidates(ConceptFieldFormConfig config, String lang, @Nullable String input, int limit) {
        if (config.isBranchConfig()) {
            return autocompleteRepository.findMatchingConceptsInBranchOf(config.getBranchTopTerm(), lang, input, limit);
        }
        if (config.isFieldCodeConfig()) {
            return findConfigurationInVocabulary(config.getVocabulary(), config.getFieldCode())
                    .map(found -> autocompleteRepository.findMatchingConceptsInBranchOf(found.getConcept(), lang, input, limit))
                    .orElseGet(() -> {
                        log.warn("Field code {} is not defined in thesaurus {}", config.getFieldCode(), config.getVocabulary().getExternalVocabularyId());
                        return List.of();
                    });
        }
        return autocompleteRepository.findMatchingConceptsInCollection(config.getCollection(), lang, input, limit);
    }

    /**
     * The explicit states of the concepts of a restricted field, by concept id. Empty for a field
     * whose list has never been customised.
     */
    private Map<Long, ConceptState> statesOf(CustomFieldConcept conceptField, ConceptFieldFormConfig config) {
        Map<Long, ConceptState> states = new HashMap<>();
        for (ConceptFieldState state : conceptFieldStateRepository.findAllByFieldAndFormConfig(conceptField, config.getFormConfig())) {
            states.put(state.getConcept().getId(), state.getState());
        }
        return states;
    }

    /**
     * How many candidates to read from the source: a followed list loses its disabled concepts after
     * the read, so it reads that many more to still fill {@link #LIMIT_RESULTS}; a frozen list keeps only
     * the concepts it enabled, which may sit anywhere in the source.
     */
    private static int scanLimit(ConceptFieldFormConfig config, Map<Long, ConceptState> states) {
        if (config.isFrozen()) {
            return FROZEN_SCAN_LIMIT;
        }
        long disabled = states.values().stream().filter(state -> state == ConceptState.DISABLED).count();
        return LIMIT_RESULTS + (int) disabled;
    }

    /**
     * A followed list offers every candidate but the disabled ones; a frozen list offers only the
     * enabled ones. Without any explicit state a followed list is returned as read.
     */
    private static List<ConceptAutocompleteDTO> applyStates(List<ConceptAutocompleteDTO> candidates,
                                                           ConceptFieldFormConfig config,
                                                           Map<Long, ConceptState> states) {
        if (states.isEmpty() && !config.isFrozen()) {
            return candidates;
        }
        return candidates.stream()
                .filter(candidate -> isOffered(candidate, config, states))
                .limit(LIMIT_RESULTS)
                .toList();
    }

    private static boolean isOffered(ConceptAutocompleteDTO candidate, ConceptFieldFormConfig config, Map<Long, ConceptState> states) {
        Long conceptId = candidate.getConceptLabelToDisplay() == null || candidate.getConceptLabelToDisplay().getConcept() == null
                ? null
                : candidate.getConceptLabelToDisplay().getConcept().getId();
        ConceptState state = conceptId == null ? null : states.get(conceptId);
        return config.isFrozen() ? state == ConceptState.ENABLED : state != ConceptState.DISABLED;
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
        return fetchAutocompleteRelated(info, fieldCode, baseValue, input, null);
    }

    /**
     * Fetches autocomplete suggestions depending on the base value, scoped to a project (Action Unit)
     * if one is given. See {@link #fetchAutocompleteRelated(UserInfo, String, Concept, String)}.
     *
     * @param actionUnitId the action unit (project) to check for an override, or null for institution-only
     */
    @NonNull
    @ExecutionTimeLogger
    public List<ConceptAutocompleteDTO> fetchAutocompleteRelated(@NonNull UserInfo info, @NonNull String fieldCode, @Nullable Concept baseValue, @Nullable String input, @Nullable Long actionUnitId) throws NoConfigForFieldException {
        ConceptFieldConfig config = selfProvider.getObject().findConfigurationForFieldCode(info, fieldCode, actionUnitId);
        if (baseValue != null) {
            conceptService.loadUnloadedRelatedConceptsOf(baseValue, config.getConcept());
        }
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
        List<Long> vocabularyIds = new ArrayList<>();
        siamoisThesaurus.vocabulary().ifPresent(v -> vocabularyIds.add(v.getId()));
        documentationThesaurusRepository.findOfInstitution(info.getInstitution().getId())
                .ifPresent(thesaurus -> vocabularyIds.add(thesaurus.getVocabulary().getId()));
        List<String> fieldCodes = vocabularyIds.isEmpty()
                ? List.of()
                : conceptFieldConfigRepository.findDistinctFieldCodesOf(vocabularyIds);
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
