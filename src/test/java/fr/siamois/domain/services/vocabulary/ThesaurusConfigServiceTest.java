package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyApiKey;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.crypto.ApiKeyEncryptor;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyKeyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.bean.settings.ThesaurusApiKey;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThesaurusConfigServiceTest {

    @Mock
    private VocabularyRepository vocabularyRepository;
    @Mock
    private PersonMapper personMapper;
    @Mock
    private VocabularyKeyRepository vocabularyKeyRepository;
    @Mock
    private ApiKeyEncryptor apiKeyEncryptor;

    @InjectMocks
    private ThesaurusConfigService service;

    private PersonDTO personDto;
    private Person person;
    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        personDto = new PersonDTO();
        personDto.setId(3L);
        person = new Person();
        person.setId(3L);
        vocabulary = new Vocabulary();
        vocabulary.setId(7L);
    }

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
    }

    private static VocabularyApiKey savedKey(long vocabularyId, String value) {
        VocabularyApiKey key = new VocabularyApiKey();
        VocabularyApiKey.VocabularyApiKeyId id = new VocabularyApiKey.VocabularyApiKeyId();
        id.setVocabularyId(vocabularyId);
        id.setPersonId(3L);
        key.setId(id);
        key.setEncryptedKey(value);
        return key;
    }

    @Test
    void findApiKeysOf_returnsTheDecryptedKeysByVocabularyId() {
        when(vocabularyKeyRepository.findAllByPersonId(3L)).thenReturn(List.of(savedKey(7L, "enc7"), savedKey(8L, "enc8")));
        when(apiKeyEncryptor.decrypt("enc7")).thenReturn("k7");
        when(apiKeyEncryptor.decrypt("enc8")).thenReturn("k8");

        assertThat(service.findApiKeysOf(3L)).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(7L, "k7", 8L, "k8"));
    }

    @Test
    void findApiKeysOf_leavesOutAKeyThatNoLongerDecrypts() {
        when(vocabularyKeyRepository.findAllByPersonId(3L)).thenReturn(List.of(savedKey(7L, "enc7"), savedKey(8L, "stale")));
        when(apiKeyEncryptor.decrypt("enc7")).thenReturn("k7");
        when(apiKeyEncryptor.decrypt("stale")).thenThrow(new IllegalStateException("bad key"));

        assertThat(service.findApiKeysOf(3L)).containsExactlyEntriesOf(java.util.Map.of(7L, "k7"));
    }

    @Test
    void updateThesaurusApiKeyConfig_createsTheKeyWithItsCompositeId() {
        ExecutionContextHolder.set(new UserInfo(new InstitutionDTO(), personDto, "fr"));
        when(personMapper.invertConvert(personDto)).thenReturn(person);
        when(vocabularyRepository.findById(7L)).thenReturn(Optional.of(vocabulary));
        when(vocabularyKeyRepository.findByVocabularyAndPerson(vocabulary, person)).thenReturn(Optional.empty());
        when(apiKeyEncryptor.encrypt("secret")).thenReturn("encrypted-secret");

        service.updateThesaurusApiKeyConfig(List.of(new ThesaurusApiKey(7L, "Thésaurus", "https://t/?idt=th1", "secret")));

        ArgumentCaptor<VocabularyApiKey> saved = ArgumentCaptor.forClass(VocabularyApiKey.class);
        verify(vocabularyKeyRepository).save(saved.capture());
        assertThat(saved.getValue().getEncryptedKey()).isEqualTo("encrypted-secret");
        assertThat(saved.getValue().getId().getVocabularyId()).isEqualTo(7L);
        assertThat(saved.getValue().getId().getPersonId()).isEqualTo(3L);
    }

    @Test
    void updateThesaurusApiKeyConfig_skipsBlankKeys() {
        ExecutionContextHolder.set(new UserInfo(new InstitutionDTO(), personDto, "fr"));
        when(personMapper.invertConvert(personDto)).thenReturn(person);

        service.updateThesaurusApiKeyConfig(List.of(new ThesaurusApiKey(7L, "Thésaurus", "https://t/?idt=th1", "  ")));

        verify(vocabularyKeyRepository, never()).save(any());
    }
}
