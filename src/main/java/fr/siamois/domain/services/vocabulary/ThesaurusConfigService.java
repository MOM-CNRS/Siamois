package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyApiKey;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyKeyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.bean.settings.ThesaurusApiKey;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ThesaurusConfigService {

    private final VocabularyRepository vocabularyRepository;
    private final PersonMapper personMapper;
    private final VocabularyKeyRepository vocabularyKeyRepository;

    /** The API keys a person saved, by the id of their vocabulary. */
    @Transactional(readOnly = true)
    public Map<Long, String> findApiKeysOf(Long personId) {
        return vocabularyKeyRepository.findAllByPersonId(personId).stream()
                .collect(Collectors.toMap(key -> key.getId().getVocabularyId(), VocabularyApiKey::getEncryptedKey));
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
                key.setEncryptedKey(apiKey.getApiKey());
                vocabularyKeyRepository.save(key);
            }
        }
    }

}
