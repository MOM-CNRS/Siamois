package fr.siamois.domain.services.actionunit;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFormConfig;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultProjectIdentifierConfigSeederTest {

    private static final Long PROJECT_ID = 42L;
    private static final String US_URI = "https://thesaurus.mom.fr/?idc=1199&idt=th337";

    @Mock private VocabularyService vocabularyService;
    @Mock private ConceptService conceptService;
    @Mock private TableFieldConfigService tableFieldConfigService;
    @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private static final UserInfo INFO = mock(UserInfo.class);

    private DefaultProjectIdentifierConfigSeeder seeder;

    @Test
    void seed_shouldCreateTheUsTypeWithItsPrefixedFormat() throws Exception {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService, transactionManager);

        Vocabulary vocabulary = new Vocabulary();
        Concept usConcept = new Concept();
        usConcept.setId(1L);

        when(vocabularyService.findOrCreateVocabularyOfUri(US_URI)).thenReturn(vocabulary);
        when(conceptService.saveOrGetConceptFromUri(vocabulary, US_URI, null)).thenReturn(usConcept);

        seeder.seed(INFO, PROJECT_ID);

        ArgumentCaptor<TypeFormConfig> usConfig = ArgumentCaptor.forClass(TypeFormConfig.class);
        verify(tableFieldConfigService).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.UE), eq(1L), usConfig.capture());
        assertThat(usConfig.getValue().getIdentifierFormat()).isEqualTo("US{NUM_UE:00}");
        assertThat(usConfig.getValue().getMinCode()).isEqualTo(1);
        assertThat(usConfig.getValue().getMaxCode()).isEqualTo(999);
    }

    @Test
    void seed_shouldSkipTheTypeWhenTheVocabularyCannotBeResolved() throws Exception {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService, transactionManager);

        when(vocabularyService.findOrCreateVocabularyOfUri(US_URI)).thenThrow(new InvalidEndpointException("unreachable"));

        seeder.seed(INFO, PROJECT_ID);

        verify(tableFieldConfigService, never()).saveFormConfig(anyLong(), eq(ConfigurableTable.UE), anyLong(), any());
    }

    @Test
    void seed_shouldNotFailWhenNoVocabularyIsConfiguredForTheProject() throws Exception {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService, transactionManager);

        Vocabulary vocabulary = new Vocabulary();
        Concept usConcept = new Concept();
        usConcept.setId(1L);

        when(vocabularyService.findOrCreateVocabularyOfUri(US_URI)).thenReturn(vocabulary);
        when(conceptService.saveOrGetConceptFromUri(vocabulary, US_URI, null)).thenReturn(usConcept);
        doThrow(new IllegalStateException("No vocabulary configured"))
                .when(tableFieldConfigService).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.UE), eq(1L), any());

        seeder.seed(INFO, PROJECT_ID);

        verify(tableFieldConfigService).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.UE), eq(1L), any());
    }

    @Test
    void seed_shouldGiveEveryConfiguredTableItsStandardType_withTheTablesOwnIdentifierFormat() throws Exception {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService, transactionManager);
        String standardUri = "https://opentheso2.mom.fr/?idc=1&idt=th277";
        org.springframework.test.util.ReflectionTestUtils.setField(seeder, "mobilierTypeUri", standardUri);

        Vocabulary vocabulary = new Vocabulary();
        Concept standard = new Concept();
        standard.setId(77L);
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenReturn(vocabulary);
        when(conceptService.saveOrGetConceptFromUri(eq(vocabulary), anyString(), isNull())).thenAnswer(invocation -> {
            String uri = invocation.getArgument(1);
            if (uri.equals(standardUri)) return standard;
            Concept other = new Concept();
            other.setId(1L);
            return other;
        });

        seeder.seed(INFO, PROJECT_ID);

        ArgumentCaptor<TypeFormConfig> config = ArgumentCaptor.forClass(TypeFormConfig.class);
        verify(tableFieldConfigService).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.MOBILIER), eq(77L), config.capture());
        assertThat(config.getValue().getIdentifierFormat()).isEqualTo(ConfigurableTable.MOBILIER.getDefaultIdentifierFormat());
    }

    @Test
    void seed_shouldLeaveATableWithoutAStandardTypeWhenNoneIsConfigured() throws Exception {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService, transactionManager);
        Vocabulary vocabulary = new Vocabulary();
        Concept concept = new Concept();
        concept.setId(1L);
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenReturn(vocabulary);
        when(conceptService.saveOrGetConceptFromUri(eq(vocabulary), anyString(), isNull())).thenReturn(concept);

        seeder.seed(INFO, PROJECT_ID);

        verify(tableFieldConfigService, never()).saveFormConfig(anyLong(), eq(ConfigurableTable.MOBILIER), anyLong(), any());
        verify(tableFieldConfigService, never()).saveFormConfig(anyLong(), eq(ConfigurableTable.PHASE), anyLong(), any());
    }

    @Test
    void seed_shouldCreateTheThreeStudyTypes_eachWithItsOwnActiveFields() throws Exception {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService, transactionManager);
        String findUri = "https://example.org/?idc=1&idt=th1";
        String ueUri = "https://example.org/?idc=2&idt=th1";
        String treatmentUri = "https://example.org/?idc=3&idt=th1";
        org.springframework.test.util.ReflectionTestUtils.setField(seeder, "studyFindTypeUri", findUri);
        org.springframework.test.util.ReflectionTestUtils.setField(seeder, "studyRecordingUnitTypeUri", ueUri);
        org.springframework.test.util.ReflectionTestUtils.setField(seeder, "studyTreatmentTypeUri", treatmentUri);

        Vocabulary vocabulary = new Vocabulary();
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenReturn(vocabulary);
        when(conceptService.saveOrGetConceptFromUri(eq(vocabulary), anyString(), isNull())).thenAnswer(invocation -> {
            String uri = invocation.getArgument(1);
            Concept concept = new Concept();
            concept.setId(uri.equals(findUri) ? 101L : uri.equals(ueUri) ? 102L : uri.equals(treatmentUri) ? 103L : 1L);
            return concept;
        });

        seeder.seed(INFO, PROJECT_ID);

        for (long id : new long[]{101L, 102L, 103L}) {
            ArgumentCaptor<TypeFormConfig> config = ArgumentCaptor.forClass(TypeFormConfig.class);
            verify(tableFieldConfigService).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.STUDY), eq(id), config.capture());
            assertThat(config.getValue().getIdentifierFormat()).isEqualTo("ETU{NUM_ETUDE:0000}");
        }

        // the studies of finds and of recording units use the two links, and none of the treatment columns
        for (long id : new long[]{101L, 102L}) {
            verify(tableFieldConfigService).setFieldActive(PROJECT_ID, ConfigurableTable.STUDY, id, "study.field.recordingUnits", true);
            verify(tableFieldConfigService).setFieldActive(PROJECT_ID, ConfigurableTable.STUDY, id, "study.field.specimens", true);
        }
        // the treatment uses the columns of the SDA sheet (its type is the study type), the treated find being mandatory
        verify(tableFieldConfigService).setFieldMandatory(PROJECT_ID, ConfigurableTable.STUDY, 103L, "study.field.specimens", true);
        verify(tableFieldConfigService).setFieldActive(PROJECT_ID, ConfigurableTable.STUDY, 103L, "study.field.recordingUnits", false);
        verify(tableFieldConfigService).setFieldActive(PROJECT_ID, ConfigurableTable.STUDY, 103L, "study.field.treatmentComments", true);
        verify(tableFieldConfigService).setFieldMandatory(PROJECT_ID, ConfigurableTable.STUDY, 103L, "study.field.treatmentComments", false);
    }

    @Test
    void seed_shouldSkipAStudyTypeThatCannotBeSeeded_andStillSeedTheOthers() throws Exception {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService, transactionManager);
        String brokenUri = "https://example.org/?idc=1&idt=th1";
        String goodUri = "https://example.org/?idc=2&idt=th1";
        org.springframework.test.util.ReflectionTestUtils.setField(seeder, "studyFindTypeUri", brokenUri);
        org.springframework.test.util.ReflectionTestUtils.setField(seeder, "studyRecordingUnitTypeUri", goodUri);
        Vocabulary vocabulary = new Vocabulary();
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenReturn(vocabulary);
        when(conceptService.saveOrGetConceptFromUri(eq(vocabulary), anyString(), isNull())).thenAnswer(invocation -> {
            Concept concept = new Concept();
            concept.setId(invocation.getArgument(1).equals(goodUri) ? 202L : 1L);
            return concept;
        });
        doThrow(new IllegalStateException("No vocabulary configured"))
                .when(tableFieldConfigService).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.STUDY), eq(1L), any());

        seeder.seed(INFO, PROJECT_ID);

        verify(tableFieldConfigService, never()).setFieldActive(eq(PROJECT_ID), eq(ConfigurableTable.STUDY), eq(1L), anyString(), anyBoolean());
        verify(tableFieldConfigService).setFieldActive(PROJECT_ID, ConfigurableTable.STUDY, 202L, "study.field.recordingUnits", true);
    }

    @Test
    void seed_shouldLeaveTheStudyTableWithoutTypesWhenNoUriIsConfigured() throws Exception {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService, transactionManager);
        Vocabulary vocabulary = new Vocabulary();
        Concept concept = new Concept();
        concept.setId(1L);
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenReturn(vocabulary);
        when(conceptService.saveOrGetConceptFromUri(eq(vocabulary), anyString(), isNull())).thenReturn(concept);

        seeder.seed(INFO, PROJECT_ID);

        verify(tableFieldConfigService, never()).saveFormConfig(anyLong(), eq(ConfigurableTable.STUDY), anyLong(), any());
    }

    @Test
    void seed_shouldBindTheUserToTheThreadWhileSeeding_andUnbindItAfterwards() throws Exception {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService, transactionManager);
        Vocabulary vocabulary = new Vocabulary();
        Concept concept = new Concept();
        concept.setId(1L);
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenReturn(vocabulary);
        when(conceptService.saveOrGetConceptFromUri(eq(vocabulary), anyString(), isNull())).thenReturn(concept);
        List<UserInfo> boundDuringSave = new ArrayList<>();
        doAnswer(invocation -> boundDuringSave.add(ExecutionContextHolder.get()))
                .when(tableFieldConfigService).saveFormConfig(anyLong(), any(), anyLong(), any());

        ExecutionContextHolder.clear();
        seeder.seed(INFO, PROJECT_ID);

        assertThat(boundDuringSave).isNotEmpty().allMatch(bound -> bound == INFO);
        assertThat(ExecutionContextHolder.get()).isNull();
    }

    @Test
    void seed_shouldPutBackTheUserThatWasBoundBeforeIt() {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService, transactionManager);
        UserInfo caller = mock(UserInfo.class);
        ExecutionContextHolder.set(caller);
        try {
            seeder.seed(INFO, PROJECT_ID);

            assertThat(ExecutionContextHolder.get()).isSameAs(caller);
        } finally {
            ExecutionContextHolder.clear();
        }
    }

    @Test
    void seed_shouldCommitTheConceptInATransactionOfItsOwn_beforeTheFormConfigIsSaved() throws Exception {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService, transactionManager);
        Vocabulary vocabulary = new Vocabulary();
        Concept usConcept = new Concept();
        usConcept.setId(1L);
        when(vocabularyService.findOrCreateVocabularyOfUri(US_URI)).thenReturn(vocabulary);
        when(conceptService.saveOrGetConceptFromUri(vocabulary, US_URI, null)).thenReturn(usConcept);

        seeder.seed(INFO, PROJECT_ID);

        // the form config services open transactions of their own: they only see a concept that is committed
        InOrder order = inOrder(transactionManager, tableFieldConfigService);
        order.verify(transactionManager).commit(any());
        order.verify(tableFieldConfigService).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.UE), eq(1L), any());
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, atLeastOnce()).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
}
