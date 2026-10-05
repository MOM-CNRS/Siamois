package fr.siamois.domain.models.exporttemplate;

import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.Column;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.Sheet;
import fr.siamois.domain.services.exporttemplate.ExportTemplateChecker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Le modèle national livré avec l'application se lit, se ré-écrit à l'identique et passe les registres de Siamois. */
class NationalTemplateResourceTest {

    private static ExportTemplateDefinition definition;
    private static String json;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = NationalTemplateResourceTest.class.getResourceAsStream("/export-templates/rapport-operation-national.json")) {
            assertThat(in).isNotNull();
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        definition = ExportTemplateJson.parse(json);
    }

    private static List<String> headers(String sheet) {
        return definition.sheets().stream().filter(s -> s.name().equals(sheet)).findFirst().orElseThrow()
                .columns().stream().map(Column::header).toList();
    }

    @Test
    void usesTheCurrentGrammar_andPassesTheSiamoisRegistries() {
        assertThat(definition.schemaVersion()).isEqualTo(ExportTemplateDefinition.CURRENT_SCHEMA_VERSION);
        assertThat(ExportTemplateChecker.check(definition)).isEmpty();
    }

    @Test
    void roundTrip_isStable() {
        assertThat(ExportTemplateJson.parse(ExportTemplateJson.toJson(definition))).isEqualTo(definition);
    }

    @Test
    void sheets_followTheReferentialNames() {
        assertThat(definition.sheets()).extracting(Sheet::name)
                .containsExactly("OA", "situation", "UE", "relation", "mobilier", "VAB", "prelevement", "traitement", "documentation");
    }

    @Test
    void headers_followTheReferentialOrder() {
        assertThat(headers("relation")).containsExactly("code_OA_NAT", "code_UE1", "type_relation_UE", "code_UE2",
                "informations_complementaires_relation");
        assertThat(headers("UE")).startsWith("code_OA_NAT", "type_UE", "identifiant_UE").endsWith("informations_complementaires_UE");
        assertThat(headers("OA")).hasSize(12).first().isEqualTo("code_OA_NAT");
        assertThat(headers("mobilier")).hasSize(23);
        assertThat(headers("documentation")).hasSize(18);
        assertThat(headers("VAB")).hasSize(18).contains("identifiant_VAB");
        assertThat(headers("prelevement")).hasSize(13);
        assertThat(headers("traitement")).hasSize(9);
    }

    @Test
    void everySheetStartsWithTheOperationCode_andFilesAreNamedAfterIt() {
        definition.sheets().forEach(s -> assertThat(s.columns().get(0).header()).isEqualTo("code_OA_NAT"));
        assertThat(definition.fileNamePattern()).contains("{oaCode}").contains("{dateCompact}");
    }

    @Test
    void sheetsWithoutSiamoisEntity_haveNoSourceAndAreOmitted() {
        for (String name : List.of("VAB", "prelevement", "traitement")) {
            Sheet sheet = definition.sheets().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
            assertThat(sheet.sources()).isEmpty();
            assertThat(sheet.omitIfEmpty()).isTrue();
        }
    }

    @Test
    void columnsWithoutAnySiamoisField_areKeptUnmapped() {
        Column region = definition.sheets().get(0).columns().stream()
                .filter(c -> c.header().equals("region_facade_maritime_OA")).findFirst().orElseThrow();
        assertThat(region.rules()).isEmpty();
    }

    @Test
    void weightUnit_isTheUnitPropertyOfTheWeightField() {
        Column unit = definition.sheets().stream().filter(s -> s.name().equals("mobilier")).findFirst().orElseThrow()
                .columns().stream().filter(c -> c.header().equals("unite_poids_objet-lot")).findFirst().orElseThrow();
        ExportTemplateDefinition.DirectRule rule = (ExportTemplateDefinition.DirectRule) unit.rules().get(0);
        assertThat(((ExportTemplateDefinition.ConceptField) rule.field()).property()).isEqualTo("unit");
    }
}
