package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConceptRef;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportFieldResolverTest {

    @Mock private TableFieldConfigService tableFieldConfigService;
    @Mock private ConceptRepository conceptRepository;

    private ExportFieldResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ExportFieldResolver(tableFieldConfigService, conceptRepository);
    }

    private static Concept concept(Long id, String thesaurus, String externalId) {
        Vocabulary v = new Vocabulary();
        v.setExternalVocabularyId(thesaurus);
        Concept c = new Concept();
        c.setId(id);
        c.setExternalId(externalId);
        c.setVocabulary(v);
        return c;
    }

    private static CustomField additional(Long id, Concept concept) {
        return CustomFieldText.builder().id(id).label("f" + id).concept(concept).build();
    }

    @Test
    void resolve_systemField_isFoundByItsConceptWithoutTouchingTheProjectConfig() {
        ConceptRef oaCode = new ConceptRef("th230", "4290928", null);

        List<CustomField> result = resolver.resolve(ExportSubject.PROJECT, oaCode, 1L, List.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getValueBinding()).isEqualTo("oaCode");
    }

    @Test
    void resolve_matchesCaseInsensitively() {
        ConceptRef oaCode = new ConceptRef("TH230", "4290928", null);

        assertThat(resolver.resolve(ExportSubject.PROJECT, oaCode, 1L, List.of())).hasSize(1);
    }

    @Test
    void resolve_additionalField_isFoundAcrossTheConfiguredTypes() {
        Concept shared = concept(10L, "th99", "777");
        Concept typeA = concept(1L, "th230", "A");
        Concept typeB = concept(2L, "th230", "B");
        CustomField forA = additional(100L, shared);
        CustomField forB = additional(200L, shared);
        when(tableFieldConfigService.listConfiguredTypeConcepts(5L, ConfigurableTable.UE)).thenReturn(List.of(typeA, typeB));
        when(tableFieldConfigService.getActiveAdditionalFields(5L, ConfigurableTable.UE, 1L)).thenReturn(List.of(forA, additional(101L, concept(11L, "th99", "other"))));
        when(tableFieldConfigService.getActiveAdditionalFields(5L, ConfigurableTable.UE, 2L)).thenReturn(List.of(forB));

        List<CustomField> result = resolver.resolve(ExportSubject.RECORDING_UNIT, new ConceptRef("th99", "777", null), 5L, List.of());

        assertThat(result).extracting(CustomField::getId).containsExactly(100L, 200L);
    }

    @Test
    void resolve_withTypeFilter_onlyLooksAtThoseTypes() {
        Concept typeA = concept(1L, "th230", "A");
        CustomField field = additional(100L, concept(10L, "th99", "777"));
        when(conceptRepository.findConceptByExternalIdIgnoreCase("th230", "A")).thenReturn(Optional.of(typeA));
        when(tableFieldConfigService.getActiveAdditionalFields(5L, ConfigurableTable.UE, 1L)).thenReturn(List.of(field));

        List<CustomField> result = resolver.resolve(ExportSubject.RECORDING_UNIT, new ConceptRef("th99", "777", null), 5L,
                List.of(new ConceptRef("th230", "A", null)));

        assertThat(result).containsExactly(field);
    }

    @Test
    void resolve_unknownConcept_returnsEmptyAndDoesNotThrow() {
        when(tableFieldConfigService.listConfiguredTypeConcepts(5L, ConfigurableTable.UE)).thenReturn(List.of());

        assertThat(resolver.resolve(ExportSubject.RECORDING_UNIT, new ConceptRef("nope", "0", null), 5L, List.of())).isEmpty();
    }

    @Test
    void resolve_projectAdditionalField_isNotSupported() {
        assertThat(resolver.resolve(ExportSubject.PROJECT, new ConceptRef("th99", "777", null), 5L, List.of())).isEmpty();
    }

    @Test
    void listFields_systemFirst_thenAdditional_withoutDuplicatesOrConceptlessFields() {
        Concept shared = concept(10L, "th99", "777");
        CustomField forA = additional(100L, shared);
        CustomField forB = additional(200L, shared);
        CustomField noConcept = CustomFieldText.builder().id(300L).label("x").build();
        when(tableFieldConfigService.listConfiguredTypeConcepts(5L, ConfigurableTable.UE))
                .thenReturn(List.of(concept(1L, "th230", "A"), concept(2L, "th230", "B")));
        when(tableFieldConfigService.getActiveAdditionalFields(5L, ConfigurableTable.UE, 1L)).thenReturn(List.of(forA, noConcept));
        when(tableFieldConfigService.getActiveAdditionalFields(5L, ConfigurableTable.UE, 2L)).thenReturn(List.of(forB));

        List<ExportFieldResolver.FieldOption> options = resolver.listFields(ExportSubject.RECORDING_UNIT, 5L);

        assertThat(options).filteredOn(o -> !o.system()).singleElement()
                .satisfies(o -> assertThat(o.concept()).isEqualTo(new ConceptRef("th99", "777", null)));
        assertThat(options).filteredOn(ExportFieldResolver.FieldOption::system).isNotEmpty();
        assertThat(options).extracting(ExportFieldResolver.FieldOption::concept).doesNotHaveDuplicates();
    }

    @Test
    void listFields_forAProject_isSystemOnly() {
        assertThat(resolver.listFields(ExportSubject.PROJECT, 5L)).isNotEmpty().allMatch(ExportFieldResolver.FieldOption::system);
    }

    @Test
    void listFields_withoutProject_isSystemOnly() {
        assertThat(resolver.listFields(ExportSubject.RECORDING_UNIT, null)).isNotEmpty().allMatch(ExportFieldResolver.FieldOption::system);
    }
}
