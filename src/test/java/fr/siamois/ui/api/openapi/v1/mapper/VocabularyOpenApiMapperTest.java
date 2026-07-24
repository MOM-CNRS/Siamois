package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.domain.models.vocabulary.label.VocabularyLabel;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.ui.api.openapi.v1.resource.vocabulary.VocabularyResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyOpenApiMapperTest {

    @Mock
    private LabelService labelService;

    @InjectMocks
    private VocabularyOpenApiMapper mapper;

    @Test
    void toResource_null_returnsNull() {
        assertThat(mapper.toResource(null, "fr")).isNull();
    }

    @Test
    void toResource_mapsAllFields() {
        VocabularyType type = new VocabularyType();
        type.setLabel("Thesaurus");

        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setId(5L);
        vocabulary.setExternalVocabularyId("TH-001");
        vocabulary.setBaseUri("https://thesaurus.example.org");
        vocabulary.setType(type);

        VocabularyLabel label = new VocabularyLabel();
        label.setValue("Thésaurus archéologie");
        when(labelService.findLabelOf(vocabulary, "fr")).thenReturn(label);

        VocabularyResource resource = mapper.toResource(vocabulary, "fr");

        assertThat(resource.getResourceType()).isEqualTo("vocabularies");
        assertThat(resource.getId()).isEqualTo("5");
        assertThat(resource.getExternalId()).isEqualTo("TH-001");
        assertThat(resource.getBaseUri()).isEqualTo("https://thesaurus.example.org");
        assertThat(resource.getUri()).isEqualTo("https://thesaurus.example.org?idt=TH-001");
        assertThat(resource.getTypeLabel()).isEqualTo("Thesaurus");
        assertThat(resource.getLabel()).isEqualTo("Thésaurus archéologie");
        verify(labelService).findLabelOf(vocabulary, "fr");
    }

    @Test
    void toResource_withoutTypeOrId() {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setExternalVocabularyId("X");
        vocabulary.setBaseUri("https://example.org");

        VocabularyLabel label = new VocabularyLabel();
        label.setValue("X");
        when(labelService.findLabelOf(vocabulary, "en")).thenReturn(label);

        VocabularyResource resource = mapper.toResource(vocabulary, "en");

        assertThat(resource.getId()).isNull();
        assertThat(resource.getTypeLabel()).isNull();
        assertThat(resource.getLabel()).isEqualTo("X");
    }
}
