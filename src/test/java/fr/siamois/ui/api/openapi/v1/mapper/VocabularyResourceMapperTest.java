package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.domain.models.form.config.ConceptState;
import fr.siamois.domain.models.form.config.VocabularyMode;
import fr.siamois.domain.models.vocabulary.FieldVocabularyConfig;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class VocabularyResourceMapperTest {

    @Test
    void toResource_nullConfig_isNull() {
        assertThat(VocabularyResourceMapper.toResource(null)).isNull();
    }

    @Test
    void toResource_fieldCodeSource_carriesOnlyTheCode() {
        FieldResource.Vocabulary result = VocabularyResourceMapper.toResource(new FieldVocabularyConfig(
                FieldVocabularyConfig.Kind.FIELD_CODE, "SIARU.TYPE", null, null, VocabularyMode.FOLLOW, Map.of()));

        assertThat(result.source()).isEqualTo(new FieldResource.Source("FIELD_CODE", "SIARU.TYPE", null, null));
        assertThat(result.mode()).isEqualTo("FOLLOW");
        assertThat(result.excludedConceptIds()).isEmpty();
        assertThat(result.concepts()).isNull();
    }

    @Test
    void toResource_followedList_listsOnlyTheDisabledConcepts_inOrder() {
        Map<Long, ConceptState> states = new TreeMap<>();
        states.put(105L, ConceptState.DISABLED);
        states.put(101L, ConceptState.DISABLED);
        states.put(103L, ConceptState.ENABLED);

        FieldResource.Vocabulary result = VocabularyResourceMapper.toResource(new FieldVocabularyConfig(
                FieldVocabularyConfig.Kind.BRANCH, null, 99L, null, VocabularyMode.FOLLOW, states));

        assertThat(result.source()).isEqualTo(new FieldResource.Source("BRANCH", null, "99", null));
        assertThat(result.excludedConceptIds()).containsExactly("101", "105");
        assertThat(result.concepts()).isNull();
    }

    @Test
    void toResource_frozenList_listsEveryConceptWithItsState() {
        Map<Long, ConceptState> states = new TreeMap<>();
        states.put(102L, ConceptState.PENDING);
        states.put(101L, ConceptState.ENABLED);

        FieldResource.Vocabulary result = VocabularyResourceMapper.toResource(new FieldVocabularyConfig(
                FieldVocabularyConfig.Kind.COLLECTION, null, null, 55L, VocabularyMode.FROZEN, states));

        assertThat(result.source()).isEqualTo(new FieldResource.Source("COLLECTION", null, null, "55"));
        assertThat(result.mode()).isEqualTo("FROZEN");
        assertThat(result.excludedConceptIds()).isNull();
        assertThat(result.concepts()).containsExactly(
                new FieldResource.ConceptStateEntry("101", "ENABLED"),
                new FieldResource.ConceptStateEntry("102", "PENDING"));
    }
}
