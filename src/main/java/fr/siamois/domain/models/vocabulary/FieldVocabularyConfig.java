package fr.siamois.domain.models.vocabulary;

import fr.siamois.domain.models.form.config.ConceptState;
import fr.siamois.domain.models.form.config.VocabularyMode;
import org.springframework.lang.Nullable;

import java.util.Map;

/**
 * Where the list of a vocabulary field comes from, for one project and one type: its source, whether the
 * list follows it or is frozen, and the explicit state of the concepts that have one.
 *
 * @param kind             what the list is drawn from
 * @param fieldCode        the field code, when {@code kind} is {@link Kind#FIELD_CODE}
 * @param topTermConceptId the root concept of the branch, when {@code kind} is {@link Kind#BRANCH}
 * @param collectionId     the collection, when {@code kind} is {@link Kind#COLLECTION}
 * @param mode             whether the list follows its source or is frozen
 * @param states           the concepts with an explicit state, by concept id; empty for a list never customised
 */
public record FieldVocabularyConfig(Kind kind,
                                    @Nullable String fieldCode,
                                    @Nullable Long topTermConceptId,
                                    @Nullable Long collectionId,
                                    VocabularyMode mode,
                                    Map<Long, ConceptState> states) {

    public enum Kind { FIELD_CODE, BRANCH, COLLECTION }
}
