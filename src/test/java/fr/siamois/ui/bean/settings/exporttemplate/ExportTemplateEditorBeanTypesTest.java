package fr.siamois.ui.bean.settings.exporttemplate;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.exporttemplate.ExportFieldResolver;
import fr.siamois.domain.services.exporttemplate.ExportSourceReader;
import fr.siamois.domain.services.exporttemplate.ExportSourceReader.SourceCounts;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.ConceptPrefLabelDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.bean.LabelBean;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditModel.EditSheet;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditModel.EditSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Types connus du projet de référence : alignement des clés d'un modèle, types des lieux, sources choisies par type. */
class ExportTemplateEditorBeanTypesTest {

    private static final String PARCEL_URI = "https://thesaurus.mom.fr/?idc=1165&idt=th338";

    private TableFieldConfigService tableService;
    private LabelBean labelBean;
    private ExportTemplateEditorBean bean;
    private EditSheet sheet;

    @BeforeEach
    void setUp() {
        tableService = mock(TableFieldConfigService.class);
        labelBean = mock(LabelBean.class);
        ExportFieldResolver resolver = mock(ExportFieldResolver.class);
        when(resolver.listFields(any(), any())).thenReturn(List.of());
        ExportSourceReader reader = mock(ExportSourceReader.class);
        when(reader.count(any(), anyLong())).thenReturn(new SourceCounts(0, Map.of()));
        LangBean lang = mock(LangBean.class);
        when(lang.msg(any(String.class))).thenAnswer(i -> i.getArgument(0));
        when(lang.msg(any(String.class), any(Object[].class))).thenAnswer(i -> i.getArgument(0));
        bean = new ExportTemplateEditorBean(null, null, resolver, tableService, null, reader, null, null, lang, labelBean, null);
        ExportTemplateEditModel model = new ExportTemplateEditModel();
        sheet = new EditSheet();
        sheet.setName("Feuille 1");
        model.getSheets().add(sheet);
        bean.setModel(model);
    }

    private static Concept concept(String thesaurus, String id, String uri) {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setExternalVocabularyId(thesaurus);
        Concept concept = new Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId(id);
        concept.setUri(uri);
        return concept;
    }

    private EditSource entitySource(String entity, String... typeKeys) {
        EditSource source = new EditSource();
        source.setKind(ExportTemplateEditModel.KIND_ENTITY);
        source.setEntity(entity);
        source.getTypeKeys().addAll(List.of(typeKeys));
        sheet.getSources().add(source);
        return source;
    }

    @Test
    void aTypeKeyWithoutUri_isAlignedOnTheKeyOfTheReferenceProject() {
        when(tableService.listConfiguredTypeConcepts(7L, ConfigurableTable.UE)).thenReturn(List.of(concept("th338", "1165", PARCEL_URI)));
        when(labelBean.findLabelOfConcept(any())).thenReturn("Parcelle");
        EditSource source = entitySource("RECORDING_UNIT", "th338|1165|");
        bean.setReferenceProjectId(7L);

        bean.alignTypeKeysWithProject();

        assertThat(source.getTypeKeys()).containsExactly("th338|1165|" + PARCEL_URI);
        assertThat(bean.typeItems(source)).extracting(ExportTemplateEditorBean.Item::label).containsExactly("Parcelle");
    }

    @Test
    void aTypeTheProjectDoesNotKnow_staysUnresolved() {
        when(tableService.listConfiguredTypeConcepts(7L, ConfigurableTable.UE)).thenReturn(List.of(concept("th338", "1165", PARCEL_URI)));
        when(labelBean.findLabelOfConcept(any())).thenReturn("Parcelle");
        EditSource source = entitySource("RECORDING_UNIT", "th338|999|");
        bean.setReferenceProjectId(7L);

        bean.alignTypeKeysWithProject();

        assertThat(source.getTypeKeys()).containsExactly("th338|999|");
        assertThat(bean.typeItems(source)).extracting(ExportTemplateEditorBean.Item::label).contains("exportTemplates.editor.unresolved");
    }

