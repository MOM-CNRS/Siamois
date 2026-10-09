package fr.siamois.domain.services.actionunit;

import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFormConfig;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultProjectIdentifierConfigSeederTest {

    private static final Long PROJECT_ID = 42L;
    private static final String US_URI = "https://opentheso2.mom.fr/?idc=4287627&idt=th1295";
    private static final String F_URI = "https://opentheso2.mom.fr/?idc=4287628&idt=th1295";

    @Mock private VocabularyService vocabularyService;
    @Mock private ConceptService conceptService;
    @Mock private TableFieldConfigService tableFieldConfigService;

    private DefaultProjectIdentifierConfigSeeder seeder;

    @Test
    void seed_shouldCreateBothTypesWithTheirPrefixedFormat() throws Exception {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService);

        Vocabulary vocabulary = new Vocabulary();
        Concept usConcept = new Concept();
        usConcept.setId(1L);
        Concept fConcept = new Concept();
        fConcept.setId(2L);

        when(vocabularyService.findOrCreateVocabularyOfUri(US_URI)).thenReturn(vocabulary);
        when(vocabularyService.findOrCreateVocabularyOfUri(F_URI)).thenReturn(vocabulary);
        when(conceptService.saveOrGetConceptFromUri(vocabulary, US_URI, null)).thenReturn(usConcept);
        when(conceptService.saveOrGetConceptFromUri(vocabulary, F_URI, null)).thenReturn(fConcept);

        seeder.seed(PROJECT_ID);

        ArgumentCaptor<TypeFormConfig> usConfig = ArgumentCaptor.forClass(TypeFormConfig.class);
        verify(tableFieldConfigService).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.UE), eq(1L), usConfig.capture());
        assertThat(usConfig.getValue().getIdentifierFormat()).isEqualTo("US{NUM_UE:00}");
        assertThat(usConfig.getValue().getMinCode()).isEqualTo(1);
        assertThat(usConfig.getValue().getMaxCode()).isEqualTo(999);

        ArgumentCaptor<TypeFormConfig> fConfig = ArgumentCaptor.forClass(TypeFormConfig.class);
        verify(tableFieldConfigService).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.UE), eq(2L), fConfig.capture());
        assertThat(fConfig.getValue().getIdentifierFormat()).isEqualTo("F{NUM_UE:00}");
    }

    @Test
    void seed_shouldSkipATypeWhenTheVocabularyCannotBeResolved_andStillProcessTheOther() throws Exception {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService);

        Vocabulary vocabulary = new Vocabulary();
        Concept fConcept = new Concept();
        fConcept.setId(2L);

        when(vocabularyService.findOrCreateVocabularyOfUri(US_URI)).thenThrow(new InvalidEndpointException("unreachable"));
        when(vocabularyService.findOrCreateVocabularyOfUri(F_URI)).thenReturn(vocabulary);
        when(conceptService.saveOrGetConceptFromUri(vocabulary, F_URI, null)).thenReturn(fConcept);

        seeder.seed(PROJECT_ID);

        verify(tableFieldConfigService, never()).saveFormConfig(anyLong(), eq(ConfigurableTable.UE), eq(1L), any());
        verify(tableFieldConfigService).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.UE), eq(2L), any());
    }

    @Test
    void seed_shouldSkipATypeWhenNoVocabularyIsConfiguredForTheProject() {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService);

        Vocabulary vocabulary = new Vocabulary();
        Concept usConcept = new Concept();
        usConcept.setId(1L);
        Concept fConcept = new Concept();
        fConcept.setId(2L);

        try {
            when(vocabularyService.findOrCreateVocabularyOfUri(US_URI)).thenReturn(vocabulary);
            when(vocabularyService.findOrCreateVocabularyOfUri(F_URI)).thenReturn(vocabulary);
        } catch (InvalidEndpointException e) {
            throw new AssertionError(e);
        }
        when(conceptService.saveOrGetConceptFromUri(vocabulary, US_URI, null)).thenReturn(usConcept);
        when(conceptService.saveOrGetConceptFromUri(vocabulary, F_URI, null)).thenReturn(fConcept);
        doThrow(new IllegalStateException("No vocabulary configured"))
                .when(tableFieldConfigService).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.UE), eq(1L), any());

        seeder.seed(PROJECT_ID);

        verify(tableFieldConfigService).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.UE), eq(2L), any());
    }

    @Test
    void seed_shouldGiveEveryConfiguredTableItsStandardType_withTheTablesOwnIdentifierFormat() throws Exception {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService);
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

        seeder.seed(PROJECT_ID);

        ArgumentCaptor<TypeFormConfig> config = ArgumentCaptor.forClass(TypeFormConfig.class);
        verify(tableFieldConfigService).saveFormConfig(eq(PROJECT_ID), eq(ConfigurableTable.MOBILIER), eq(77L), config.capture());
        assertThat(config.getValue().getIdentifierFormat()).isEqualTo(ConfigurableTable.MOBILIER.getDefaultIdentifierFormat());
    }

    @Test
    void seed_shouldLeaveATableWithoutAStandardTypeWhenNoneIsConfigured() throws Exception {
        seeder = new DefaultProjectIdentifierConfigSeeder(vocabularyService, conceptService, tableFieldConfigService);
        Vocabulary vocabulary = new Vocabulary();
        Concept concept = new Concept();
        concept.setId(1L);
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenReturn(vocabulary);
        when(conceptService.saveOrGetConceptFromUri(eq(vocabulary), anyString(), isNull())).thenReturn(concept);

        seeder.seed(PROJECT_ID);

        verify(tableFieldConfigService, never()).saveFormConfig(anyLong(), eq(ConfigurableTable.MOBILIER), anyLong(), any());
        verify(tableFieldConfigService, never()).saveFormConfig(anyLong(), eq(ConfigurableTable.PHASE), anyLong(), any());
    }
}
