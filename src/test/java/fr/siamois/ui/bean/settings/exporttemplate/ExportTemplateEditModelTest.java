package fr.siamois.ui.bean.settings.exporttemplate;

import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.*;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditModel.EditRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportTemplateEditModelTest {

    private static final ConceptRef C = new ConceptRef("th230", "4290928", "https://thesaurus.mom.fr/?idt=th230&idc=4290928");

    /** Feuille à deux sources ; la règle de « type » vaut pour toutes (liste de sources vide). */
    private static final ExportTemplateDefinition FULL = new ExportTemplateDefinition(3, "u", "1.0.0", "Rapport", "OA{oaCode}", List.of(
            new Sheet("UE", true,
                    List.of(new EntitySource(EntityKind.RECORDING_UNIT, List.of(C)), new ProjectSource()),
                    List.of("type"),
                    List.of(
                            new Column("code", OutputType.NUMBER, List.of(
                                    new DirectRule(List.of(0), new ConceptField(C), List.of("project", "mainLocation"), new ListOptions(" & ", true)),
                                    new ConstantRule(List.of(1), "x"))),
                            new Column("type", OutputType.TEXT, List.of(
                                    new ConcatRule(List.of(), List.of(
                                            new ConcatPart(null, "US", null, List.of()),
                                            new ConcatPart("n", null, new ConceptField(C), List.of("project"))), "_", "="))),
                            new Column("rel", OutputType.TEXT, List.of(
                                    new DirectRule(List.of(), new ColumnField("relationType"), List.of(), null)))))));

    @Test
    void roundTrip_isLossless() {
        ExportTemplateDefinition original = FULL;

        ExportTemplateDefinition back = ExportTemplateEditModel.from(original).toDefinition();

        assertThat(back).isEqualTo(original);
    }

    @Test
    void from_exposesTextualFormValues() {
        ExportTemplateEditModel model = ExportTemplateEditModel.from(FULL);

        EditRule direct = model.getSheets().get(0).getColumns().get(0).getRules().get(0);
        assertThat(direct.getPath()).isEqualTo("project,mainLocation");
        assertThat(direct.getField().getKey()).isEqualTo("th230|4290928|https://thesaurus.mom.fr/?idt=th230&idc=4290928");
        assertThat(direct.isList()).isTrue();
        assertThat(direct.isListSorted()).isTrue();
        assertThat(model.getSheets().get(0).getSources().get(0).getTypeKeys()).hasSize(1);
    }

    @Test
    void toDefinition_appliesFormEdits() {
        ExportTemplateEditModel model = ExportTemplateEditModel.from(FULL);
        model.setName("Renommé");
        model.setFileNamePattern("  ");
        EditRule direct = model.getSheets().get(0).getColumns().get(0).getRules().get(0);
        direct.setList(false);
        direct.setPath(" project , ,");

        ExportTemplateDefinition d = model.toDefinition();

        assertThat(d.name()).isEqualTo("Renommé");
        assertThat(d.fileNamePattern()).isNull();
        assertThat(d.sheets().get(0).columns().get(0).rules().get(0))
                .isInstanceOfSatisfying(ExportTemplateDefinition.DirectRule.class, r -> {
                    assertThat(r.list()).isNull();
                    assertThat(r.path()).containsExactly("project");
                });
    }

    @Test
    void conceptKey_roundTrips_withAndWithoutUri() {
        assertThat(ExportTemplateEditModel.refOf("th|1|")).isEqualTo(new ConceptRef("th", "1", null));
        assertThat(ExportTemplateEditModel.refOf("th|1")).isEqualTo(new ConceptRef("th", "1", null));
        ConceptRef withUri = new ConceptRef("th", "1", "http://x/?a=b|c");
        assertThat(ExportTemplateEditModel.refOf(ExportTemplateEditModel.keyOf(withUri))).isEqualTo(withUri);
    }

    @Test
    void invalidConceptKey_isRejected() {
        assertThatThrownBy(() -> ExportTemplateEditModel.refOf("only-one")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExportTemplateEditModel.refOf(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExportTemplateEditModel.refOf("|1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pathOf_splitsOnCommasAndIgnoresBlanks() {
        assertThat(ExportTemplateEditModel.pathOf(null)).isEmpty();
        assertThat(ExportTemplateEditModel.pathOf("")).isEmpty();
        assertThat(ExportTemplateEditModel.pathOf("a, b ,,c")).isEqualTo(List.of("a", "b", "c"));
    }

    @Test
    void commonCoreSourceAndNamedField_surviveTheFormRoundTrip() {
        ExportTemplateDefinition d = new ExportTemplateDefinition(3, "u", "1", "T", null, List.of(
                new Sheet("M", false, List.of(new TableSource("Mobilier")), List.of(),
                        List.of(new Column("num", OutputType.TEXT, List.of(
                                new DirectRule(List.of(0), new NameField("NumInventaire"), List.of(), null)))))));

        assertThat(ExportTemplateEditModel.from(d).toDefinition()).isEqualTo(d);
    }
}
