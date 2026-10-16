package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FieldWidth;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldBoolean;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEntityKind;
import fr.siamois.domain.models.relation.RelationMappingMode;
import fr.siamois.domain.models.relation.RelationMirror;
import fr.siamois.domain.models.relation.RelationRule;
import fr.siamois.domain.models.relation.RelationRuleKind;
import fr.siamois.domain.models.relation.RelationRuleSeverity;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static fr.siamois.domain.models.relation.SystemRelations.*;

/**
 * Creates the platform's relation definitions ({@link SystemRelations}) and the concepts that name their
 * sides and types, in a local vocabulary (no external thesaurus call). Idempotent: an existing definition is
 * left as it is (its labels and vocabularies are the institution's to configure), and only what is missing
 * (concepts, labels, type mappings, rules) is added.
 */
@Service
@RequiredArgsConstructor
public class RelationDefinitionSeeder {

    private static final String FR = "fr";
    private static final String EN = "en";

    private final VocabularyRepository vocabularyRepository;
    private final VocabularyTypeRepository vocabularyTypeRepository;
    private final LabelService labelService;
    private final ConceptSeeder conceptSeeder;
    private final ConceptRepository conceptRepository;
    private final RelationDefinitionRepository definitionRepository;
    private final RelationTypeMappingRepository mappingRepository;
    private final RelationRuleRepository ruleRepository;
    private final CustomFieldRepository customFieldRepository;
    private final FormConfigRepository formConfigRepository;
    private final FieldFormConfigRepository fieldFormConfigRepository;

    private record Label(String externalId, String fr, String en) {
    }

    private static final List<Label> CONCEPTS = List.of(
            new Label(SIDE_POSTERIOR, "Postérieur", "Posterior"),
            new Label(SIDE_ANTERIOR, "Antérieur", "Anterior"),
            new Label(SIDE_SYNCHRONOUS, "Synchrone", "Synchronous"),
            new Label(FIELD_UNCERTAIN, "Incertain", "Uncertain"),
            new Label(TYPE_CUTS, "recoupe", "cuts"),
            new Label(TYPE_COVERS, "recouvre", "covers"),
            new Label(TYPE_FILLS, "comble", "fills"),
            new Label(TYPE_ABUTS, "s'appuie sur", "abuts"),
            new Label(TYPE_CUT_BY, "est recoupé par", "is cut by"),
            new Label(TYPE_COVERED_BY, "est recouvert par", "is covered by"),
            new Label(TYPE_FILLED_BY, "est comblé par", "is filled by"),
            new Label(TYPE_ABUTTED_BY, "est appuyé par", "is abutted by"),
            new Label(TYPE_EQUAL_TO, "égal à", "equal to"),
            new Label(TYPE_CONTEMPORARY_WITH, "contemporain de", "contemporary with"),
            new Label(TYPE_BONDED_TO, "se lie à", "bonded to")
    );

    /** The order relation's imposed correspondence: the type chosen on the source side forces the target side's. */
    private static final List<String[]> ORDER_MAPPINGS = List.of(
            new String[]{TYPE_CUTS, TYPE_CUT_BY},
            new String[]{TYPE_COVERS, TYPE_COVERED_BY},
            new String[]{TYPE_FILLS, TYPE_FILLED_BY},
            new String[]{TYPE_ABUTS, TYPE_ABUTTED_BY}
    );

    @Transactional
    public void seed() {
        Vocabulary vocabulary = seedVocabulary();
        Map<String, Concept> concepts = seedConcepts(vocabulary);

        RelationDefinition order = seedDefinition(ORDER_CODE, RelationMirror.OTHER,
                concepts.get(SIDE_POSTERIOR), concepts.get(SIDE_ANTERIOR));
        RelationDefinition synchronous = seedDefinition(SYNCHRONOUS_CODE, RelationMirror.SELF,
                concepts.get(SIDE_SYNCHRONOUS), null);

        seedLinkForm(order, concepts);
        seedLinkForm(synchronous, concepts);

        for (String[] pair : ORDER_MAPPINGS) {
            seedMapping(order, concepts.get(pair[0]), concepts.get(pair[1]));
        }

        seedRule(RelationRuleKind.NO_CYCLE, null, Set.of(order));
        seedRule(RelationRuleKind.PAIR_CARDINALITY, 1, Set.of(order));
        seedRule(RelationRuleKind.EXCLUSION, null, Set.of(order, synchronous));
    }

    private Vocabulary seedVocabulary() {
        Vocabulary existing = vocabularyRepository
                .findVocabularyByBaseUriAndVocabExternalId(VOCABULARY_BASE_URI, VOCABULARY_ID).orElse(null);
        if (existing != null) return existing;

        VocabularyType type = vocabularyTypeRepository.findVocabularyTypeByLabel("Thesaurus")
                .orElseThrow(() -> new IllegalStateException("Thesaurus type not found"));
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setBaseUri(VOCABULARY_BASE_URI);
        vocabulary.setExternalVocabularyId(VOCABULARY_ID);
        vocabulary.setType(type);
        Vocabulary saved = vocabularyRepository.save(vocabulary);
        labelService.updateLabel(saved, FR, "Relations SIAMOIS");
        labelService.updateLabel(saved, EN, "SIAMOIS relations");
        return saved;
    }

