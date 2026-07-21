package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.VocabularyLabel;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.ui.api.openapi.v1.resource.vocabulary.VocabularyResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VocabularyOpenApiMapper {

    private final LabelService labelService;

    public VocabularyResource toResource(Vocabulary vocabulary, String lang) {
        if (vocabulary == null) {
            return null;
        }
        VocabularyResource resource = new VocabularyResource();
        resource.setResourceType("vocabularies");
        if (vocabulary.getId() != null) {
            resource.setId(String.valueOf(vocabulary.getId()));
        }
        resource.setExternalId(vocabulary.getExternalVocabularyId());
        resource.setBaseUri(vocabulary.getBaseUri());
        resource.setUri(vocabulary.getUri());
        if (vocabulary.getType() != null) {
            resource.setTypeLabel(vocabulary.getType().getLabel());
        }
        VocabularyLabel label = labelService.findLabelOf(vocabulary, lang);
        if (label != null) {
            resource.setLabel(label.getValue());
        }
        return resource;
    }
}
