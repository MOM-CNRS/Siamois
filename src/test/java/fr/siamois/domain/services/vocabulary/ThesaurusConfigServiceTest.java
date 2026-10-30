package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.vocabulary.VocabularyInstance;
import fr.siamois.domain.models.vocabulary.VocabularyApiKey;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.crypto.ApiKeyEncryptor;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyKeyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyInstanceRepository;
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
    private VocabularyInstanceRepository vocabularyInstanceRepository;
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
    private VocabularyInstance instance;

    @BeforeEach
    void setUp() {
        personDto = new PersonDTO();
        personDto.setId(3L);
        person = new Person();
        person.setId(3L);
        instance = new VocabularyInstance("https://t");
        instance.setId(7L);
    }

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
    }

    private static VocabularyApiKey savedKey(long instanceId, String value) {
        VocabularyApiKey key = new VocabularyApiKey();
        VocabularyApiKey.VocabularyApiKeyId id = new VocabularyApiKey.VocabularyApiKeyId();
        id.setInstanceId(instanceId);
        id.setPersonId(3L);
        key.setId(id);
        key.setEncryptedKey(value);
        return key;
    }

    @Test
    void findApiKeysOf_returnsTheDecryptedKeysByInstanceId() {
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
        when(vocabularyKeyRepository.findById(any())).thenReturn(Optional.empty());
        when(vocabularyInstanceRepository.findById(7L)).thenReturn(Optional.of(instance));
        when(apiKeyEncryptor.encrypt("secret")).thenReturn("encrypted-secret");

        service.updateThesaurusApiKeyConfig(List.of(new ThesaurusApiKey(7L, "https://t", "secret")));

        ArgumentCaptor<VocabularyApiKey> saved = ArgumentCaptor.forClass(VocabularyApiKey.class);
        verify(vocabularyKeyRepository).save(saved.capture());
        assertThat(saved.getValue().getEncryptedKey()).isEqualTo("encrypted-secret");
        assertThat(saved.getValue().getId().getInstanceId()).isEqualTo(7L);
        assertThat(saved.getValue().getId().getPersonId()).isEqualTo(3L);
        assertThat(saved.getValue().getInstance()).isSameAs(instance);
    }

    @Test
    void updateThesaurusApiKeyConfig_skipsBlankKeys() {
        ExecutionContextHolder.set(new UserInfo(new InstitutionDTO(), personDto, "fr"));
        when(personMapper.invertConvert(personDto)).thenReturn(person);

        service.updateThesaurusApiKeyConfig(List.of(new ThesaurusApiKey(7L, "https://t", "  ")));

        verify(vocabularyKeyRepository, never()).save(any());
    }

    @Test
    void updateThesaurusApiKeyConfig_updatesTheExistingKeyOfTheInstance() {
        ExecutionContextHolder.set(new UserInfo(new InstitutionDTO(), personDto, "fr"));
        when(personMapper.invertConvert(personDto)).thenReturn(person);
        VocabularyApiKey existing = savedKey(7L, "old");
        when(vocabularyKeyRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(apiKeyEncryptor.encrypt("secret")).thenReturn("encrypted-secret");

        service.updateThesaurusApiKeyConfig(List.of(new ThesaurusApiKey(7L, "https://t", "secret")));

        verify(vocabularyKeyRepository).save(existing);
        verify(vocabularyInstanceRepository, never()).findById(any());
        assertThat(existing.getEncryptedKey()).isEqualTo("encrypted-secret");
    }
}
