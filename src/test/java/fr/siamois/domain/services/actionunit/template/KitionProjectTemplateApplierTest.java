package fr.siamois.domain.services.actionunit.template;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConceptFromFieldCode;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.FieldOptions;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.actionunit.DefaultProjectDefinition;
import fr.siamois.domain.services.form.FormConfigService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.CodeSourceService;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.measurement.UnitDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KitionProjectTemplateApplierTest {

    private static final long PROJECT = 7L;

    private final KitionProjectDefinition definition = new KitionProjectDefinition();

    @Mock private DefaultProjectDefinition commonCore;
    @Mock private ProjectThesaurusAssigner thesaurusAssigner;
    @Mock private CodeSourceService codeSourceService;
    @Mock private TableFieldConfigService tableFieldConfigService;
    @Mock private FormConfigService formConfigService;
    @Mock private VocabularyRepository vocabularyRepository;
    @Mock private ConceptRepository conceptRepository;
    @Mock private UnitDefinitionRepository unitDefinitionRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private KitionProjectTemplateApplier applier;

    @BeforeEach
    void setUp() {
        applier = new KitionProjectTemplateApplier(definition, commonCore, thesaurusAssigner, codeSourceService,
                tableFieldConfigService, formConfigService, vocabularyRepository, conceptRepository, unitDefinitionRepository, transactionManager);
        long[] next = {100};
        when(conceptRepository.findConceptByExternalIdIgnoreCase(eq("th182"), any())).thenAnswer(call -> {
            Concept concept = new Concept();
            concept.setId(next[0]++);
            return Optional.of(concept);
        });
        when(tableFieldConfigService.typeNameOf(eq(PROJECT), any(), anyLong()))
                .thenAnswer(call -> Optional.of("type-" + call.getArgument(2)));
        when(tableFieldConfigService.createOrGetFormConfig(eq(PROJECT), any(ConfigurableTable.class), anyLong()))
                .thenReturn(Optional.of(new FormConfig()));
        when(tableFieldConfigService.findField(eq(PROJECT), any(), anyLong(), any(String.class))).thenAnswer(call -> {
            String label = call.getArgument(3);
            return SystemFieldCatalog.fieldsOf(call.getArgument(1)).stream()
                    .filter(field -> label.equals(field.getLabel())).findFirst();
        });
    }

    @Test
    void supportsOnlyTheKitionTemplate() {
        assertTrue(applier.supports(ProjectTemplate.KITION_ID));
        assertFalse(applier.supports(ProjectTemplate.DEFAULT_ID));
        assertFalse(applier.supports(null));
        assertFalse(applier.transactional());
    }

    @Test
    void readsTheTypesOfEachTableFromABranchOfTheKitionThesaurus() throws Exception {
        applier.apply(mock(UserInfo.class), ProjectTemplate.KITION_ID, PROJECT);

        ArgumentCaptor<ConceptDTO> branch = ArgumentCaptor.forClass(ConceptDTO.class);
        verify(codeSourceService).useBranch(eq(PROJECT), eq(ConfigurableTable.UE.getFieldCode()), branch.capture(), any());
        assertEquals("2334010", branch.getValue().getExternalId());
        assertEquals("https://thesaurus.mom.fr/?idt=th182", branch.getValue().getVocabulary().completeUri());
        verify(codeSourceService).useBranch(eq(PROJECT), eq(ConfigurableTable.DOCUMENT.getFieldCode()), any(), any());
    }

    @Test
    void configuresEveryTypeOfTheDefinition() {
        applier.apply(mock(UserInfo.class), ProjectTemplate.KITION_ID, PROJECT);

        // 4 stratigraphic types + 4 kinds of documentation
        verify(tableFieldConfigService, times(4)).saveFormConfig(eq(PROJECT), eq(ConfigurableTable.UE), anyLong(), any());
        verify(tableFieldConfigService, times(4)).saveFormConfig(eq(PROJECT), eq(ConfigurableTable.DOCUMENT), anyLong(), any());
        verify(thesaurusAssigner).assignCommonCore(PROJECT);
    }

    @Test
    void addsTheTextFieldsToTheNamedTypesOnly() {
        applier.apply(mock(UserInfo.class), ProjectTemplate.KITION_ID, PROJECT);

        // "Interprétation (texte libre)" on US and Locus, "Homogénéité" and "Fiabilité" on US only
        verify(tableFieldConfigService, times(2)).createField(eq(PROJECT), eq(ConfigurableTable.UE), any(),
                eq("Interprétation (texte libre)"), eq(FieldType.TEXT), any(), eq(FieldOptions.NONE));
        verify(tableFieldConfigService, times(1)).createField(eq(PROJECT), eq(ConfigurableTable.UE), any(),
                eq("Homogénéité"), eq(FieldType.TEXT), any(), eq(FieldOptions.NONE));
        verify(tableFieldConfigService, times(1)).createField(eq(PROJECT), eq(ConfigurableTable.UE), any(),
                eq("Fiabilité"), eq(FieldType.TEXT), any(), eq(FieldOptions.NONE));
        verify(tableFieldConfigService, never()).createField(eq(PROJECT), eq(ConfigurableTable.DOCUMENT), any(), any(), any(), any(), any());
    }

    @Test
    void addsTheFourZCotesAsMeasurementsInMetresAndTheSheetDatesAsDates() {
        UnitDefinition metre = new UnitDefinition();
        metre.setId(9L);
        when(unitDefinitionRepository.findFirstBySymbolAndDimensionOrderByIdAsc("m", UnitDefinition.Dimension.LENGTH))
                .thenReturn(Optional.of(metre));

        applier.apply(mock(UserInfo.class), ProjectTemplate.KITION_ID, PROJECT);

        // 4 cotes on the stratigraphic unit and on the locus
        verify(tableFieldConfigService, times(2)).createField(eq(PROJECT), eq(ConfigurableTable.UE), any(),
                eq("Z inférieur 1"), eq(FieldType.MEASUREMENT), any(), eq(FieldOptions.units(9L, java.util.Set.of(9L))));
        verify(tableFieldConfigService, times(2)).createField(eq(PROJECT), eq(ConfigurableTable.UE), any(),
                eq("Z supérieur 2"), eq(FieldType.MEASUREMENT), any(), eq(FieldOptions.units(9L, java.util.Set.of(9L))));
        verify(tableFieldConfigService, times(2)).createField(eq(PROJECT), eq(ConfigurableTable.UE), any(),
                eq("Date de création de la fiche"), eq(FieldType.DATETIME), any(), eq(FieldOptions.NONE));
        // the commentary on the dating only exists on the stratigraphic unit
        verify(tableFieldConfigService, times(1)).createField(eq(PROJECT), eq(ConfigurableTable.UE), any(),
                eq("Commentaire de datation"), eq(FieldType.TEXT), any(), eq(FieldOptions.NONE));
    }

    @Test
    void readsTheChronologyOfEveryUeTypeFromItsBranchAndLoadsItOnce() {
        applier.apply(mock(UserInfo.class), ProjectTemplate.KITION_ID, PROJECT);

        ArgumentCaptor<CustomFieldConceptFromFieldCode> field = ArgumentCaptor.forClass(CustomFieldConceptFromFieldCode.class);
        ArgumentCaptor<ConceptDTO> branch = ArgumentCaptor.forClass(ConceptDTO.class);
        // the chronology is imported by the first type, the locus-nature list by the locus, the interpretations by the US
        verify(formConfigService, times(3)).addConceptConfigFor(any(FormConfig.class), field.capture(), branch.capture());
        assertEquals("SIARU.CHRONO", field.getAllValues().get(0).getFieldCode());
        assertEquals("2292534", branch.getAllValues().get(0).getExternalId());
        assertEquals("SIARU.INTERPRETATION", field.getAllValues().get(1).getFieldCode());
        assertEquals("2425024", branch.getAllValues().get(1).getExternalId());
        assertEquals("SIARU.INTERPRETATION", field.getAllValues().get(2).getFieldCode());
        assertEquals("42436", branch.getAllValues().get(2).getExternalId());
        // the other three UE types reuse the chronology just imported
        verify(formConfigService, times(3)).addConceptConfigForLoadedBranch(any(FormConfig.class), any(), any(ConceptDTO.class));
    }

    @Test
    void aStepThatFailsDoesNotStopTheOthers() {
        when(tableFieldConfigService.createField(eq(PROJECT), eq(ConfigurableTable.UE), any(), eq("Fiabilité"), any(), any(), any()))
                .thenThrow(new IllegalStateException("boom"));

        applier.apply(mock(UserInfo.class), ProjectTemplate.KITION_ID, PROJECT);

        verify(tableFieldConfigService, times(1)).createField(eq(PROJECT), eq(ConfigurableTable.UE), any(),
                eq("Homogénéité"), any(), any(), any());
    }
}
