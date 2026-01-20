package fr.siamois.infrastructure.dataimport;

import fr.siamois.infrastructure.database.initializer.seeder.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OOXMLImportServiceTest {

    private OOXMLImportService service;

    @BeforeEach
    void setUp() {
        service = new OOXMLImportService();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Workbook workbook() {
        return new XSSFWorkbook();
    }

    private Sheet sheet(Workbook wb, String name, String... headers) {
        Sheet sheet = wb.createSheet(name);
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        return sheet;
    }

    private void row(Sheet sheet, int rowIndex, Object... values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                row.createCell(i).setCellValue(values[i].toString());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Column normalization
    // -------------------------------------------------------------------------

    @Test
    void indexColumns_normalizesAccentsAndSpaces() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Test",
                "Nom",
                "Prénom",
                "Email Admins",
                "Unité Spatiale"
        );

        Map<String, Integer> cols = service.indexColumns(s.getRow(0));

        assertThat(cols)
                .containsEntry("nom", 0)
                .containsEntry("prenom", 1)
                .containsEntry("email admins", 2)
                .containsEntry("unite spatiale", 3);
    }

    // -------------------------------------------------------------------------
    // URI parsing
    // -------------------------------------------------------------------------

    @Test
    void extractIdtAndIdcFromUri() {
        String uri = "https://example.fr/th?idt=th230&idc=4287979";

        assertThat(service.extractIdtFromUri(uri)).contains("th230");
        assertThat(service.extractIdcFromUri(uri)).contains("4287979");
    }

    // -------------------------------------------------------------------------
    // Date parsing
    // -------------------------------------------------------------------------

    @Test
    void parseOffsetDateTime_fromIsoString() {
        Workbook wb = workbook();
        Sheet s = wb.createSheet();
        Row r = s.createRow(0);
        Cell c = r.createCell(0);
        c.setCellValue("2024-02-10");

        OffsetDateTime dt = service.parseOffsetDateTime(c);

        assertThat(dt).isEqualTo(
                OffsetDateTime.of(2024, 2, 10, 0, 0, 0, 0, ZoneOffset.UTC)
        );
    }

    // -------------------------------------------------------------------------
    // Persons
    // -------------------------------------------------------------------------

    @Test
    void parsePersons_basicCase() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Personne",
                "Email",
                "Nom",
                "Prenom",
                "Identifiant"
        );

        row(s, 1, "john@doe.fr", "Doe", "John", "jdoe");

        List<PersonSeeder.PersonSpec> persons = service.parsePersons(s);

        assertThat(persons).hasSize(1);

        PersonSeeder.PersonSpec p = persons.get(0);
        assertThat(p.email()).isEqualTo("john@doe.fr");
        assertThat(p.name()).isEqualTo("Doe");
        assertThat(p.lastname()).isEqualTo("John");
        assertThat(p.username()).isEqualTo("jdoe");
    }

    // -------------------------------------------------------------------------
    // Spatial units
    // -------------------------------------------------------------------------

    @Test
    void parseSpatialUnits_withChildren() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Unité spatiale",
                "Nom",
                "Uri type",
                "Createur",
                "Institution",
                "Enfants"
        );

        row(s, 1, "Parcelle A", "uri?idt=th1&idc=1", "a@b.fr", "INST", "US 1&&US 2");
        row(s, 2, "US 1", "uri?idt=th1&idc=2", "a@b.fr", "INST", null);
        row(s, 3, "US 2", "uri?idt=th1&idc=3", "a@b.fr", "INST", null);

        List<SpatialUnitSeeder.SpatialUnitSpecs> specs = service.parseSpatialUnits(s);

        SpatialUnitSeeder.SpatialUnitSpecs parent =
                specs.stream()
                        .filter(sp -> sp.name().equals("Parcelle A"))
                        .findFirst()
                        .orElseThrow();

        assertThat(parent.childrenKey())
                .extracting(SpatialUnitSeeder.SpatialUnitKey::unitName)
                .containsExactlyInAnyOrder("US 1", "US 2");
    }

    // -------------------------------------------------------------------------
    // Action codes
    // -------------------------------------------------------------------------

    @Test
    void parseActionCodes_basic() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Code",
                "Code",
                "Type uri"
        );

        row(s, 1, "FOU", "uri?idt=th9&idc=99");

        List<ActionCodeSeeder.ActionCodeSpec> specs =
                service.parseActionCodes(s);

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).code()).isEqualTo("FOU");
        assertThat(specs.get(0).typeConceptExternalId()).isEqualTo("99");
        assertThat(specs.get(0).typeVocabularyExternalId()).isEqualTo("th9");
    }

    // -------------------------------------------------------------------------
    // Recording unit relations
    // -------------------------------------------------------------------------

    @Test
    void parseRecordingUnitRelations_basic() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "UE_rel",
                "Parent",
                "Enfant"
        );

        row(s, 1, "100", "101");

        List<RecordingUnitRelSeeder.RecordingUnitDTO> rels =
                service.parseRecordingRels(s);

        assertThat(rels).hasSize(1);
        assertThat(rels.get(0).parent()).isEqualTo("100");
        assertThat(rels.get(0).child()).isEqualTo("101");
    }

    // -------------------------------------------------------------------------
    // importFromExcel (smoke test)
    // -------------------------------------------------------------------------

    @Test
    void importFromExcel_smokeTest() throws Exception {
        Workbook wb = workbook();

        sheet(wb, "Institution", "Nom");
        sheet(wb, "Personne", "Email");
        sheet(wb, "Unité spatiale", "Nom");
        sheet(wb, "Code", "Code", "Type uri");
        sheet(wb, "Unite action", "Nom");
        sheet(wb, "Prelev.", "Identifiant");
        sheet(wb, "UE_rel", "Parent", "Enfant");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);

        ImportSpecs specs = service.importFromExcel(
                new ByteArrayInputStream(out.toByteArray())
        );

        assertThat(specs).isNotNull();
    }
}
