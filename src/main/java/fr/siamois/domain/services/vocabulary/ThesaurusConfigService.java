package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.vocabulary.VocabularyApiKey;
import fr.siamois.domain.models.vocabulary.VocabularyInstance;
import fr.siamois.infrastructure.crypto.ApiKeyEncryptor;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyKeyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyInstanceRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.bean.settings.ThesaurusApiKey;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThesaurusConfigService {

    private final VocabularyInstanceRepository vocabularyInstanceRepository;
    private final PersonMapper personMapper;
    private final VocabularyKeyRepository vocabularyKeyRepository;
    private final ApiKeyEncryptor apiKeyEncryptor;

    /**
     * The API keys a person saved, decrypted, by the id of their instance. A key that no longer decrypts (the
     * encryption password or salt changed) is left out: the person types it again.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> findApiKeysOf(Long personId) {
        Map<Long, String> keys = new HashMap<>();
        for (VocabularyApiKey key : vocabularyKeyRepository.findAllByPersonId(personId)) {
            try {
                keys.put(key.getId().getInstanceId(), apiKeyEncryptor.decrypt(key.getEncryptedKey()));
            } catch (IllegalStateException e) {
                log.warn("API key of person {} for vocabulary instance {} cannot be decrypted, ignored",
                        personId, key.getId().getInstanceId());
            }
        }
        return keys;
    }

    /**
     * Saves every key filled in, all or none: a failure on one key leaves the others as they were.
     */
    @Transactional
    public void updateThesaurusApiKeyConfig(List<ThesaurusApiKey> apiKeys) {
        Person person = personMapper.invertConvert(ExecutionContextHolder.getNonNull().getUser());
        for (ThesaurusApiKey apiKey : apiKeys) {
            if (StringUtils.isNotBlank(apiKey.getApiKey())) {
                VocabularyApiKey key = vocabularyKeyRepository.findById(keyId(apiKey.getInstanceId(), person.getId()))
                        .orElseGet(() -> newKey(apiKey.getInstanceId(), person));
                key.setEncryptedKey(apiKeyEncryptor.encrypt(apiKey.getApiKey()));
                vocabularyKeyRepository.save(key);
            }
        }
    }

    /**
     * A key not saved yet, for an instance that must exist.
     */
    private VocabularyApiKey newKey(Long instanceId, Person person) {
        VocabularyInstance instance = vocabularyInstanceRepository.findById(instanceId).orElseThrow(() ->
                new IllegalStateException("ThesaurusApiKey should contain a valid VocabularyInstance ID. Instead : " + instanceId));
        VocabularyApiKey key = new VocabularyApiKey();
        key.setId(keyId(instance.getId(), person.getId()));
        key.setInstance(instance);
        key.setPerson(person);
        return key;
    }

    /**
     * The id of the key of a person for an instance.
     */
    private static VocabularyApiKey.VocabularyApiKeyId keyId(Long instanceId, Long personId) {
        VocabularyApiKey.VocabularyApiKeyId id = new VocabularyApiKey.VocabularyApiKeyId();
        id.setInstanceId(instanceId);
        id.setPersonId(personId);
        return id;
    }

}
