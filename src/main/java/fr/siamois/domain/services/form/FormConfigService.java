package fr.siamois.domain.services.form;

import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        ConceptFieldFormConfig conceptFieldFormConfig = createOrGetFieldConfig(formConfig, customFieldConcept);
        Concept concept;
        try {
            Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(branchTopConcept.getVocabulary().completeUri());
            branchTopConcept.setVocabulary(vocabularyMapper.convert(vocabulary));
            concept = conceptService.saveOrGetConcept(branchTopConcept);
            conceptFieldFormConfig.setBranchTopTerm(concept);
            conceptFieldFormConfig.setCollection(null);

            loadDownExpansion(concept);

            fieldFormConfigRepository.save(conceptFieldFormConfig);
        } catch (InvalidEndpointException e) {
            log.error("Error while saving concept {} ", branchTopConcept.getExternalId(), e);
            throw new IllegalArgumentException("Error while saving concept " + branchTopConcept.getExternalId(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void addConceptConfigFor(@NonNull FormConfig formConfig, @NonNull CustomFieldConcept customFieldConcept, @NonNull ConceptCollectionDTO collectionDTO) {
        ConceptFieldFormConfig conceptFieldFormConfig = createOrGetFieldConfig(formConfig, customFieldConcept);
        ConceptCollection savedCollection = conceptCollectionService.createOrUpdateConceptCollection(collectionDTO);
        conceptFieldFormConfig.setCollection(savedCollection);
        conceptFieldFormConfig.setBranchTopTerm(null);
        fieldFormConfigRepository.save(conceptFieldFormConfig);
    }

    private @NonNull ConceptFieldFormConfig createOrGetFieldConfig(@NonNull FormConfig formConfig, @NonNull CustomFieldConcept customFieldConcept) {
        Optional<FieldFormConfig> opt = fieldFormConfigRepository.findByFormConfigAndField(formConfig, customFieldConcept);
        ConceptFieldFormConfig conceptFieldFormConfig;
        if (opt.isEmpty()) {
            conceptFieldFormConfig = new ConceptFieldFormConfig();
            conceptFieldFormConfig.setFormConfig(formConfig);
            conceptFieldFormConfig.setField(customFieldConcept);
        } else {
            if (opt.get() instanceof ConceptFieldFormConfig cffc) {
                conceptFieldFormConfig = cffc;
            } else {
                conceptFieldFormConfig = new ConceptFieldFormConfig(opt.get());
            }
        }
        return conceptFieldFormConfig;
    }

    private void loadDownExpansion(@NonNull Concept concept) {
        try {
            ConceptBranchDTO dto = conceptApi.fetchDownExpansion(concept.getVocabulary(), concept.getExternalId());
            ConceptApiUtils.BranchLoadComponents components = new ConceptApiUtils.BranchLoadComponents(applicationContext);
            ConceptApiUtils.saveAllConceptsOfBranch(components, concept.getVocabulary(), dto);
        } catch (ErrorProcessingExpansionException e) {
            log.error("Error while fetching down expansion for branch config {} ", concept.getExternalId(), e);
        }
    }


}
