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

/** Le modèle national des opérations sous-marines : celui du milieu terrestre, avec la feuille « situation » du milieu sous-marin. */
class UnderwaterTemplateResourceTest {

    private static ExportTemplateDefinition underwater;
    private static ExportTemplateDefinition national;

    private static ExportTemplateDefinition load(String name) throws IOException {
        try (InputStream in = UnderwaterTemplateResourceTest.class.getResourceAsStream("/export-templates/" + name)) {
            assertThat(in).isNotNull();
            return ExportTemplateJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @BeforeAll
    static void loadBoth() throws IOException {
        underwater = load("rapport-operation-national-sous-marin.json");
        national = load("rapport-operation-national.json");
    }

    private static Sheet sheet(ExportTemplateDefinition d, String name) {
        return d.sheets().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void passesTheSiamoisRegistries_andRoundTrips() {
        assertThat(ExportTemplateChecker.check(underwater)).isEmpty();
        assertThat(ExportTemplateJson.parse(ExportTemplateJson.toJson(underwater))).isEqualTo(underwater);
    }

    @Test
    void hasItsOwnIdentity() {
        assertThat(underwater.id()).isNotEqualTo(national.id());
        assertThat(underwater.name()).contains("sous-marin");
    }

    @Test
    void situationSheet_followsTheUnderwaterTableOfTheReferential() {
        assertThat(sheet(underwater, "situation").columns()).extracting(Column::header).containsExactly(
                "code_OA_NAT", "carte_SHOM_numero", "intitule_lieu_OA", "latitude_OA", "longitude_OA",
                "profondeur_OA", "nom_commune_OA", "numero_INSEE_commune_OA", "informations_complementaires_OA");
    }

    @Test
    void everyColumnIsMapped() {
        underwater.sheets().forEach(s -> s.columns()
                .forEach(c -> assertThat(c.rules()).as(s.name() + "." + c.header()).isNotEmpty()));
    }

    @Test
    void otherSheets_areThoseOfTheNationalTemplate() {
        List<String> others = national.sheets().stream().map(Sheet::name).filter(n -> !n.equals("situation")).toList();
        for (String name : others) {
            assertThat(sheet(underwater, name)).as(name).isEqualTo(sheet(national, name));
        }
    }
}
