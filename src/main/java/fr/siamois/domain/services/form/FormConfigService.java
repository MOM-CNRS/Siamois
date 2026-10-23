package fr.siamois.domain.services.form;

import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.ConceptCollectionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.vocabulary.ConceptCollectionDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.concept.ConceptBranchDTO;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.mapper.vocabulary.VocabularyMapper;
import fr.siamois.utils.vocabulary.ConceptApiUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormConfigService {

    private final FieldFormConfigRepository fieldFormConfigRepository;
    private final ConceptService conceptService;
    private final VocabularyService vocabularyService;
    private final ConceptApi conceptApi;
    private final ConceptCollectionService conceptCollectionService;
    private final ApplicationContext applicationContext;
    private final VocabularyMapper vocabularyMapper;

    /**
     * Creates the branch config for a {@link CustomFieldConcept} with a specified top term.
     * @param formConfig The FormConfig parent of the branch config
     * @param customFieldConcept The field object
     * @param branchTopConcept The branch's top term. Only the {@link ConceptDTO#externalId} and {@link ConceptDTO#vocabulary} are mandatory
     */
    @Transactional(rollbackFor = Exception.class)
    public void addConceptConfigFor(@NonNull FormConfig formConfig,
                                    @NonNull CustomFieldConcept customFieldConcept,
                                    @NonNull ConceptDTO branchTopConcept) {
        doAddConceptConfig(formConfig, customFieldConcept, branchTopConcept, null);
    }

    /**
     * Same as {@link #addConceptConfigFor(FormConfig, CustomFieldConcept, ConceptDTO)}, reporting the
     * branch's down-expansion progress on {@code progressWrapper}.
     *
     * @param progressWrapper where to report progress, or null when nobody is watching
     */
    @Transactional(rollbackFor = Exception.class)
    public void addConceptConfigFor(@NonNull FormConfig formConfig,
                                    @NonNull CustomFieldConcept customFieldConcept,
                                    @NonNull ConceptDTO branchTopConcept,
                                    @Nullable ProgressWrapper progressWrapper) {
        doAddConceptConfig(formConfig, customFieldConcept, branchTopConcept, progressWrapper);
    }

    private void doAddConceptConfig(FormConfig formConfig, CustomFieldConcept customFieldConcept,
                                    ConceptDTO branchTopConcept, @Nullable ProgressWrapper progressWrapper) {
        ConceptFieldFormConfig conceptFieldFormConfig = createOrGetFieldConfig(formConfig, customFieldConcept);
        Concept concept;
        try {
            Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(branchTopConcept.getVocabulary().completeUri());
            branchTopConcept.setVocabulary(vocabularyMapper.convert(vocabulary));
            concept = conceptService.saveOrGetConcept(branchTopConcept);
            conceptFieldFormConfig.setBranchTopTerm(concept);
            conceptFieldFormConfig.setCollection(null);
            clearFieldCode(conceptFieldFormConfig);

            loadDownExpansion(concept, progressWrapper);

            fieldFormConfigRepository.save(conceptFieldFormConfig);
        } catch (InvalidEndpointException e) {
            log.error("Error while saving concept {} ", branchTopConcept.getExternalId(), e);
            throw new IllegalArgumentException("Error while saving concept " + branchTopConcept.getExternalId(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void addConceptConfigFor(@NonNull FormConfig formConfig, @NonNull CustomFieldConcept customFieldConcept, @NonNull ConceptCollectionDTO collectionDTO) {
        doAddConceptConfig(formConfig, customFieldConcept, collectionDTO, null);
    }

    /**
     * Same as {@link #addConceptConfigFor(FormConfig, CustomFieldConcept, ConceptCollectionDTO)},
     * reporting the collection's import progress on {@code progressWrapper}.
     *
     * @param progressWrapper where to report progress, or null when nobody is watching
     */
    @Transactional(rollbackFor = Exception.class)
    public void addConceptConfigFor(@NonNull FormConfig formConfig, @NonNull CustomFieldConcept customFieldConcept, @NonNull ConceptCollectionDTO collectionDTO, @Nullable ProgressWrapper progressWrapper) {
        doAddConceptConfig(formConfig, customFieldConcept, collectionDTO, progressWrapper);
    }

    /**
     * Names a collection already imported as a field's source, without reading the thesaurus again.
     *
     * @param collection a collection of the local database
     */
    @Transactional(rollbackFor = Exception.class)
    public void addConceptConfigFor(@NonNull FormConfig formConfig, @NonNull CustomFieldConcept customFieldConcept, @NonNull ConceptCollection collection) {
        ConceptFieldFormConfig conceptFieldFormConfig = createOrGetFieldConfig(formConfig, customFieldConcept);
        conceptFieldFormConfig.setCollection(collection);
        conceptFieldFormConfig.setBranchTopTerm(null);
        clearFieldCode(conceptFieldFormConfig);
        fieldFormConfigRepository.save(conceptFieldFormConfig);
    }

    private void doAddConceptConfig(FormConfig formConfig, CustomFieldConcept customFieldConcept,
                                    ConceptCollectionDTO collectionDTO, @Nullable ProgressWrapper progressWrapper) {
        ConceptFieldFormConfig conceptFieldFormConfig = createOrGetFieldConfig(formConfig, customFieldConcept);
        ConceptCollection savedCollection = conceptCollectionService.createOrUpdateConceptCollection(collectionDTO, progressWrapper);
        conceptFieldFormConfig.setCollection(savedCollection);
        conceptFieldFormConfig.setBranchTopTerm(null);
        clearFieldCode(conceptFieldFormConfig);
        fieldFormConfigRepository.save(conceptFieldFormConfig);
    }

    /**
     * Names a field's source by a field code looked up in a given thesaurus, instead of a branch or a
     * collection: the list is the subtree of the concept that carries the code in that thesaurus.
     *
     * @param vocabulary the thesaurus the code is looked up in
     * @param fieldCode  a code the thesaurus defines
     */
    @Transactional(rollbackFor = Exception.class)
    public void addFieldCodeConfigFor(@NonNull FormConfig formConfig,
                                      @NonNull CustomFieldConcept customFieldConcept,
                                      @NonNull Vocabulary vocabulary,
                                      @NonNull String fieldCode) {
        ConceptFieldFormConfig conceptFieldFormConfig = createOrGetFieldConfig(formConfig, customFieldConcept);
        conceptFieldFormConfig.setVocabulary(vocabulary);
        conceptFieldFormConfig.setFieldCode(fieldCode);
        conceptFieldFormConfig.setBranchTopTerm(null);
        conceptFieldFormConfig.setCollection(null);
        fieldFormConfigRepository.save(conceptFieldFormConfig);
    }

    private static void clearFieldCode(ConceptFieldFormConfig config) {
        config.setVocabulary(null);
        config.setFieldCode(null);
    }

    /**
     * Reads the branch/collection restriction currently configured for a field, without creating a
     * row when there is none.
     *
     * @param formConfig The FormConfig owning the field's configuration
     * @param customFieldConcept The field to look up
     * @return the field's concept configuration, empty if the field carries no {@link ConceptFieldFormConfig} yet
     */
    public Optional<ConceptFieldFormConfig> findConceptConfigFor(@NonNull FormConfig formConfig, @NonNull CustomFieldConcept customFieldConcept) {
        return fieldFormConfigRepository.findByFormConfigAndField(formConfig, customFieldConcept)
                .filter(ConceptFieldFormConfig.class::isInstance)
                .map(ConceptFieldFormConfig.class::cast);
    }

    /**
     * Clears a field's branch/collection restriction, if it has one — e.g. when the field is switched
     * back to being driven by its own field code. No-op when the field carries no
     * {@link ConceptFieldFormConfig} yet, since there is then nothing to clear.
     *
     * @param formConfig The FormConfig owning the field's configuration
     * @param customFieldConcept The field to clear
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearConceptConfigFor(@NonNull FormConfig formConfig, @NonNull CustomFieldConcept customFieldConcept) {
        findConceptConfigFor(formConfig, customFieldConcept).ifPresent(config -> {
            config.setBranchTopTerm(null);
            config.setCollection(null);
            clearFieldCode(config);
            fieldFormConfigRepository.save(config);
        });
    }

    /**
     * A field's row is created as a plain {@link FieldFormConfig} (active/mandatory only) the first
     * time it is added to a type — it only becomes a {@link ConceptFieldFormConfig} the first time a
     * branch or collection is configured on it. Converting one JOINED-inheritance subtype into another
     * for the same row can't be done by just building a new object with the same id and saving it: the
     * old instance is still managed in this session under that identity, and Hibernate refuses to
     * insert a second, different object under the same key ({@code NonUniqueObjectException}).
     * Deleting the old row and saving a new one in the same transaction doesn't work either: Envers
     * then writes a deletion and an insertion audit row under the same (id, revision) key. So the row
     * is promoted in place, by adding it to the subtype table, and read back as a
     * {@code ConceptFieldFormConfig}, which keeps all its attributes.
     */
    private @NonNull ConceptFieldFormConfig createOrGetFieldConfig(@NonNull FormConfig formConfig, @NonNull CustomFieldConcept customFieldConcept) {
        Optional<FieldFormConfig> opt = fieldFormConfigRepository.findByFormConfigAndField(formConfig, customFieldConcept);
        ConceptFieldFormConfig conceptFieldFormConfig;
        if (opt.isEmpty()) {
            conceptFieldFormConfig = new ConceptFieldFormConfig();
            conceptFieldFormConfig.setFormConfig(formConfig);
            conceptFieldFormConfig.setField(customFieldConcept);
        } else if (opt.get() instanceof ConceptFieldFormConfig cffc) {
            conceptFieldFormConfig = cffc;
        } else {
            fieldFormConfigRepository.promoteToConceptConfig(customFieldConcept.getId(), formConfig.getId());
            conceptFieldFormConfig = fieldFormConfigRepository.findByFormConfigAndField(formConfig, customFieldConcept)
                    .filter(ConceptFieldFormConfig.class::isInstance)
                    .map(ConceptFieldFormConfig.class::cast)
                    .orElseThrow(() -> new IllegalStateException(
                            "Field config of field " + customFieldConcept.getId() + " was not promoted to a concept config"));
        }
        return conceptFieldFormConfig;
    }

    private void loadDownExpansion(@NonNull Concept concept, @Nullable ProgressWrapper progressWrapper) {
        try {
            ConceptBranchDTO dto = conceptApi.fetchDownExpansion(concept.getVocabulary(), concept.getExternalId());
            ConceptApiUtils.BranchLoadComponents components = new ConceptApiUtils.BranchLoadComponents(applicationContext);
            ConceptApiUtils.saveAllConceptsOfBranch(components, concept.getVocabulary(), dto, new HashMap<>(), progressWrapper);
        } catch (ErrorProcessingExpansionException e) {
            log.error("Error while fetching down expansion for branch config {} ", concept.getExternalId(), e);
        }
    }


}
