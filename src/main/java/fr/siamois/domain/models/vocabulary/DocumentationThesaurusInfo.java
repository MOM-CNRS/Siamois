package fr.siamois.domain.models.vocabulary;

import org.springframework.lang.NonNull;

import java.io.Serializable;

/**
 * A documentation thesaurus: the id of its vocabulary in the database, and the thesaurus it is in OpenTheso.
 */
public record DocumentationThesaurusInfo(
        @NonNull Long vocabularyId,
        @NonNull ThesaurusInfo thesaurus
) implements Serializable {
}
