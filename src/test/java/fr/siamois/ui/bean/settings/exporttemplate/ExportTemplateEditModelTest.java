package fr.siamois.ui.bean.settings.exporttemplate;

import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConceptRef;
import fr.siamois.domain.models.exporttemplate.ExportTemplateJson;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditModel.EditRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportTemplateEditModelTest {

    private static final String CONCEPT = "{\"thesaurus\":\"th230\",\"id\":\"4290928\",\"uri\":\"https://thesaurus.mom.fr/?idt=th230&idc=4290928\"}";

    private static final String FULL = "{\"schemaVersion\":1,\"id\":\"u\",\"version\":\"1.0.0\",\"name\":\"Rapport\",\"fileNamePattern\":\"OA{oaCode}\","
            + "\"sheets\":[{\"name\":\"UE\",\"omitIfEmpty\":true,\"sortBy\":[\"type\"],"
            + "\"sources\":[{\"kind\":\"ENTITY\",\"entity\":\"RECORDING_UNIT\",\"types\":[" + CONCEPT + "]},{\"kind\":\"PROJECT\"}],"
            + "\"columns\":["
            + "{\"header\":\"code\",\"output\":\"NUMBER\",\"rules\":[{\"type\":\"DIRECT\",\"sources\":[0],\"field\":{\"concept\":" + CONCEPT + "},\"path\":[\"project\",\"mainLocation\"],\"list\":{\"separator\":\" & \",\"sorted\":true}},"
            + "{\"type\":\"CONSTANT\",\"sources\":[1],\"value\":\"x\"}]},"
            + "{\"header\":\"type\",\"output\":\"TEXT\",\"rules\":[{\"type\":\"CONCAT\",\"separator\":\"_\",\"labelSeparator\":\"=\","
            + "\"parts\":[{\"literal\":\"US\"},{\"label\":\"n\",\"field\":{\"concept\":" + CONCEPT + "},\"path\":[\"project\"]}]}]},"
            + "{\"header\":\"rel\",\"rules\":[{\"type\":\"DIRECT\",\"field\":{\"column\":\"relationType\"}}]}"
            + "]}]}";

    @Test
    void roundTrip_isLossless() {
        ExportTemplateDefinition original = ExportTemplateJson.parse(FULL);

        ExportTemplateDefinition back = ExportTemplateEditModel.from(original).toDefinition();

        assertThat(back).isEqualTo(original);
    }

    @Test
    void from_exposesTextualFormValues() {
        ExportTemplateEditModel model = ExportTemplateEditModel.from(ExportTemplateJson.parse(FULL));

        EditRule direct = model.getSheets().get(0).getColumns().get(0).getRules().get(0);
        assertThat(direct.getPath()).isEqualTo("project,mainLocation");
        assertThat(direct.getField().getKey()).isEqualTo("th230|4290928|https://thesaurus.mom.fr/?idt=th230&idc=4290928");
        assertThat(direct.isList()).isTrue();
        assertThat(direct.isListSorted()).isTrue();
        assertThat(model.getSheets().get(0).getSources().get(0).getTypeKeys()).hasSize(1);
    }

    @Test
    void toDefinition_appliesFormEdits() {
        ExportTemplateEditModel model = ExportTemplateEditModel.from(ExportTemplateJson.parse(FULL));
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
}
