package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyApiKey;
import fr.siamois.infrastructure.crypto.ApiKeyEncryptor;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyKeyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
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
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThesaurusConfigService {

    private final VocabularyRepository vocabularyRepository;
    private final PersonMapper personMapper;
    private final VocabularyKeyRepository vocabularyKeyRepository;
    private final ApiKeyEncryptor apiKeyEncryptor;

    /**
     * The API keys a person saved, decrypted, by the id of their vocabulary. A key that no longer decrypts (the
     * encryption password or salt changed) is left out: the person types it again.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> findApiKeysOf(Long personId) {
        Map<Long, String> keys = new HashMap<>();
        for (VocabularyApiKey key : vocabularyKeyRepository.findAllByPersonId(personId)) {
            try {
                keys.put(key.getId().getVocabularyId(), apiKeyEncryptor.decrypt(key.getEncryptedKey()));
            } catch (IllegalStateException e) {
                log.warn("API key of person {} for vocabulary {} cannot be decrypted, ignored",
                        personId, key.getId().getVocabularyId());
            }
        }
        return keys;
    }

    /** Saves every key filled in, all or none: a failure on one key leaves the others as they were. */
    @Transactional
    public void updateThesaurusApiKeyConfig(List<ThesaurusApiKey> apiKeys) {
        UserInfo info = ExecutionContextHolder.getNonNull();
        Person person = personMapper.invertConvert(info.getUser());
        for (ThesaurusApiKey apiKey : apiKeys) {
            if (Objects.nonNull(apiKey.getApiKey()) && StringUtils.isNotBlank(apiKey.getApiKey())) {
                Vocabulary vocabulary = vocabularyRepository.findById(apiKey.getVocabularyId()).orElseThrow(() -> new IllegalStateException("ThesaurusApiKey should contain a valid Vocabulary ID. Instead : " + apiKey.getVocabularyId()));
                Optional<VocabularyApiKey> opt = vocabularyKeyRepository.findByVocabularyAndPerson(vocabulary, person);
                VocabularyApiKey key;
                if (opt.isPresent()) {
                    key = opt.get();
                } else {
                    key = new VocabularyApiKey();
                    VocabularyApiKey.VocabularyApiKeyId id = new VocabularyApiKey.VocabularyApiKeyId();
                    id.setVocabularyId(vocabulary.getId());
                    id.setPersonId(person.getId());
                    key.setId(id);
                    key.setVocabulary(vocabulary);
                    key.setPerson(person);
                }
                key.setEncryptedKey(apiKeyEncryptor.encrypt(apiKey.getApiKey()));
                vocabularyKeyRepository.save(key);
            }
        }
    }

}
