package fr.siamois.domain.models.exporttemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportTemplateJsonTest {

    private static final String CONCEPT = "{\"thesaurus\":\"th230\",\"id\":\"4290928\",\"uri\":\"https://thesaurus.mom.fr/?idt=th230&idc=4290928\"}";
    private static final String RU = "\"UE_1\":{\"kind\":\"ENTITY\",\"entity\":\"RECORDING_UNIT\"}";
    private static final String PROJECT_JOIN = "\"UE_1_project\":{\"join\":{\"topterm\":\"UE_1\",\"on\":{\"local_key\":\"project\",\"foreign_key\":\"id\"}}}";
    private static final String ROOT = "[{\"target_root\":\"UE\",\"source_topterm\":\"UE_1\"}]";
    private static final String CODE_COLUMN = "{\"UE.a\":{\"type\":\"text\"}}";
    private static final String CONSTANT_FIELD = "[{\"target\":\"UE.a\",\"source\":\"UE_1\",\"constant\":\"x\"}]";

    /** Gabarit v3 : seuls les blocs nécessaires au cas sont fournis. */
    private static String template(String sources, String roots, String schemaTarget, String fields) {
        return "{\"version\":3,\"id\":\"a\",\"templateVersion\":\"1.0.0\",\"name\":\"T\",\"sources\":{" + sources + "},"
                + "\"root_connections\":" + roots + ",\"schema_target\":" + schemaTarget + ",\"fields\":" + fields + "}";
    }

    private static String simple(String fields) {
        return template(RU, ROOT, CODE_COLUMN, fields);
    }

    @Test
    void parse_fullTemplate_readsEverything() {
        String json = "{\"version\":3,\"id\":\"uuid-1\",\"templateVersion\":\"1.2.0\",\"name\":\"Rapport\",\"fileNamePattern\":\"OA{oaCode}\","
                + "\"sources\":{\"UE_1\":{\"kind\":\"ENTITY\",\"entity\":\"RECORDING_UNIT\",\"types\":[" + CONCEPT + "]}," + PROJECT_JOIN + "},"
                + "\"root_connections\":" + ROOT + ","
                + "\"sheets\":{\"UE\":{\"omitIfEmpty\":true,\"sortBy\":[\"type_UE\"]}},"
                + "\"schema_target\":{\"UE.code_OA_NAT\":{\"type\":\"text\"},\"UE.type_UE\":{\"type\":\"text\"},\"UE.desc\":{}},"
                + "\"fields\":["
                + "{\"target\":\"UE.code_OA_NAT\",\"source\":\"UE_1_project\",\"field\":{\"concept\":" + CONCEPT + "}},"
                + "{\"target\":\"UE.type_UE\",\"source\":\"UE_1\",\"constant\":\"US\"},"
                + "{\"target\":\"UE.desc\",\"source\":\"UE_1\",\"concat\":{\"separator\":\" & \","
                + "\"parts\":[{\"label\":\"nature\",\"source\":\"UE_1\",\"field\":{\"concept\":" + CONCEPT + "}},{\"literal\":\"x\"}]}}]}";

        ExportTemplateDefinition d = ExportTemplateJson.parse(json);

        assertThat(d.id()).isEqualTo("uuid-1");
        assertThat(d.version()).isEqualTo("1.2.0");
        assertThat(d.fileNamePattern()).isEqualTo("OA{oaCode}");
        Sheet ue = d.sheets().get(0);
        assertThat(ue.omitIfEmpty()).isTrue();
        assertThat(ue.sortBy()).containsExactly("type_UE");
        assertThat(ue.sources().get(0)).isInstanceOfSatisfying(EntitySource.class, s -> {
            assertThat(s.entity()).isEqualTo(EntityKind.RECORDING_UNIT);
            assertThat(s.types()).containsExactly(new ConceptRef("th230", "4290928", "https://thesaurus.mom.fr/?idt=th230&idc=4290928"));
        });
        assertThat(ue.columns()).extracting(Column::header).containsExactly("code_OA_NAT", "type_UE", "desc");
        assertThat(ue.columns().get(0).rules().get(0)).isInstanceOfSatisfying(DirectRule.class, r -> {
            assertThat(r.path()).containsExactly("project");
            assertThat(r.sources()).containsExactly(0);
        });
        assertThat(ue.columns().get(2).rules().get(0)).isInstanceOfSatisfying(ConcatRule.class, r -> {
            assertThat(r.labelSeparator()).isEqualTo(": ");
            assertThat(r.parts()).hasSize(2);
        });
    }

    @Test
    void joinChain_becomesAPathFromThePrimarySource() {
        String sources = "\"SP_1\":{\"kind\":\"ENTITY\",\"entity\":\"SPECIMEN\"},"
                + "\"SP_1_recordingUnit\":{\"join\":{\"topterm\":\"SP_1\",\"on\":{\"local_key\":\"recordingUnit\",\"foreign_key\":\"id\"}}},"
                + "\"SP_1_recordingUnit_project\":{\"join\":{\"topterm\":\"SP_1_recordingUnit\",\"on\":{\"local_key\":\"project\",\"foreign_key\":\"id\"}}}";
        String json = template(sources, "[{\"target_root\":\"M\",\"source_topterm\":\"SP_1\"}]", "{\"M.oa\":{}}",
                "[{\"target\":\"M.oa\",\"source\":\"SP_1_recordingUnit_project\",\"field\":{\"concept\":" + CONCEPT + "}}]");

        DirectRule rule = (DirectRule) ExportTemplateJson.parse(json).sheets().get(0).columns().get(0).rules().get(0);

        assertThat(rule.path()).containsExactly("recordingUnit", "project");
    }

    @Test
    void sameRuleOnSeveralSources_isMergedBack() {
        String sources = RU + ",\"OA_1\":{\"kind\":\"PROJECT\"}";
        String json = template(sources,
                "[{\"target_root\":\"UE\",\"source_topterm\":\"UE_1\"},{\"target_root\":\"UE\",\"source_topterm\":\"OA_1\"}]", CODE_COLUMN,
                "[{\"target\":\"UE.a\",\"source\":\"UE_1\",\"constant\":\"x\"},{\"target\":\"UE.a\",\"source\":\"OA_1\",\"constant\":\"x\"}]");

        List<Rule> rules = ExportTemplateJson.parse(json).sheets().get(0).columns().get(0).rules();

        assertThat(rules).singleElement().isInstanceOfSatisfying(ConstantRule.class,
                r -> assertThat(r.sources()).containsExactly(0, 1));
    }

    @Test
    void shareQ3StatusMarker_isAcceptedAndIgnored() {
        ExportTemplateDefinition d = ExportTemplateJson.parse(simple(
                "[{\"target\":\"UE.a\",\"source\":\"UE_1\",\"constant\":\"x\",\"status\":\"validated\"}]"));

        assertThat(d.sheets().get(0).columns().get(0).rules()).hasSize(1);
    }

    @Test
    void commonCore_tableSourceAndNamedField_roundTripWithoutConcepts() {
        String json = template("\"T_1\":{\"kind\":\"TABLE\",\"name\":\"Mobilier\"}",
                "[{\"target_root\":\"M\",\"source_topterm\":\"T_1\"}]", "{\"M.num\":{}}",
                "[{\"target\":\"M.num\",\"source\":\"T_1\",\"field\":\"NumInventaire\"}]");

        ExportTemplateDefinition d = ExportTemplateJson.parse(json);

        assertThat(d.sheets().get(0).sources()).containsExactly(new TableSource("Mobilier"));
        assertThat(d.sheets().get(0).columns().get(0).rules().get(0))
                .isInstanceOfSatisfying(DirectRule.class, r -> assertThat(r.field()).isEqualTo(new NameField("NumInventaire")));
        assertThat(ExportTemplateJson.parse(ExportTemplateJson.toJson(d))).isEqualTo(d);
    }

    @Test
    void roundTrip_isStable() {
        ExportTemplateDefinition d = new ExportTemplateDefinition(3, "u", "1", "T", "f", List.of(
                new Sheet("UE", true,
                        List.of(new EntitySource(EntityKind.RECORDING_UNIT, List.of(new ConceptRef("th230", "1", null))),
                                new TechnicalSource("STRATIGRAPHIC_RELATIONSHIP")),
                        List.of("b"),
                        List.of(
                                new Column("a", OutputType.NUMBER, List.of(
                                        new DirectRule(List.of(0), new ConceptField(new ConceptRef("th230", "2", "u")), List.of("project", "mainLocation"),
                                                new ListOptions(" & ", true)),
                                        new DirectRule(List.of(1), new ColumnField("relationType"), List.of(), null))),
                                new Column("b", OutputType.DATE, List.of(
                                        new ConstantRule(List.of(0, 1), "v"))),
                                new Column("c", OutputType.TEXT, List.of(
                                        new ConcatRule(List.of(0), List.of(
                                                new ConcatPart("l", null, new ConceptField(new ConceptRef("th230", "3", null)), List.of("project")),
                                                new ConcatPart(null, "-", null, List.of())), "_", "="))))),
                new Sheet("OA", false, List.of(new ProjectSource()), List.of(),
                        List.of(new Column("x", OutputType.TEXT, List.of(new ConstantRule(List.of(0), "1")))))));

        assertThat(ExportTemplateJson.parse(ExportTemplateJson.toJson(d))).isEqualTo(d);
    }

    @Test
    void toWire_usesTheSharedLanguage() throws Exception {
        ExportTemplateDefinition d = new ExportTemplateDefinition(3, "u", "1", "T", null, List.of(
                new Sheet("UE", false, List.of(new EntitySource(EntityKind.RECORDING_UNIT, List.of())), List.of(),
                        List.of(new Column("a", OutputType.TEXT, List.of(
                                new DirectRule(List.of(), new ConceptField(new ConceptRef("th230", "2", null)), List.of("project"), null)))))));

        JsonNode wire = new ObjectMapper().readTree(ExportTemplateJson.toJson(d));

        assertThat(wire.get("version").asInt()).isEqualTo(3);
        assertThat(wire.get("root_connections")).hasSize(1);
        assertThat(wire.get("root_connections").get(0).get("target_root").asText()).isEqualTo("UE");
        assertThat(wire.get("schema_target").get("UE.a").get("type").asText()).isEqualTo("text");
        JsonNode join = wire.get("sources").get("UE_1_project").get("join");
        assertThat(join.get("topterm").asText()).isEqualTo("UE_1");
        assertThat(join.get("on").get("local_key").asText()).isEqualTo("project");
        assertThat(join.get("on").get("foreign_key").asText()).isEqualTo("id");
        assertThat(wire.get("fields").get(0).get("source").asText()).isEqualTo("UE_1_project");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTemplates")
    void parse_invalid_isRejected(String label, String json, String expectedMessage) {
        assertThatThrownBy(() -> ExportTemplateJson.parse(json))
                .isInstanceOf(InvalidExportTemplateException.class)
                .hasMessageContaining(expectedMessage);
    }

    static Stream<Arguments> invalidTemplates() {
        String ok = simple(CONSTANT_FIELD);
        return Stream.of(
                Arguments.of("malformed json", "{", "Malformed JSON"),
                Arguments.of("not an object", "[]", "Expected an object"),
                Arguments.of("old version", ok.replace("\"version\":3", "\"version\":1"), "Unsupported version"),
                Arguments.of("unknown root key", ok.replace("\"name\":\"T\"", "\"name\":\"T\",\"sql\":\"x\""), "Unknown property 'sql'"),
                Arguments.of("no root connection", template(RU, "[]", CODE_COLUMN, CONSTANT_FIELD), "'root_connections' cannot be empty"),
                Arguments.of("root on unknown source", template(RU, "[{\"target_root\":\"UE\",\"source_topterm\":\"ZZ\"}]", CODE_COLUMN, CONSTANT_FIELD), "Unknown source 'ZZ'"),
                Arguments.of("root on joined source", template(RU + "," + PROJECT_JOIN, "[{\"target_root\":\"UE\",\"source_topterm\":\"UE_1_project\"}]", CODE_COLUMN, CONSTANT_FIELD), "is joined"),
                Arguments.of("dot in sheet name", template(RU, "[{\"target_root\":\"U.E\",\"source_topterm\":\"UE_1\"}]", CODE_COLUMN, CONSTANT_FIELD), "cannot contain '.'"),
                Arguments.of("sheet without column", template(RU, ROOT, "{\"X.a\":{}}", "[]"), "Sheet 'UE' has no column"),
                Arguments.of("unknown source kind", simple(CONSTANT_FIELD).replace("\"kind\":\"ENTITY\"", "\"kind\":\"SQL\""), "Unknown source kind"),
                Arguments.of("unknown entity", simple(CONSTANT_FIELD).replace("RECORDING_UNIT", "NOPE"), "Unknown entity"),
                Arguments.of("join foreign key not id", template(RU + "," + PROJECT_JOIN.replace("\"foreign_key\":\"id\"", "\"foreign_key\":\"code\""), ROOT, CODE_COLUMN, CONSTANT_FIELD), "foreign_key must be 'id'"),
                Arguments.of("join from unknown source", template(RU + "," + PROJECT_JOIN.replace("\"topterm\":\"UE_1\"", "\"topterm\":\"ZZ\""), ROOT, CODE_COLUMN, CONSTANT_FIELD), "Join from unknown source"),
                Arguments.of("join cycle", template("\"A\":{\"join\":{\"topterm\":\"B\",\"on\":{\"local_key\":\"x\",\"foreign_key\":\"id\"}}},\"B\":{\"join\":{\"topterm\":\"A\",\"on\":{\"local_key\":\"y\",\"foreign_key\":\"id\"}}}", ROOT, CODE_COLUMN, CONSTANT_FIELD), "Join cycle"),
                Arguments.of("target not in schema", simple("[{\"target\":\"UE.zzz\",\"source\":\"UE_1\",\"constant\":\"x\"}]"), "not in schema_target"),
                Arguments.of("target without sheet", simple("[{\"target\":\"a\",\"source\":\"UE_1\",\"constant\":\"x\"}]"), "Invalid target"),
                Arguments.of("source not feeding the sheet", template(RU + ",\"OA_1\":{\"kind\":\"PROJECT\"}", ROOT, CODE_COLUMN,
                        "[{\"target\":\"UE.a\",\"source\":\"OA_1\",\"constant\":\"x\"}]"), "does not feed sheet"),
                Arguments.of("two fields on same source", simple("[{\"target\":\"UE.a\",\"source\":\"UE_1\",\"constant\":\"x\"},{\"target\":\"UE.a\",\"source\":\"UE_1\",\"constant\":\"y\"}]"), "Several fields target"),
                Arguments.of("entry without rule", simple("[{\"target\":\"UE.a\",\"source\":\"UE_1\"}]"), "exactly one of"),
                Arguments.of("entry with two rules", simple("[{\"target\":\"UE.a\",\"source\":\"UE_1\",\"constant\":\"x\",\"concat\":{\"parts\":[{\"literal\":\"y\"}]}}]"), "exactly one of"),
                Arguments.of("constant through a join", template(RU + "," + PROJECT_JOIN, ROOT, CODE_COLUMN,
                        "[{\"target\":\"UE.a\",\"source\":\"UE_1_project\",\"constant\":\"x\"}]"), "constant must use a primary source"),
                Arguments.of("sortBy unknown header", template(RU, ROOT, CODE_COLUMN, CONSTANT_FIELD).replace("\"schema_target\"", "\"sheets\":{\"UE\":{\"sortBy\":[\"zzz\"]}},\"schema_target\""), "unknown header"),
                Arguments.of("options for unknown sheet", template(RU, ROOT, CODE_COLUMN, CONSTANT_FIELD).replace("\"schema_target\"", "\"sheets\":{\"ZZ\":{}},\"schema_target\""), "unknown sheet 'ZZ'"),
                Arguments.of("field without concept or column", simple("[{\"target\":\"UE.a\",\"source\":\"UE_1\",\"field\":{}}]"), "needs 'concept' or 'column'"),
                Arguments.of("concat part with both", simple("[{\"target\":\"UE.a\",\"source\":\"UE_1\",\"concat\":{\"parts\":[{\"literal\":\"x\",\"source\":\"UE_1\",\"field\":{\"column\":\"c\"}}]}}]"), "exactly one of"),
                Arguments.of("concat part from another primary", template(RU + ",\"OA_1\":{\"kind\":\"PROJECT\"}",
                        "[{\"target_root\":\"UE\",\"source_topterm\":\"UE_1\"},{\"target_root\":\"UE\",\"source_topterm\":\"OA_1\"}]", CODE_COLUMN,
                        "[{\"target\":\"UE.a\",\"source\":\"UE_1\",\"concat\":{\"parts\":[{\"source\":\"OA_1\",\"field\":{\"column\":\"c\"}}]}}]"), "same primary source")
        );
    }

    @Test
    void sheetDeclaredOnlyInSchemaTarget_isASheetWithoutSource_andRoundTrips() {
        String json = template(RU, ROOT, "{\"UE.a\":{\"type\":\"text\"},\"Vide.h\":{\"type\":\"number\"}}", CONSTANT_FIELD);

        ExportTemplateDefinition parsed = ExportTemplateJson.parse(json);

        assertThat(parsed.sheets()).extracting(ExportTemplateDefinition.Sheet::name).containsExactly("UE", "Vide");
        assertThat(parsed.sheets().get(1).sources()).isEmpty();
        assertThat(ExportTemplateJson.parse(ExportTemplateJson.toJson(parsed))).isEqualTo(parsed);
    }

    @Test
    void fieldProperty_roundTrips_andUnknownPropertyIsRejected() {
        String ok = simple("[{\"target\":\"UE.a\",\"source\":\"UE_1\",\"field\":{\"concept\":" + CONCEPT + ",\"property\":\"unit\"}}]");

        ExportTemplateDefinition parsed = ExportTemplateJson.parse(ok);

        ExportTemplateDefinition.DirectRule rule = (ExportTemplateDefinition.DirectRule) parsed.sheets().get(0).columns().get(0).rules().get(0);
        assertThat(((ExportTemplateDefinition.ConceptField) rule.field()).property()).isEqualTo("unit");
        assertThat(ExportTemplateJson.parse(ExportTemplateJson.toJson(parsed))).isEqualTo(parsed);
        assertThatThrownBy(() -> ExportTemplateJson.parse(ok.replace("\"unit\"", "\"zzz\"")))
                .isInstanceOf(InvalidExportTemplateException.class).hasMessageContaining("Unknown field property");
    }
}
