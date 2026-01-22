package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.vocabulary.ThesaurusInfo;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.infrastructure.api.ThesaurusApi;
import fr.siamois.infrastructure.api.dto.LabelDTO;
import fr.siamois.infrastructure.api.dto.ThesaurusDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyServiceTest {

    @Mock
    private VocabularyRepository vocabularyRepository;

    @Mock
    private ThesaurusApi thesaurusApi;

    @Mock
    private VocabularyTypeRepository vocabularyTypeRepository;

    @Mock
    private LabelService labelService;

    @InjectMocks
    private VocabularyService vocabularyService;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        vocabulary = new Vocabulary();
        vocabulary.setId(1L);
    }

    @Test
    void findById_Success() {
        when(vocabularyRepository.findById(1L)).thenReturn(Optional.ofNullable(vocabulary));

        Vocabulary actualResult = vocabularyService.findVocabularyById(1L);

        assertEquals(vocabulary, actualResult);
    }

    @Test
    void saveOrGetVocabulary_Success() {
        Vocabulary newVocabulary = new Vocabulary();
        newVocabulary.setBaseUri("http://example.com");
        newVocabulary.setExternalVocabularyId("123");

        when(vocabularyRepository.findVocabularyByBaseUriAndVocabExternalId(newVocabulary.getBaseUri(), newVocabulary.getExternalVocabularyId())).thenReturn(Optional.empty());
        when(vocabularyRepository.save(newVocabulary)).thenReturn(newVocabulary);

        Vocabulary result = vocabularyService.saveOrGetVocabulary(newVocabulary);

        assertNotNull(result);
        assertEquals(newVocabulary, result);
    }

    @Test
    void findOrCreateVocabularyOfUri_Success() throws InvalidEndpointException {
        String uri = "http://example.com/openapi/v1/thesaurus?idt=123";

        ThesaurusDTO thesaurusDTO = new ThesaurusDTO();
        thesaurusDTO.setIdTheso("123");
        thesaurusDTO.setBaseUri("http://example.com");
        LabelDTO labelDTO = new LabelDTO();
        labelDTO.setLang("en");
        labelDTO.setTitle("Test Thesaurus");
        thesaurusDTO.setLabels(List.of(labelDTO));

        VocabularyType vocabularyType = new VocabularyType();
        vocabularyType.setLabel("Thesaurus");

        when(thesaurusApi.fetchThesaurusInfo(uri)).thenReturn(thesaurusDTO);
        when(vocabularyTypeRepository.findVocabularyTypeByLabel("Thesaurus")).thenReturn(Optional.of(vocabularyType));
        when(vocabularyRepository.save(any(Vocabulary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Vocabulary result = vocabularyService.findOrCreateVocabularyOfUri(uri);

        assertNotNull(result);
        assertEquals("123", result.getExternalVocabularyId());
        assertEquals("http://example.com", result.getBaseUri());
        assertEquals(vocabularyType, result.getType());
    }

    @Test
    void findAllPublicThesaurusOf_success_preferredLanguageFound() throws InvalidEndpointException {
        String server = "http://example.com";
        String lang = "fr";
        ThesaurusDTO dto1 = new ThesaurusDTO("1", List.of(new LabelDTO("en", "English Label"), new LabelDTO("fr", "Label Français")));
        when(thesaurusApi.fetchAllPublicThesaurus(server)).thenReturn(List.of(dto1));

        List<ThesaurusInfo> result = vocabularyService.findAllPublicThesaurusOf(server, lang);

        assertEquals(1, result.size());
        assertEquals("1", result.get(0).idTheso());
        assertEquals("Label Français", result.get(0).label());
        assertEquals("fr", result.get(0).langLabel());
    }

    @Test
    void findAllPublicThesaurusOf_success_fallbackToDefaultLanguage() throws InvalidEndpointException {
        String server = "http://example.com";
        String lang = "es";
        ThesaurusDTO dto1 = new ThesaurusDTO("1", List.of(new LabelDTO("en", "English Label"), new LabelDTO("fr", "Label Français")));
        when(thesaurusApi.fetchAllPublicThesaurus(server)).thenReturn(List.of(dto1));

        List<ThesaurusInfo> result = vocabularyService.findAllPublicThesaurusOf(server, lang);

        assertEquals(1, result.size());
        assertEquals("1", result.get(0).idTheso());
        assertEquals("English Label", result.get(0).label());
        assertEquals("en", result.get(0).langLabel());
    }

    @Test
    void findAllPublicThesaurusOf_noLabels() throws InvalidEndpointException {
        String server = "http://example.com";
        String lang = "fr";
        ThesaurusDTO dto1 = new ThesaurusDTO("1", Collections.emptyList());
        when(thesaurusApi.fetchAllPublicThesaurus(server)).thenReturn(List.of(dto1));

        List<ThesaurusInfo> result = vocabularyService.findAllPublicThesaurusOf(server, lang);

        assertEquals(1, result.size());
        assertEquals("1", result.get(0).idTheso());
        assertEquals("NULL", result.get(0).label());
        assertEquals("fr", result.get(0).langLabel());
    }

    @Test
    void findAllPublicThesaurusOf_apiReturnsEmptyList() throws InvalidEndpointException {
        String server = "http://example.com";
        String lang = "fr";
        when(thesaurusApi.fetchAllPublicThesaurus(server)).thenReturn(Collections.emptyList());

        List<ThesaurusInfo> result = vocabularyService.findAllPublicThesaurusOf(server, lang);

        assertTrue(result.isEmpty());
    }

    @Test
    void findAllPublicThesaurusOf_apiThrowsException() throws InvalidEndpointException {
        String server = "http://example.com";
        String lang = "fr";
        when(thesaurusApi.fetchAllPublicThesaurus(server)).thenThrow(new InvalidEndpointException("API Error"));

        assertThrows(InvalidEndpointException.class, () -> vocabularyService.findAllPublicThesaurusOf(server, lang));
    }

    @Test
    void findAllPublicThesaurusOf_multipleThesaurus() throws InvalidEndpointException {
        String server = "http://example.com";
        String lang = "fr";
        ThesaurusDTO dto1 = new ThesaurusDTO("1", List.of(new LabelDTO("fr", "Label 1")));
        ThesaurusDTO dto2 = new ThesaurusDTO("2", List.of(new LabelDTO("en", "Label 2")));
        ThesaurusDTO dto3 = new ThesaurusDTO("3", List.of(new LabelDTO("de", "Label 3"), new LabelDTO("fr", "Label 3 fr")));
        when(thesaurusApi.fetchAllPublicThesaurus(server)).thenReturn(List.of(dto1, dto2, dto3));

        List<ThesaurusInfo> result = vocabularyService.findAllPublicThesaurusOf(server, lang);

        assertEquals(3, result.size());
        assertEquals("Label 1", result.get(0).label());
        assertEquals("fr", result.get(0).langLabel());
        assertEquals("Label 2", result.get(1).label());
        assertEquals("en", result.get(1).langLabel());
        assertEquals("Label 3 fr", result.get(2).label());
        assertEquals("fr", result.get(2).langLabel());
    }
}