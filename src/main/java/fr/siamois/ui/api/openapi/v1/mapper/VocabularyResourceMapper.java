package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.domain.models.form.config.ConceptState;
import fr.siamois.domain.models.form.config.VocabularyMode;
import fr.siamois.domain.models.vocabulary.FieldVocabularyConfig;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import org.springframework.lang.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Turns the vocabulary of a field ({@link FieldVocabularyConfig}) into its wire shape. A followed list
 * says which concepts are switched off; a frozen one lists its concepts and their state.
 */
public final class VocabularyResourceMapper {

    private VocabularyResourceMapper() {
    }

    @Nullable
    public static FieldResource.Vocabulary toResource(@Nullable FieldVocabularyConfig config) {
        if (config == null) {
            return null;
        }
        FieldResource.Source source = new FieldResource.Source(
                config.kind().name(),
                config.fieldCode(),
                config.topTermConceptId() == null ? null : String.valueOf(config.topTermConceptId()),
                config.collectionId() == null ? null : String.valueOf(config.collectionId()));
        Map<Long, ConceptState> states = config.states();
        if (config.mode() == VocabularyMode.FROZEN) {
            List<FieldResource.ConceptStateEntry> concepts = states.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> new FieldResource.ConceptStateEntry(String.valueOf(e.getKey()), e.getValue().name()))
                    .toList();
            return new FieldResource.Vocabulary(source, config.mode().name(), null, concepts);
        }
        List<String> excluded = states.entrySet().stream()
                .filter(e -> e.getValue() == ConceptState.DISABLED)
                .map(Map.Entry::getKey)
                .sorted(Comparator.naturalOrder())
                .map(String::valueOf)
                .toList();
        return new FieldResource.Vocabulary(source, config.mode().name(), excluded, null);
    }
}
