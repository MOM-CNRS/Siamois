package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ColumnField;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConceptField;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConceptRef;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.exporttemplate.ExportValueReader.AnswerCache;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.form.CustomFieldAnswerService.ListOwner;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerTextViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportValueReaderTest {

    private static final ConceptField ANY_CONCEPT = new ConceptField(new ConceptRef("th", "1", null));

    @Mock private CustomFieldAnswerService answerService;

    private ExportValueReader reader;

    @BeforeEach
    void setUp() {
        reader = new ExportValueReader(answerService);
    }

    private static CustomField system(String binding) {
        return CustomFieldText.builder().id(-1L).label("l").isSystemField(true).valueBinding(binding).build();
    }

    private static CustomField additional(Long id) {
        return CustomFieldText.builder().id(id).label("a" + id).isSystemField(false).build();
    }

    private static CustomFieldAnswerViewModel answer(String text) {
        CustomFieldAnswerTextViewModel vm = new CustomFieldAnswerTextViewModel();
        vm.setValue(text);
        return vm;
    }

    @Test
    void read_systemField_readsTheBoundProperty() {
        ActionUnit project = new ActionUnit();
        project.setOaCode("062222");

        List<Object> values = reader.read(ExportRow.of(ExportSubject.PROJECT, project), ANY_CONCEPT,
                List.of(system("oaCode")), AnswerCache.EMPTY);

        assertThat(values).containsExactly("062222");
    }

    @Test
    void read_systemField_withAnUnreadableBinding_isEmpty() {
        List<Object> values = reader.read(ExportRow.of(ExportSubject.PROJECT, new ActionUnit()), ANY_CONCEPT,
                List.of(system("doesNotExist")), AnswerCache.EMPTY);

        assertThat(values).isEmpty();
    }

    @Test
    void read_systemField_withoutBinding_isEmpty() {
        List<Object> values = reader.read(ExportRow.of(ExportSubject.PROJECT, new ActionUnit()), ANY_CONCEPT,
                List.of(system(null)), AnswerCache.EMPTY);

        assertThat(values).isEmpty();
    }

    @Test
    void read_collectionValues_areFlattenedWithoutNulls() {
        ActionUnit project = new ActionUnit();
        project.setChildren(new java.util.LinkedHashSet<>(java.util.Arrays.asList(new ActionUnit(), null)));

        List<Object> values = reader.read(ExportRow.of(ExportSubject.PROJECT, project), ANY_CONCEPT,
                List.of(system("children")), AnswerCache.EMPTY);

        assertThat(values).hasSize(1);
    }

    @Test
    void read_columnField_readsTheValueOfATechnicalRow() {
        ExportRow row = ExportRow.technical(Map.of(), Map.of("uncertain", true));

        assertThat(reader.read(row, new ColumnField("uncertain"), List.of(), AnswerCache.EMPTY)).containsExactly(true);
        assertThat(reader.read(row, new ColumnField("missing"), List.of(), AnswerCache.EMPTY)).isEmpty();
    }

    @Test
    void read_conceptField_onATechnicalRow_isEmpty() {
        ExportRow row = ExportRow.technical(Map.of(), Map.of());

        assertThat(reader.read(row, ANY_CONCEPT, List.of(system("oaCode")), AnswerCache.EMPTY)).isEmpty();
    }

    @Test
    void read_additionalField_readsTheCachedAnswer_firstCandidateWithAValueWins() {
        RecordingUnit unit = new RecordingUnit();
        unit.setId(10L);
        CustomField forTypeA = additional(100L);
        CustomField forTypeB = additional(200L);
        AnswerCache cache = new AnswerCache(Map.of(10L, Map.of(forTypeB, answer("b-value"))));

        List<Object> values = reader.read(ExportRow.of(ExportSubject.RECORDING_UNIT, unit), ANY_CONCEPT,
                List.of(forTypeA, forTypeB), cache);

        assertThat(values).containsExactly("b-value");
    }

    @Test
    void read_additionalField_withoutAnAnswer_isEmpty() {
        RecordingUnit unit = new RecordingUnit();
        unit.setId(10L);

        assertThat(reader.read(ExportRow.of(ExportSubject.RECORDING_UNIT, unit), ANY_CONCEPT,
                List.of(additional(100L)), AnswerCache.EMPTY)).isEmpty();
    }

    @Test
    void prefetch_loadsOnlyAdditionalFields_inOneCall() {
        RecordingUnit u1 = new RecordingUnit();
        u1.setId(1L);
        RecordingUnit u2 = new RecordingUnit();
        u2.setId(2L);
        Map<Long, Map<CustomField, CustomFieldAnswerViewModel>> loaded = Map.of(1L, Map.of());
        when(answerService.loadAdditionalFieldAnswers(ListOwner.RECORDING_UNIT, List.of(1L, 2L), Set.of(100L)))
                .thenReturn(loaded);

        AnswerCache cache = reader.prefetch(ExportSubject.RECORDING_UNIT,
                List.of(ExportRow.of(ExportSubject.RECORDING_UNIT, u1), ExportRow.of(ExportSubject.RECORDING_UNIT, u2)),
                List.of(system("type"), additional(100L)));

        assertThat(cache.byOwnerId()).isSameAs(loaded);
    }

    @Test
    void prefetch_withOnlySystemFields_doesNotQuery() {
        AnswerCache cache = reader.prefetch(ExportSubject.RECORDING_UNIT,
                List.of(ExportRow.of(ExportSubject.RECORDING_UNIT, new RecordingUnit())), List.of(system("type")));

        assertThat(cache).isSameAs(AnswerCache.EMPTY);
        verifyNoInteractions(answerService);
    }

    @Test
    void prefetch_forProjects_isNotSupportedAndDoesNotQuery() {
        AnswerCache cache = reader.prefetch(ExportSubject.PROJECT,
                List.of(ExportRow.of(ExportSubject.PROJECT, new ActionUnit())), List.of(additional(100L)));

        assertThat(cache).isSameAs(AnswerCache.EMPTY);
        verify(answerService, never()).loadAdditionalFieldAnswers(any(ListOwner.class), anyCollection(), anyCollection());
    }
}
