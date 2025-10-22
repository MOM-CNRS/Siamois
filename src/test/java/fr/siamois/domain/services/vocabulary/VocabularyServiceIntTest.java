package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.infrastructure.api.RequestFactory;
import fr.siamois.infrastructure.api.ThesaurusApi;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Disabled
@ExtendWith(MockitoExtension.class)
class VocabularyServiceIntTest {

    @Mock
    private VocabularyRepository vocabularyRepository;

    @Mock
    private VocabularyTypeRepository vocabularyTypeRepository;

    @Mock
    private LabelService labelService;

    private VocabularyService vocabularyService;

    @BeforeEach
    void setUp() {
        ThesaurusApi thesaurusApi = new ThesaurusApi(new RequestFactory(new RestTemplateBuilder()));
        vocabularyService = new VocabularyService(vocabularyRepository, thesaurusApi, vocabularyTypeRepository, labelService);
    }

    @Test
    @Tag("integration")
    void findOrCreateVocabularyOfUri_withNativeLink() throws InvalidEndpointException {
        String nativeLink = "https://thesaurus.mom.fr/?idt=th227";

        UserInfo info = new UserInfo(new Institution(), new Person(), "fr");
        info.getInstitution().setId(1L);
        info.getInstitution().getManagers().add(info.getUser());
        info.getInstitution().setName("SiaDev");

        info.getUser().setId(1L);
        info.getUser().setUsername("username");
        info.getUser().setPassword("password");
        info.getUser().setEmail("mail");

        VocabularyType vocabularyType = new VocabularyType();
        vocabularyType.setId(1L);
        vocabularyType.setLabel("Thesaurus");

        when(vocabularyTypeRepository.findVocabularyTypeByLabel("Thesaurus")).thenReturn(Optional.of(vocabularyType));
        when(vocabularyRepository.save(any(Vocabulary.class))).then(invocation -> {
            Vocabulary vocabulary = invocation.getArgument(0);
            vocabulary.setId(1L);
            return vocabulary;
        });

        Vocabulary result = vocabularyService.findOrCreateVocabularyOfUri(nativeLink);
        assertNotNull(result);
        assertEquals("https://thesaurus.mom.fr", result.getBaseUri());
        assertEquals("th227", result.getExternalVocabularyId());
    }

    @Test
    @Tag("integration")
    void findOrCreateVocabularyOfUri_withArkLink() throws InvalidEndpointException {
        String nativeLink = "https://thesaurus.mom.fr/api/ark:/66666/SIA-TTDBMLCXLNL9Q93GK17L7-S";

        UserInfo info = new UserInfo(new Institution(), new Person(), "fr");
        info.getInstitution().setId(1L);
        info.getInstitution().getManagers().add(info.getUser());
        info.getInstitution().setName("SiaDev");

        info.getUser().setId(1L);
        info.getUser().setUsername("username");
        info.getUser().setPassword("password");
        info.getUser().setEmail("mail");

        VocabularyType vocabularyType = new VocabularyType();
        vocabularyType.setId(1L);
        vocabularyType.setLabel("Thesaurus");

        when(vocabularyTypeRepository.findVocabularyTypeByLabel("Thesaurus")).thenReturn(Optional.of(vocabularyType));
        when(vocabularyRepository.save(any(Vocabulary.class))).then(invocation -> {
            Vocabulary vocabulary = invocation.getArgument(0);
            vocabulary.setId(1L);
            return vocabulary;
        });

        Vocabulary result = vocabularyService.findOrCreateVocabularyOfUri(nativeLink);

        assertNotNull(result);
        assertEquals("https://thesaurus.mom.fr", result.getBaseUri());
        assertEquals("th223", result.getExternalVocabularyId());
    }

}
