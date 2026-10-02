package fr.siamois.domain.models.exporttemplate;

import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportTemplateJsonTest {

    private static final String CONCEPT = "{\"thesaurus\":\"th230\",\"id\":\"4290928\",\"uri\":\"https://thesaurus.mom.fr/?idt=th230&idc=4290928\"}";

    private static String template(String sheetBody) {
        return "{\"schemaVersion\":1,\"id\":\"a\",\"version\":\"1.0.0\",\"name\":\"T\",\"sheets\":[" + sheetBody + "]}";
    }

    private static String sheet(String sources, String columns) {
        return "{\"name\":\"UE\",\"sources\":" + sources + ",\"columns\":" + columns + "}";
    }

    private static final String ONE_ENTITY_SOURCE = "[{\"kind\":\"ENTITY\",\"entity\":\"RECORDING_UNIT\"}]";
    private static final String TWO_SOURCES = "[{\"kind\":\"ENTITY\",\"entity\":\"SPECIMEN\"},{\"kind\":\"PROJECT\"}]";

    private static String column(String header, String rules) {
        return "{\"header\":\"" + header + "\",\"rules\":" + rules + "}";
    }

    @Test
    void parse_fullTemplate_readsEverything() {
        String json = "{\"schemaVersion\":1,\"id\":\"uuid-1\",\"version\":\"1.2.0\",\"name\":\"Rapport\","
                + "\"fileNamePattern\":\"OA{oaCode}\",\"sheets\":[{"
                + "\"name\":\"UE\",\"omitIfEmpty\":true,\"sortBy\":[\"type_UE\"],"
                + "\"sources\":[{\"kind\":\"ENTITY\",\"entity\":\"RECORDING_UNIT\",\"types\":[" + CONCEPT + "]}],"
                + "\"columns\":["
                + "{\"header\":\"code_OA_NAT\",\"rules\":[{\"type\":\"DIRECT\",\"field\":{\"concept\":" + CONCEPT + "},\"path\":[\"project\"]}]},"
                + "{\"header\":\"type_UE\",\"output\":\"TEXT\",\"rules\":[{\"type\":\"CONSTANT\",\"value\":\"US\"}]},"
                + "{\"header\":\"desc\",\"rules\":[{\"type\":\"CONCAT\",\"separator\":\" & \","
                + "\"parts\":[{\"label\":\"nature\",\"field\":{\"concept\":" + CONCEPT + "}},{\"literal\":\"x\"}]}]}"
                + "]}]}";

        ExportTemplateDefinition d = ExportTemplateJson.parse(json);

        assertThat(d.id()).isEqualTo("uuid-1");
        assertThat(d.fileNamePattern()).isEqualTo("OA{oaCode}");
        Sheet ue = d.sheets().get(0);
        assertThat(ue.omitIfEmpty()).isTrue();
        assertThat(ue.sortBy()).containsExactly("type_UE");
        assertThat(ue.sources().get(0)).isInstanceOfSatisfying(EntitySource.class, s -> {
            assertThat(s.entity()).isEqualTo(EntityKind.RECORDING_UNIT);
            assertThat(s.types()).containsExactly(new ConceptRef("th230", "4290928", "https://thesaurus.mom.fr/?idt=th230&idc=4290928"));
        });
        assertThat(ue.columns().get(0).rules().get(0)).isInstanceOfSatisfying(DirectRule.class, r -> {
            assertThat(r.path()).containsExactly("project");
            assertThat(r.sources()).isEmpty();
        });
        assertThat(ue.columns().get(1).output()).isEqualTo(OutputType.TEXT);
        assertThat(ue.columns().get(2).rules().get(0)).isInstanceOfSatisfying(ConcatRule.class, r -> {
            assertThat(r.labelSeparator()).isEqualTo(": ");
            assertThat(r.parts()).hasSize(2);
        });
    }

    @Test
    void roundTrip_isStable() {
        String json = template(sheet(TWO_SOURCES, "[" + column("a",
                "[{\"type\":\"DIRECT\",\"sources\":[0],\"field\":{\"column\":\"parent\"},\"list\":{\"separator\":\" & \",\"sorted\":true}},"
                        + "{\"type\":\"CONSTANT\",\"sources\":[1],\"value\":\"v\"}]") + "]"));

        ExportTemplateDefinition once = ExportTemplateJson.parse(json);
        ExportTemplateDefinition twice = ExportTemplateJson.parse(ExportTemplateJson.toJson(once));

        assertThat(twice).isEqualTo(once);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTemplates")
    void parse_invalid_isRejected(String label, String json, String expectedMessage) {
        assertThatThrownBy(() -> ExportTemplateJson.parse(json))
                .isInstanceOf(InvalidExportTemplateException.class)
                .hasMessageContaining(expectedMessage);
    }

    static Stream<Arguments> invalidTemplates() {
        String directRule = "[{\"type\":\"CONSTANT\",\"value\":\"x\"}]";
        return Stream.of(
                Arguments.of("malformed json", "{", "Malformed JSON"),
                Arguments.of("not an object", "[]", "Expected an object"),
                Arguments.of("wrong schemaVersion", template(sheet(ONE_ENTITY_SOURCE, "[" + column("a", directRule) + "]")).replace("\"schemaVersion\":1", "\"schemaVersion\":2"), "Unsupported schemaVersion"),
                Arguments.of("unknown root key", template(sheet(ONE_ENTITY_SOURCE, "[" + column("a", directRule) + "]")).replace("\"name\":\"T\"", "\"name\":\"T\",\"sql\":\"x\""), "Unknown property 'sql'"),
                Arguments.of("no sheets", template(""), "'sheets' cannot be empty"),
                Arguments.of("duplicate sheet", template(sheet(ONE_ENTITY_SOURCE, "[" + column("a", directRule) + "]") + "," + sheet(ONE_ENTITY_SOURCE, "[" + column("a", directRule) + "]")), "Duplicate sheet name"),
                Arguments.of("no source", template(sheet("[]", "[" + column("a", directRule) + "]")), "'sources' cannot be empty"),
                Arguments.of("unknown source kind", template(sheet("[{\"kind\":\"SQL\"}]", "[" + column("a", directRule) + "]")), "Unknown source kind"),
                Arguments.of("unknown entity", template(sheet("[{\"kind\":\"ENTITY\",\"entity\":\"NOPE\"}]", "[" + column("a", directRule) + "]")), "Unknown entity"),
                Arguments.of("duplicate header", template(sheet(ONE_ENTITY_SOURCE, "[" + column("a", directRule) + "," + column("a", directRule) + "]")), "Duplicate header"),
                Arguments.of("unknown rule type", template(sheet(ONE_ENTITY_SOURCE, "[" + column("a", "[{\"type\":\"SCRIPT\"}]") + "]")), "Unknown rule type"),
                Arguments.of("source index out of range", template(sheet(ONE_ENTITY_SOURCE, "[" + column("a", "[{\"type\":\"CONSTANT\",\"sources\":[3],\"value\":\"x\"}]") + "]")), "Invalid source index"),
                Arguments.of("source covered twice", template(sheet(ONE_ENTITY_SOURCE, "[" + column("a", "[{\"type\":\"CONSTANT\",\"value\":\"x\"},{\"type\":\"CONSTANT\",\"sources\":[0],\"value\":\"y\"}]") + "]")), "targeted by several rules"),
                Arguments.of("sortBy unknown header", template(sheet(ONE_ENTITY_SOURCE, "[" + column("a", directRule) + "]").replace("\"name\":\"UE\"", "\"name\":\"UE\",\"sortBy\":[\"zzz\"]")), "unknown header"),
                Arguments.of("field without concept or column", template(sheet(ONE_ENTITY_SOURCE, "[" + column("a", "[{\"type\":\"DIRECT\",\"field\":{}}]") + "]")), "needs 'concept' or 'column'"),
                Arguments.of("concat part with both", template(sheet(ONE_ENTITY_SOURCE, "[" + column("a", "[{\"type\":\"CONCAT\",\"parts\":[{\"literal\":\"x\",\"field\":{\"column\":\"c\"}}]}]") + "]")), "exactly one of"),
                Arguments.of("blank path entry", template(sheet(ONE_ENTITY_SOURCE, "[" + column("a", "[{\"type\":\"DIRECT\",\"field\":{\"column\":\"c\"},\"path\":[\" \"]}]") + "]")), "Blank path entry")
        );
    }
}
