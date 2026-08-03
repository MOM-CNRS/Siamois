package fr.siamois.domain.services.form;

import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptHierarchy;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.PurlInfoDTO;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptHierarchyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormConfigService {

    private final FieldFormConfigRepository fieldFormConfigRepository;
    private final ConceptService conceptService;
    private final VocabularyService vocabularyService;
    private final ConceptApi conceptApi;
    private final ConceptHierarchyRepository conceptHierarchyRepository;
    private final ConceptRepository conceptRepository;

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
            vocabularyService.findOrCreateVocabularyOfUri(branchTopConcept.getVocabulary().completeUri());
            concept = conceptService.saveOrGetConcept(branchTopConcept);
        } catch (InvalidEndpointException e) {
            log.error("Error while saving concept {} ", branchTopConcept.getExternalId(), e);
            throw new IllegalArgumentException("Error while saving concept " + branchTopConcept.getExternalId(), e);
        }
        conceptFieldFormConfig.setBranchTopTerm(concept);
        conceptFieldFormConfig.setCollection(null);

        loadDownExpansion(concept);

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
            Map<String, Concept> urlSavedConcept = new HashMap<>();
            saveAllConceptFromBranch(concept, dto, urlSavedConcept);
            for (Map.Entry<String, FullInfoDTO> info : dto.getData().entrySet()) {
                FullInfoDTO fullInfoDTO = info.getValue();
                if (Objects.nonNull(fullInfoDTO.getNarrower())) {
                    createRelations(info, fullInfoDTO, urlSavedConcept);
                }
                if (Objects.nonNull(fullInfoDTO.getRelated())) {
                    createRelatedConceptsRelations(concept, info, urlSavedConcept, fullInfoDTO);
                }

            }
        } catch (ErrorProcessingExpansionException e) {
            log.error("Error while fetching down expansion for branch config {} ", concept.getExternalId(), e);
        }
    }

    private void createRelatedConceptsRelations(@org.jspecify.annotations.NonNull Concept concept, Map.@org.jspecify.annotations.NonNull Entry<String, FullInfoDTO> info, @org.jspecify.annotations.NonNull Map<String, Concept> urlTosavedConcept, @org.jspecify.annotations.NonNull FullInfoDTO fullInfoDTO) {
        Concept currentConcept = urlTosavedConcept.get(info.getKey());
        Set<Concept> relatedConcepts = currentConcept.getRelatedConcepts();
        for (PurlInfoDTO related : fullInfoDTO.getRelated()) {
            FullInfoDTO relatedInfos = conceptApi.fetchConceptInfoByUri(concept.getVocabulary(), related.getValue());
            relatedConcepts.add(conceptService.saveOrGetConceptFromFullDTO(concept.getVocabulary(), relatedInfos, null));
        }
        urlTosavedConcept.put(info.getKey(), conceptRepository.save(currentConcept));
    }

    private void saveAllConceptFromBranch(@org.jspecify.annotations.NonNull Concept concept, @org.jspecify.annotations.NonNull ConceptBranchDTO dto, Map<String, Concept> savedConcept) {
        for (Map.Entry<String, FullInfoDTO> info : dto.getData().entrySet()) {
            savedConcept.put(info.getKey(), conceptService.saveOrGetConceptFromFullDTO(concept.getVocabulary(), info.getValue(), null));
        }
    }

    private void createRelations(Map.Entry<String, FullInfoDTO> info, @org.jspecify.annotations.NonNull FullInfoDTO fullInfoDTO, Map<String, Concept> savedConcept) {
        for (PurlInfoDTO purlInfoDTO : fullInfoDTO.getNarrower()) {
            Concept parent = savedConcept.get(info.getKey());
            Concept child = savedConcept.get(purlInfoDTO.getValue());
            if (parent == null) {
                throw new IllegalStateException("No concept found in cache map for URL " + info.getKey());
            }
            if (child == null) {
                throw new IllegalStateException("No concept found in cache map for URL " + purlInfoDTO.getValue());
            }
            if (!parent.equals(child)) {
                ConceptHierarchy relation = new ConceptHierarchy(parent, child, null);
                conceptHierarchyRepository.save(relation);
            }
        }
    }


}
