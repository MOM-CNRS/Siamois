package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The reference thesaurus SIAMOIS ships with: the source of every field it defines (a field code it
 * knows), unless a field is explicitly pointed at another thesaurus.
 */
@Component
@RequiredArgsConstructor
public class SiamoisThesaurus {

    public static final String DEFAULT_URL = "https://thesaurus.mom.fr/?idt=th338";
    private static final String ID_MARKER = "/?idt=";

    @Value("${siamois.thesaurus.url:" + DEFAULT_URL + "}")
    private String url = DEFAULT_URL;

    private final VocabularyRepository vocabularyRepository;

    public String url() {
        return url;
    }

    /** The thesaurus, once it has been created in the database; empty before. */
    public Optional<Vocabulary> vocabulary() {
        int marker = url.indexOf(ID_MARKER);
        if (marker < 0) {
            return Optional.empty();
        }
        return vocabularyRepository.findVocabularyByBaseUriAndVocabExternalId(
                url.substring(0, marker), url.substring(marker + ID_MARKER.length()));
    }
}
