package fr.siamois.domain.services.actionunit;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFormConfig;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.form.FormConfigService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.CodeSourceService;
import fr.siamois.domain.services.vocabulary.FieldCodeResolver;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptCollectionRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ThesaurusProjectConfigSeederTest {

    private static final Long PROJECT_ID = 42L;
    private static final String UE_TYPE_CODE = ConfigurableTable.UE.getFieldCode();

    private final DefaultProjectDefinition definition = new DefaultProjectDefinition();
    private final DefaultProjectDefinition.Type us = definition.tables().stream()
            .filter(table -> table.table() == ConfigurableTable.UE).findFirst().orElseThrow().types().get(0);

    @Mock private VocabularyRepository vocabularyRepository;
    @Mock private ConceptRepository conceptRepository;
    @Mock private ConceptCollectionRepository conceptCollectionRepository;
    @Mock private FieldCodeResolver fieldCodeResolver;
    @Mock private CodeSourceService codeSourceService;
    @Mock private TableFieldConfigService tableFieldConfigService;
    @Mock private FormConfigService formConfigService;
    @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private final Vocabulary vocabulary = new Vocabulary();
    private final FormConfig usFormConfig = new FormConfig();
    private ThesaurusProjectConfigSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new ThesaurusProjectConfigSeeder(definition, vocabularyRepository, conceptRepository,
                conceptCollectionRepository, fieldCodeResolver, codeSourceService, tableFieldConfigService, formConfigService, transactionManager);
        vocabulary.setExternalVocabularyId(definition.externalVocabularyId());
        when(vocabularyRepository.findVocabularyByBaseUriAndVocabExternalId(definition.baseUri(), definition.externalVocabularyId()))
                .thenReturn(Optional.of(vocabulary));
        when(fieldCodeResolver.findIn(any(), anyString())).thenReturn(Optional.empty());
        when(tableFieldConfigService.createOrGetFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.UE), anyLong()))
                .thenReturn(Optional.of(usFormConfig));
        when(tableFieldConfigService.findField(eq(PROJECT_ID), eq(ConfigurableTable.UE), anyLong(), anyString()))
                .thenAnswer(invocation -> Optional.of(CustomFieldSelectOneFromFieldCode.builder().build()));
    }

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
    }

    private static Concept conceptOf(long id) {
        Concept concept = new Concept();
        concept.setId(id);
        return concept;
    }

    private static ConceptCollection collectionOf(String externalId) {
        ConceptCollection collection = new ConceptCollection();
        collection.setExternalId(externalId);
        return collection;
    }

    private void thesaurusDefinesUeTypes() {
        ConceptFieldConfig typeCode = new ConceptFieldConfig();
        typeCode.setFieldCode(UE_TYPE_CODE);
        when(fieldCodeResolver.findIn(vocabulary, UE_TYPE_CODE)).thenReturn(Optional.of(typeCode));
        definition.tables().stream().filter(table -> table.table() == ConfigurableTable.UE).findFirst().orElseThrow().types()
                .forEach(type -> when(conceptRepository.findConceptByExternalIdIgnoreCase(definition.externalVocabularyId(), type.concept()))
                        .thenReturn(Optional.of(conceptOf(Long.parseLong(type.concept())))));
    }

    @Test
    void seed_redirectsTheTypesOfTheTableToTheThesaurus_andConfiguresEachTypeOfTheDefinition() {
        thesaurusDefinesUeTypes();

        seeder.seed(mock(UserInfo.class), PROJECT_ID);

        verify(codeSourceService).useFieldCode(PROJECT_ID, UE_TYPE_CODE, vocabulary, UE_TYPE_CODE);
        verify(tableFieldConfigService, times(3)).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.UE), anyLong(), any(TypeFormConfig.class));
        org.mockito.ArgumentCaptor<TypeFormConfig> config = org.mockito.ArgumentCaptor.forClass(TypeFormConfig.class);
        verify(tableFieldConfigService).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.UE), eq(Long.parseLong(us.concept())), config.capture());
        assertThat(config.getValue().getIdentifierFormat()).isEqualTo("US{NUM_UE:000}");
    }

    @Test
    void seed_usesTheCollectionTheDefinitionNamesForTheFieldAndTheType_elseTheFieldCodeOfTheThesaurus() {
        thesaurusDefinesUeTypes();
        String interpretationList = us.lists().get(RecordingUnit.INTERPRETATION_FIELD_CODE);
        ConceptCollection collection = collectionOf(interpretationList);
        when(conceptCollectionRepository.findByVocabularyAndExternalId(vocabulary, interpretationList)).thenReturn(Optional.of(collection));
        ConceptFieldConfig natureCode = new ConceptFieldConfig();
        when(fieldCodeResolver.findIn(vocabulary, RecordingUnit.CHRONOLOGICAL_ATTRIBUTION_FIELD_CODE)).thenReturn(Optional.of(natureCode));

        seeder.seed(mock(UserInfo.class), PROJECT_ID);

        // the interpretation of a US is its own collection, the one of "Fait" and "Ensemble" theirs
        verify(formConfigService, times(1)).addConceptConfigFor(eq(usFormConfig), any(CustomFieldConcept.class), eq(collection));
        // the chronology has no list per type: it follows its code, once for each of the three types
        verify(formConfigService, times(3)).addFieldCodeConfigFor(eq(usFormConfig), any(CustomFieldConcept.class), eq(vocabulary),
                eq(RecordingUnit.CHRONOLOGICAL_ATTRIBUTION_FIELD_CODE));
    }

    @Test
    void seed_readsAFieldWholeWhenItsCollectionIsNotImported() {
        thesaurusDefinesUeTypes();
        when(conceptCollectionRepository.findByVocabularyAndExternalId(any(), anyString())).thenReturn(Optional.empty());
        when(fieldCodeResolver.findIn(vocabulary, RecordingUnit.INTERPRETATION_FIELD_CODE)).thenReturn(Optional.of(new ConceptFieldConfig()));

        seeder.seed(mock(UserInfo.class), PROJECT_ID);

        verify(formConfigService, never()).addConceptConfigFor(any(FormConfig.class), any(CustomFieldConcept.class), any(ConceptCollection.class));
        verify(formConfigService, times(3)).addFieldCodeConfigFor(eq(usFormConfig), any(CustomFieldConcept.class), eq(vocabulary),
                eq(RecordingUnit.INTERPRETATION_FIELD_CODE));
    }

    @Test
    void seed_skipsATypeThatIsNotInTheLocalCopy_andSeedsTheOthers() {
        thesaurusDefinesUeTypes();
        DefaultProjectDefinition.Type ensemble = definition.tables().stream()
                .filter(table -> table.table() == ConfigurableTable.UE).findFirst().orElseThrow().types().get(2);
        when(conceptRepository.findConceptByExternalIdIgnoreCase(definition.externalVocabularyId(), ensemble.concept()))
                .thenReturn(Optional.empty());

        seeder.seed(mock(UserInfo.class), PROJECT_ID);

        verify(tableFieldConfigService, times(2)).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.UE), anyLong(), any(TypeFormConfig.class));
    }

    @Test
    void seed_doesNothingWhenTheThesaurusWasNeverImported() {
        when(vocabularyRepository.findVocabularyByBaseUriAndVocabExternalId(any(), any())).thenReturn(Optional.empty());

        seeder.seed(mock(UserInfo.class), PROJECT_ID);

        verifyNoInteractions(codeSourceService, tableFieldConfigService, formConfigService, transactionManager);
    }

    @Test
    void seed_restoresTheContextOfTheCaller() {
        UserInfo previous = mock(UserInfo.class);
        ExecutionContextHolder.set(previous);
        UserInfo user = mock(UserInfo.class);

        seeder.seed(user, PROJECT_ID);

        assertThat(ExecutionContextHolder.get()).isSameAs(previous);
    }
}
