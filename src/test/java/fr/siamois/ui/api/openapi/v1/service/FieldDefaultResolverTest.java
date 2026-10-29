package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.config.ConceptState;
import fr.siamois.domain.models.form.config.FieldDefault;
import fr.siamois.domain.models.form.config.VocabularyMode;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.vocabulary.FieldVocabularyConfig;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldDefaultResolverTest {

    private static final Long PROJECT = 7L;
    private static final Long TYPE = 9L;

    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private FieldConfigurationService fieldConfigurationService;

    private FieldDefaultResolver resolver() {
        return new FieldDefaultResolver(conceptRepository, fieldConfigurationService);
    }

    @Test
    void aSingleChoiceKeepsItsConceptAsAnId_aMultipleOneAsAList() {
        CustomFieldSelectOneFromFieldCode one = one(1L);
        CustomFieldSelectMultipleFromFieldCode many = many(2L);
        when(conceptRepository.existsById(any())).thenReturn(true);
        FormUiDto form = formOf(col(one, "10"), col(many, "20", "21"));

        Map<String, Object> defaults = resolver().defaultsOf(form, PROJECT, TYPE, Set.of());

        assertThat(defaults).containsEntry("1", "10").containsEntry("2", List.of("20", "21"));
    }

    @Test
    void aFieldTheRequestAnswersKeepsItsOwnValue() {
        when(conceptRepository.existsById(any())).thenReturn(true);
        FormUiDto form = formOf(col(one(1L), "10"), col(one(2L), "11"));

        assertThat(resolver().defaultsOf(form, PROJECT, TYPE, Set.of("1"))).containsOnlyKeys("2");
    }

    @Test
    void aConceptThatNoLongerExistsIsDropped() {
        when(conceptRepository.existsById(10L)).thenReturn(false);
        when(conceptRepository.existsById(11L)).thenReturn(true);
        FormUiDto form = formOf(col(many(2L), "10", "11"));

        assertThat(resolver().defaultsOf(form, PROJECT, TYPE, Set.of())).containsEntry("2", List.of("11"));
    }

    @Test
    void aConceptTheListNoLongerOffersIsDropped_followedOrFrozen() {
        CustomFieldSelectOneFromFieldCode followed = one(1L);
        CustomFieldSelectOneFromFieldCode frozen = one(2L);
        when(conceptRepository.existsById(any())).thenReturn(true);
        lenient().when(fieldConfigurationService.describeVocabulary(
                org.mockito.ArgumentMatchers.same(followed), any(), any())).thenReturn(config(VocabularyMode.FOLLOW, Map.of(10L, ConceptState.DISABLED)));
        lenient().when(fieldConfigurationService.describeVocabulary(
                org.mockito.ArgumentMatchers.same(frozen), any(), any())).thenReturn(config(VocabularyMode.FROZEN, Map.of()));
        FormUiDto form = formOf(col(followed, "10"), col(frozen, "10"));

        assertThat(resolver().defaultsOf(form, PROJECT, TYPE, Set.of())).isEmpty();
    }

    @Test
    void aFrozenListOffersOnlyTheConceptsItEnabled() {
        CustomFieldSelectOneFromFieldCode frozen = one(2L);
        when(conceptRepository.existsById(any())).thenReturn(true);
        when(fieldConfigurationService.describeVocabulary(org.mockito.ArgumentMatchers.same(frozen), any(), any()))
                .thenReturn(config(VocabularyMode.FROZEN, Map.of(10L, ConceptState.ENABLED)));

        assertThat(resolver().defaultsOf(formOf(col(frozen, "10")), PROJECT, TYPE, Set.of())).containsEntry("2", "10");
    }

    @Test
    void aFieldWithoutDefaultOrOfAnotherKindGivesNothing() {
        CustomFieldText text = new CustomFieldText();
        text.setId(3L);
        CustomColUiDto bare = new CustomColUiDto();
        bare.setField(text);

        assertThat(resolver().defaultsOf(formOf(bare), PROJECT, TYPE, Set.of())).isEmpty();
        assertThat(resolver().defaultsOf(null, PROJECT, TYPE, Set.of())).isEmpty();
    }

    private static FieldVocabularyConfig config(VocabularyMode mode, Map<Long, ConceptState> states) {
        return new FieldVocabularyConfig(FieldVocabularyConfig.Kind.FIELD_CODE, "CODE", null, null, mode, states);
    }

    private static CustomFieldSelectOneFromFieldCode one(long id) {
        CustomFieldSelectOneFromFieldCode field = new CustomFieldSelectOneFromFieldCode();
        field.setId(id);
        return field;
    }

    private static CustomFieldSelectMultipleFromFieldCode many(long id) {
        CustomFieldSelectMultipleFromFieldCode field = new CustomFieldSelectMultipleFromFieldCode();
        field.setId(id);
        return field;
    }

    private static CustomColUiDto col(fr.siamois.domain.models.form.customfield.CustomField field, String... conceptIds) {
        CustomColUiDto col = new CustomColUiDto();
        col.setField(field);
        col.setDefaultValue(FieldDefault.ofConceptIds(List.of(conceptIds)));
        return col;
    }

    private static FormUiDto formOf(CustomColUiDto... columns) {
        CustomRowUiDto row = new CustomRowUiDto();
        row.setColumns(new java.util.ArrayList<>(List.of(columns)));
        CustomFormPanelUiDto panel = new CustomFormPanelUiDto();
        panel.setRows(List.of(row));
        FormUiDto form = new FormUiDto();
        form.setLayout(List.of(panel));
        return form;
    }
}