    private Map<String, Concept> seedConcepts(Vocabulary vocabulary) {
        Map<String, Concept> byExternalId = new HashMap<>();
        for (Label label : CONCEPTS) {
            conceptSeeder.seed(vocabulary, List.of(
                    new ConceptSeeder.ConceptSpec(VOCABULARY_ID, label.externalId(), label.fr(), FR),
                    new ConceptSeeder.ConceptSpec(VOCABULARY_ID, label.externalId(), label.en(), EN)));
            Concept concept = conceptSeeder.findConceptOrThrow(new ConceptSeeder.ConceptKey(VOCABULARY_ID, label.externalId()));
            if (!concept.isLoaded()) {
                // labelled locally: nothing to fetch from an external thesaurus
                concept.setLoaded(true);
                concept = conceptRepository.save(concept);
            }
            byExternalId.put(label.externalId(), concept);
        }
        return byExternalId;
    }

    private RelationDefinition seedDefinition(String code, RelationMirror mirror, Concept sourceSide, Concept targetSide) {
        RelationDefinition existing = definitionRepository.findByCode(code).orElse(null);
        if (existing != null) return existing;

        RelationDefinition definition = new RelationDefinition();
        definition.setCode(code);
        definition.setSystem(true);
        definition.setMirror(mirror);
        definition.setSourceSideConcept(sourceSide);
        definition.setTargetSideConcept(targetSide);
        definition.addKind(RelationSide.SOURCE, RelationEntityKind.RECORDING_UNIT, null);
        definition.addKind(RelationSide.TARGET, RelationEntityKind.RECORDING_UNIT, null);
        return definitionRepository.save(definition);
    }

    /** Gives a definition created by an institution the same link form as the platform's: one optional "uncertain" boolean. */
    @Transactional
    public void addUncertainLinkForm(RelationDefinition definition) {
        Concept uncertain = conceptSeeder.findConceptOrThrow(new ConceptSeeder.ConceptKey(VOCABULARY_ID, FIELD_UNCERTAIN));
        seedLinkForm(definition, Map.of(FIELD_UNCERTAIN, uncertain));
    }

    /** The platform definitions' link form: one optional boolean, "uncertain". Created once, kept as configured. */
    private void seedLinkForm(RelationDefinition definition, Map<String, Concept> concepts) {
        if (definition.getLinkFormConfig() != null) return;

        CustomField uncertain = customFieldRepository.findById(UNCERTAIN_FIELD_ID).orElseGet(() ->
                customFieldRepository.save(CustomFieldBoolean.builder()
                        .id(UNCERTAIN_FIELD_ID)
                        .label(UNCERTAIN_FIELD_LABEL)
                        .isSystemField(true)
                        .valueBinding("uncertain")
                        .concept(concepts.get(FIELD_UNCERTAIN))
                        .allowNoAnswer(false)
                        .build()));

        FormConfig config = new FormConfig();
        config.setFieldConcept(definition.getSourceSideConcept());
        config.setIdentifierFormat("");
        config = formConfigRepository.save(config);

        FieldFormConfig fieldConfig = new FieldFormConfig();
        fieldConfig.setField(uncertain);
        fieldConfig.setFormConfig(config);
        fieldConfig.setActive(true);
        fieldConfig.setPosition(1);
        fieldConfig.setWidth(FieldWidth.HALF);
        fieldFormConfigRepository.save(fieldConfig);

        definition.setLinkFormConfig(config);
        definitionRepository.save(definition);
    }

    private void seedMapping(RelationDefinition definition, Concept sourceType, Concept targetType) {
        boolean known = mappingRepository.findByDefinition(definition).stream()
                .anyMatch(m -> sourceType.equals(m.getSourceType()) && targetType.equals(m.getTargetType()));
        if (known) return;

        RelationTypeMapping mapping = new RelationTypeMapping();
        mapping.setDefinition(definition);
        mapping.setSourceType(sourceType);
        mapping.setTargetType(targetType);
        mapping.setMode(RelationMappingMode.IMPOSED);
        mappingRepository.save(mapping);
    }

    /** A rule is created once: found by its kind among the rules of its first definition, whatever else it covers. */
    private void seedRule(RelationRuleKind kind, Integer maxCount, Set<RelationDefinition> definitions) {
        RelationDefinition anchor = definitions.iterator().next();
        boolean known = ruleRepository.findByKindAndDefinitionsContaining(kind, anchor).stream()
                .anyMatch(r -> r.getDefinitions().equals(definitions));
        if (known) return;

        RelationRule rule = new RelationRule();
        rule.setKind(kind);
        rule.setSeverity(RelationRuleSeverity.BLOCKING);
        rule.setMaxCount(maxCount);
        rule.getDefinitions().addAll(definitions);
        ruleRepository.save(rule);
    }
}
