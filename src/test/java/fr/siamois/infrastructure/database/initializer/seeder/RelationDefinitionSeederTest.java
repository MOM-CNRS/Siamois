package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEntityKind;
import fr.siamois.domain.models.relation.RelationMappingMode;
import fr.siamois.domain.models.relation.RelationMirror;
import fr.siamois.domain.models.relation.RelationRule;
import fr.siamois.domain.models.relation.RelationRuleKind;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.models.relation.RelationTypeMapping;
import fr.siamois.domain.models.relation.SystemRelations;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationTypeMappingRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationDefinitionSeederTest {

    @Mock VocabularyRepository vocabularyRepository;
    @Mock VocabularyTypeRepository vocabularyTypeRepository;
    @Mock LabelService labelService;
    @Mock ConceptSeeder conceptSeeder;
    @Mock ConceptRepository conceptRepository;
    @Mock RelationDefinitionRepository definitionRepository;
    @Mock RelationTypeMappingRepository mappingRepository;
    @Mock RelationRuleRepository ruleRepository;
    @Mock CustomFieldRepository customFieldRepository;
    @Mock FormConfigRepository formConfigRepository;
    @Mock FieldFormConfigRepository fieldFormConfigRepository;

    private RelationDefinitionSeeder seeder;
    private final Map<String, Concept> concepts = new HashMap<>();

    @BeforeEach
    void setUp() {
        seeder = new RelationDefinitionSeeder(vocabularyRepository, vocabularyTypeRepository, labelService, conceptSeeder,
                conceptRepository, definitionRepository, mappingRepository, ruleRepository,
                customFieldRepository, formConfigRepository, fieldFormConfigRepository);
        when(conceptSeeder.findConceptOrThrow(any())).thenAnswer(inv -> {
            ConceptSeeder.ConceptKey key = inv.getArgument(0);
            return concepts.computeIfAbsent(key.conceptExtId(), id -> {
                Concept c = new Concept();
                c.setExternalId(id);
                return c;
            });
        });
        when(conceptRepository.save(any(Concept.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void emptyBase() {
        when(vocabularyRepository.findVocabularyByBaseUriAndVocabExternalId(any(), any())).thenReturn(Optional.empty());
        when(vocabularyTypeRepository.findVocabularyTypeByLabel("Thesaurus")).thenReturn(Optional.of(new VocabularyType()));
        when(vocabularyRepository.save(any(Vocabulary.class))).thenAnswer(inv -> inv.getArgument(0));
        when(definitionRepository.findByCode(any())).thenReturn(Optional.empty());
        when(definitionRepository.save(any(RelationDefinition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mappingRepository.findByDefinition(any())).thenReturn(List.of());
        when(ruleRepository.findByKindAndDefinitionsContaining(any(), any())).thenReturn(List.of());
        // a field row, once saved, is found by the next definition (as the database would)
        java.util.concurrent.atomic.AtomicReference<CustomField> savedField = new java.util.concurrent.atomic.AtomicReference<>();
        when(customFieldRepository.findById(SystemRelations.UNCERTAIN_FIELD_ID))
                .thenAnswer(inv -> Optional.ofNullable(savedField.get()));
        when(customFieldRepository.save(any(CustomField.class))).thenAnswer(inv -> {
            savedField.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(formConfigRepository.save(any(FormConfig.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void seed_onAnEmptyBase_createsTheLocalVocabularyTheTwoDefinitionsTheirMappingsAndRules() {
        emptyBase();

        seeder.seed();

        ArgumentCaptor<Vocabulary> vocabulary = ArgumentCaptor.forClass(Vocabulary.class);
        verify(vocabularyRepository).save(vocabulary.capture());
        assertThat(vocabulary.getValue().getExternalVocabularyId()).isEqualTo(SystemRelations.VOCABULARY_ID);
        verify(labelService, times(2)).updateLabel(any(Vocabulary.class), any(), any());
        // a concept per side and per type, each labelled in French and English
        verify(conceptSeeder, times(15)).seed(any(), any());

        ArgumentCaptor<RelationDefinition> definitions = ArgumentCaptor.forClass(RelationDefinition.class);
        // each definition is saved when created, then again once its link form is attached
        verify(definitionRepository, times(4)).save(definitions.capture());
        RelationDefinition order = definitions.getAllValues().get(0);
        assertThat(order.getCode()).isEqualTo(SystemRelations.ORDER_CODE);
        assertThat(order.isSystem()).isTrue();
        assertThat(order.getMirror()).isEqualTo(RelationMirror.OTHER);
        assertThat(order.getSourceSideConcept().getExternalId()).isEqualTo(SystemRelations.SIDE_POSTERIOR);
        assertThat(order.getTargetSideConcept().getExternalId()).isEqualTo(SystemRelations.SIDE_ANTERIOR);
        assertThat(order.kindsOf(RelationSide.SOURCE)).extracting(k -> k.getEntityKind())
                .containsExactly(RelationEntityKind.RECORDING_UNIT);
        assertThat(order.kindsOf(RelationSide.TARGET)).hasSize(1);

        RelationDefinition synchronous = definitions.getAllValues().get(1);
        assertThat(synchronous.getCode()).isEqualTo(SystemRelations.SYNCHRONOUS_CODE);
        assertThat(synchronous.getMirror()).isEqualTo(RelationMirror.SELF);
        assertThat(synchronous.getTargetSideConcept()).isNull();

        // the link form: a system boolean "uncertain" on a form config of its own, shared by the two definitions' field
        assertThat(order.getLinkFormConfig()).isNotNull();
        assertThat(synchronous.getLinkFormConfig()).isNotNull().isNotSameAs(order.getLinkFormConfig());
        ArgumentCaptor<FieldFormConfig> fieldConfigs = ArgumentCaptor.forClass(FieldFormConfig.class);
        verify(fieldFormConfigRepository, times(2)).save(fieldConfigs.capture());
        assertThat(fieldConfigs.getAllValues()).allMatch(c -> c.getField().getId() == SystemRelations.UNCERTAIN_FIELD_ID && c.isActive());
        // one field row for both: created once
        verify(customFieldRepository, times(1)).save(any(CustomField.class));

        ArgumentCaptor<RelationTypeMapping> mappings = ArgumentCaptor.forClass(RelationTypeMapping.class);
        verify(mappingRepository, times(4)).save(mappings.capture());
        assertThat(mappings.getAllValues()).allMatch(m -> m.getMode() == RelationMappingMode.IMPOSED && m.getDefinition() == order);
        assertThat(mappings.getAllValues().get(0).getSourceType().getExternalId()).isEqualTo(SystemRelations.TYPE_CUTS);
        assertThat(mappings.getAllValues().get(0).getTargetType().getExternalId()).isEqualTo(SystemRelations.TYPE_CUT_BY);

        ArgumentCaptor<RelationRule> rules = ArgumentCaptor.forClass(RelationRule.class);
        verify(ruleRepository, times(3)).save(rules.capture());
        assertThat(rules.getAllValues()).extracting(RelationRule::getKind)
                .containsExactly(RelationRuleKind.NO_CYCLE, RelationRuleKind.PAIR_CARDINALITY, RelationRuleKind.EXCLUSION);
        assertThat(rules.getAllValues().get(1).getMaxCount()).isEqualTo(1);
        // the exclusion covers both definitions: an order link and a synchronous one cannot join the same pair
        assertThat(rules.getAllValues().get(2).getDefinitions()).containsExactlyInAnyOrder(order, synchronous);
    }

    @Test
    void seed_onAnAlreadySeededBase_createsNothing() {
        Vocabulary vocabulary = new Vocabulary();
        when(vocabularyRepository.findVocabularyByBaseUriAndVocabExternalId(any(), any())).thenReturn(Optional.of(vocabulary));

        RelationDefinition order = existingDefinition(SystemRelations.ORDER_CODE, RelationMirror.OTHER);
        RelationDefinition synchronous = existingDefinition(SystemRelations.SYNCHRONOUS_CODE, RelationMirror.SELF);
        order.setLinkFormConfig(new FormConfig());
        synchronous.setLinkFormConfig(new FormConfig());
        when(definitionRepository.findByCode(SystemRelations.ORDER_CODE)).thenReturn(Optional.of(order));
        when(definitionRepository.findByCode(SystemRelations.SYNCHRONOUS_CODE)).thenReturn(Optional.of(synchronous));

        List<RelationTypeMapping> known = List.of(
                mapping(order, SystemRelations.TYPE_CUTS, SystemRelations.TYPE_CUT_BY),
                mapping(order, SystemRelations.TYPE_COVERS, SystemRelations.TYPE_COVERED_BY),
                mapping(order, SystemRelations.TYPE_FILLS, SystemRelations.TYPE_FILLED_BY),
                mapping(order, SystemRelations.TYPE_ABUTS, SystemRelations.TYPE_ABUTTED_BY));
        when(mappingRepository.findByDefinition(order)).thenReturn(known);

        when(ruleRepository.findByKindAndDefinitionsContaining(RelationRuleKind.NO_CYCLE, order))
                .thenReturn(List.of(rule(RelationRuleKind.NO_CYCLE, order)));
        when(ruleRepository.findByKindAndDefinitionsContaining(RelationRuleKind.PAIR_CARDINALITY, order))
                .thenReturn(List.of(rule(RelationRuleKind.PAIR_CARDINALITY, order)));
        when(ruleRepository.findByKindAndDefinitionsContaining(eq(RelationRuleKind.EXCLUSION), any()))
                .thenReturn(List.of(rule(RelationRuleKind.EXCLUSION, order, synchronous)));

        seeder.seed();

        verify(vocabularyRepository, never()).save(any(Vocabulary.class));
        verify(definitionRepository, never()).save(any(RelationDefinition.class));
        verify(mappingRepository, never()).save(any(RelationTypeMapping.class));
        verify(ruleRepository, never()).save(any(RelationRule.class));
        verify(fieldFormConfigRepository, never()).save(any(FieldFormConfig.class));
    }

    @Test
    void seed_anExistingDefinitionIsLeftAsConfigured() {
        emptyBase();
        RelationDefinition order = existingDefinition(SystemRelations.ORDER_CODE, RelationMirror.NONE);
        order.setLinkFormConfig(new FormConfig());
        when(definitionRepository.findByCode(SystemRelations.ORDER_CODE)).thenReturn(Optional.of(order));

        seeder.seed();

        // its mirror was changed by an administrator: the seed does not put it back
        assertThat(order.getMirror()).isEqualTo(RelationMirror.NONE);
        verify(definitionRepository, times(2)).save(any(RelationDefinition.class)); // only the synchronous one: created, then its link form
    }

    private RelationDefinition existingDefinition(String code, RelationMirror mirror) {
        RelationDefinition definition = new RelationDefinition();
        definition.setCode(code);
        definition.setMirror(mirror);
        return definition;
    }

    private RelationTypeMapping mapping(RelationDefinition definition, String source, String target) {
        RelationTypeMapping mapping = new RelationTypeMapping();
        mapping.setDefinition(definition);
        mapping.setSourceType(concept(source));
        mapping.setTargetType(concept(target));
        return mapping;
    }

    private Concept concept(String externalId) {
        return concepts.computeIfAbsent(externalId, id -> {
            Concept c = new Concept();
            c.setExternalId(id);
            return c;
        });
    }

    private RelationRule rule(RelationRuleKind kind, RelationDefinition... definitions) {
        RelationRule rule = new RelationRule();
        rule.setKind(kind);
        rule.getDefinitions().addAll(List.of(definitions));
        return rule;
    }
}