    @Test
    void withoutAReferenceProject_theTypeKeysAreLeftAlone() {
        EditSource source = entitySource("RECORDING_UNIT", "th338|1165|");

        bean.alignTypeKeysWithProject();

        assertThat(source.getTypeKeys()).containsExactly("th338|1165|");
    }

    @Test
    void aSourceWithoutTypesAndNonEntitySources_areSkippedByTheAlignment() {
        EditSource untyped = entitySource("RECORDING_UNIT");
        EditSource project = new EditSource();
        sheet.getSources().add(project);
        bean.setReferenceProjectId(7L);

        bean.alignTypeKeysWithProject();

        assertThat(untyped.getTypeKeys()).isEmpty();
        assertThat(project.getTypeKeys()).isEmpty();
    }

    @Test
    void placesExposeTheValuesOfTheirCategoryAsTypes() {
        ConceptDTO parcel = new ConceptDTO();
        parcel.setExternalId("1165");
        VocabularyDTO vocabulary = new VocabularyDTO();
        vocabulary.setExternalVocabularyId("th338");
        parcel.setVocabulary(vocabulary);
        ConceptPrefLabelDTO label = new ConceptPrefLabelDTO();
        label.setConcept(parcel);
        label.setLabel("Parcelle");
        ConceptAutocompleteDTO suggestion = new ConceptAutocompleteDTO(label, "Parcelle", List.of(), null, null);
        ConceptAutocompleteDTO withoutConcept = new ConceptAutocompleteDTO(new ConceptPrefLabelDTO(), "?", List.of(), null, null);
        when(tableService.measureConcepts(eq(7L), eq(SpatialUnit.TYPE_FIELD_CODE), any())).thenReturn(List.of(suggestion, suggestion, withoutConcept));
        EditSource source = entitySource("SPATIAL_UNIT");
        bean.setReferenceProjectId(7L);

        assertThat(bean.typeItems(source))
                .containsExactly(new ExportTemplateEditorBean.Item("Parcelle", "th338|1165|"));
    }

    @Test
    void aSubjectWithoutTypes_exposesNone() {
        EditSource source = new EditSource();
        source.setKind(ExportTemplateEditModel.KIND_ENTITY);
        source.setEntity("RECORDING_UNIT");
        bean.setReferenceProjectId(7L);
        when(tableService.listConfiguredTypeConcepts(7L, ConfigurableTable.UE)).thenReturn(List.of());

        assertThat(bean.typeItems(source)).isEmpty();
    }

    @Test
    void pickingATableChecksAllItsTypes_andTheSourceKeepsThemExplicitly() {
        when(tableService.listConfiguredTypeConcepts(7L, ConfigurableTable.UE)).thenReturn(List.of(
                concept("th338", "1199", "u1"), concept("th338", "1200", "u2")));
        when(labelBean.findLabelOfConcept(any())).thenReturn("T");
        bean.setReferenceProjectId(7L);

        bean.toggleTable(table("RECORDING_UNIT"));
        bean.confirmSourceDialog();

        assertThat(sheet.getSources()).hasSize(1);
        assertThat(sheet.getSources().get(0).getTypeKeys()).containsExactly("th338|1199|u1", "th338|1200|u2");
    }

    @Test
    void aTableWithoutAnyTypeInTheReferenceProject_cannotBePicked() {
        when(tableService.listConfiguredTypeConcepts(7L, ConfigurableTable.UE)).thenReturn(List.of());
        bean.setReferenceProjectId(7L);

        bean.toggleTable(table("RECORDING_UNIT"));

        assertThat(bean.getSourceDialog().getTable()).isNull();
        assertThat(bean.isSourceDialogConfirmable()).isFalse();
    }

    private ExportSourceDialog.Table table(String key) {
        return bean.getSourceTree().stream().filter(t -> t.getKey().equals(key)).findFirst().orElseThrow();
    }
}
