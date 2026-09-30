package fr.siamois.domain.services.form.rules;

import fr.siamois.domain.models.form.rules.ConceptIdLookup;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ConceptIdLookup} adossé à la table des concepts. Seuls les succès sont mis en cache (l'id
 * d'un concept ne change pas) : un concept absent peut être importé plus tard.
 */
@Service
@RequiredArgsConstructor
public class RepositoryConceptIdLookup implements ConceptIdLookup {

    private final ConceptRepository conceptRepository;
    private final Map<String, Long> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<Long> conceptId(String vocabularyExtId, String conceptExtId) {
        String key = vocabularyExtId.toLowerCase(Locale.ROOT) + "|" + conceptExtId.toLowerCase(Locale.ROOT);
        Long cached = cache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<Long> id = conceptRepository.findConceptByExternalIdIgnoreCase(vocabularyExtId, conceptExtId)
                .map(c -> c.getId());
        id.ifPresent(v -> cache.put(key, v));
        return id;
    }
}
