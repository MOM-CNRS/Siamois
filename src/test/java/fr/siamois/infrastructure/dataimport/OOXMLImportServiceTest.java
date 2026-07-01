package fr.siamois.infrastructure.dataimport;

import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.initializer.seeder.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void parseOffsetDateTime_nullCell() {
        OffsetDateTime dt = service.parseOffsetDateTime(null);
        assertThat(dt).isNull();
    }

    @Test
    void parseOffsetDateTime_emptyString() {
        Workbook wb = workbook();
        Sheet s = wb.createSheet();
        Row r = s.createRow(0);
        Cell c = r.createCell(0);
        c.setCellValue("   "); // espaces

        OffsetDateTime dt = service.parseOffsetDateTime(c);
        assertThat(dt).isNull();
    }

    @Test
    void parseOffsetDateTime_excelNumericDate() {
        Workbook wb = workbook();
        Sheet s = wb.createSheet();
        Row r = s.createRow(0);
        Cell c = r.createCell(0);

        c.setCellValue(Date.from(
                LocalDate.of(2024, Month.FEBRUARY, 10)
                        .atStartOfDay(ZoneOffset.UTC)  // <-- UTC
                        .toInstant()
        ));
        // Excel considère ce type comme NUMERIC
        CellStyle style = wb.createCellStyle();
        style.setDataFormat((short) 14); // format date
        c.setCellStyle(style);

        OffsetDateTime dt = service.parseOffsetDateTime(c);
        assertThat(dt).isEqualTo(
                LocalDate.of(2024, Month.FEBRUARY, 10).atStartOfDay().atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    void parseOffsetDateTime_invalidString() {
        Workbook wb = workbook();
        Sheet s = wb.createSheet();
        Row r = s.createRow(0);
        Cell c = r.createCell(0);
        c.setCellValue("not-a-date");

        OffsetDateTime dt = service.parseOffsetDateTime(c);
        assertThat(dt).isNull();
    }

    @Test
    void parseOffsetDateTime_stringWithSpaces() {
        Workbook wb = workbook();
        Sheet s = wb.createSheet();
        Row r = s.createRow(0);
        Cell c = r.createCell(0);
        c.setCellValue(" 2024-02-10 "); // espaces avant/après

        OffsetDateTime dt = service.parseOffsetDateTime(c);
        assertThat(dt).isEqualTo(
                OffsetDateTime.of(2024, 2, 10, 0, 0, 0, 0, ZoneOffset.UTC)
        );
    }

    @Test
    void parseOffsetDateTime_numericNotDate() {
        Workbook wb = workbook();
        Sheet s = wb.createSheet();
        Row r = s.createRow(0);
        Cell c = r.createCell(0);
        c.setCellValue(123.45); // juste un nombre

        OffsetDateTime dt = service.parseOffsetDateTime(c);
        assertThat(dt).isNull();
    }







    @Test
    void parsePersons_basic() {
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

        List<SpatialUnitSeeder.SpatialUnitSpecs> specs = service.parseSpatialUnits(s, null);

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

        List<RecordingUnitRelSeeder.RecordingUnitRelDTO> rels =
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


        Sheet meta = sheet(wb, "sheet_metadata",
                "sheet_id",
                "sheet_name"
        );
        row(meta, 1, "person", "Personne");
        row(meta, 2, "institution", "Institution");
        row(meta, 3, "spatial_unit", "Unité spatiale");
        row(meta, 4, "code", "Code");
        row(meta, 5, "action_unit", "Unite action");
        row(meta, 6, "uniteAction", "Nom");
        row(meta, 7, "recording_unit", "UE");
        row(meta, 8, "specimen", "Prelev");


        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);

        ImportSpecs specs = service.importFromExcel(
                new ByteArrayInputStream(out.toByteArray()), OOXMLImportService.ImportScope.ALL, null
        );

        assertThat(specs).isNotNull();
    }

    @Test
    void importFromExcel_noMetaSheet() throws Exception {
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
                new ByteArrayInputStream(out.toByteArray()), OOXMLImportService.ImportScope.ALL, null
        );

        assertThat(specs).isNotNull();
    }

    @Test
    void parseRecordingUnits_fullValidRow() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("Unite action");

        // Header
        Row header = s.createRow(0);
        header.createCell(0).setCellValue("Identifiant");
        header.createCell(1).setCellValue("Description");
        header.createCell(2).setCellValue("Type uri");
        header.createCell(3).setCellValue("Cycle uri");
        header.createCell(4).setCellValue("Couleur de la matrice");
        header.createCell(5).setCellValue("Texture de la matrice");
        header.createCell(6).setCellValue("Composition de la matrice");
        header.createCell(7).setCellValue("Agent uri");
        header.createCell(8).setCellValue("Interpretation uri");
        header.createCell(9).setCellValue("Author email");
        header.createCell(10).setCellValue("Institution");
        header.createCell(11).setCellValue("Contributeurs email");
        header.createCell(12).setCellValue("Date d'ouverture");
        header.createCell(13).setCellValue("Date de fermeture");
        header.createCell(14).setCellValue("Unite spatiale");
        header.createCell(15).setCellValue("Unite d'action");

        // Data row
        Row row = s.createRow(1);
        row.createCell(0).setCellValue("123");
        row.createCell(1).setCellValue("US stratigraphique");
        row.createCell(2).setCellValue("uri?idt=th1&idc=10");
        row.createCell(3).setCellValue("uri?idt=th2&idc=20");
        row.createCell(4).setCellValue("Brun");
        row.createCell(5).setCellValue("Sableux");
        row.createCell(6).setCellValue("Argile");
        row.createCell(7).setCellValue("uri?idt=th3&idc=30");
        row.createCell(8).setCellValue("uri?idt=th4&idc=40");
        row.createCell(9).setCellValue("author@site.fr");
        row.createCell(10).setCellValue("INST");
        row.createCell(11).setCellValue("a@b.fr; c@d.fr");
        row.createCell(12).setCellValue("2023-01-10");
        row.createCell(13).setCellValue("2023-02-20");
        row.createCell(14).setCellValue("US-01");
        row.createCell(15).setCellValue("UA-99");

        // Execute
        List<RecordingUnitSeeder.RecordingUnitSpecs> specs =
                service.parseRecordingUnits(s, OOXMLImportService.ImportScope.ALL, null);

        // Assert
        assertThat(specs).hasSize(1);

        RecordingUnitSeeder.RecordingUnitSpecs ru = specs.get(0);

        // Identifiers
        assertThat(ru.fullIdentifier()).isEqualTo("123");
        assertThat(ru.identifier()).isEqualTo(123);

        // Concepts
        assertThat(ru.type()).isEqualTo(new ConceptSeeder.ConceptKey("th1", "10"));
        assertThat(ru.geomorphologicalCycle()).isEqualTo(new ConceptSeeder.ConceptKey("th2", "20"));
        assertThat(ru.geomorphologicalAgent()).isEqualTo(new ConceptSeeder.ConceptKey("th3", "30"));
        assertThat(ru.interpretation()).isEqualTo(new ConceptSeeder.ConceptKey("th4", "40"));

        // Metadata
        assertThat(ru.authorEmail()).isEqualTo("author@site.fr");
        assertThat(ru.institutionIdentifier()).isEqualTo("INST");
        assertThat(ru.excavators()).containsExactly("a@b.fr", "c@d.fr");

        // Dates
        assertThat(ru.beginDate()).isEqualTo(
                OffsetDateTime.of(2023, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(ru.endDate()).isEqualTo(
                OffsetDateTime.of(2023, 2, 20, 0, 0, 0, 0, ZoneOffset.UTC)
        );

        // Spatial / action unit
        assertThat(ru.spatialUnitName().unitName()).isEqualTo("US-01");
        assertThat(ru.actionUnitIdentifier().fullIdentifier()).isEqualTo("UA-99");

        // Matrix
        assertThat(ru.matrixColor()).isEqualTo("Brun");
        assertThat(ru.matrixComposition()).isEqualTo("Argile");
        assertThat(ru.matrixTexture()).isEqualTo("Sableux");

        // System fields
        assertThat(ru.createdBy()).isEqualTo(OOXMLImportService.SIAMOIS_SYSTEM);
        assertThat(ru.creationTime()).isNotNull();
    }


    @Test
    void parseActionUnits_fullValidRow() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("Unite action");

        // Header
        Row header = s.createRow(0);
        header.createCell(0).setCellValue("Nom");
        header.createCell(1).setCellValue("Identifiant");
        header.createCell(2).setCellValue("Code");
        header.createCell(3).setCellValue("Type uri");
        header.createCell(4).setCellValue("Createur");
        header.createCell(5).setCellValue("Institution");
        header.createCell(6).setCellValue("Date debut");
        header.createCell(7).setCellValue("Date fin");
        header.createCell(8).setCellValue("Contexte spatiale");

        // Data row
        Row row = s.createRow(1);
        row.createCell(0).setCellValue("Décapage zone nord");
        row.createCell(1).setCellValue("UA-001");
        row.createCell(2).setCellValue("FOU");
        row.createCell(3).setCellValue("uri?idt=th9&idc=99");
        row.createCell(4).setCellValue("user@site.fr");
        row.createCell(5).setCellValue("INST");
        row.createCell(6).setCellValue("2023-03-01");
        row.createCell(7).setCellValue("2023-03-10");
        row.createCell(8).setCellValue("US-01&&US-02");

        // Execute
        List<ActionUnitSeeder.ActionUnitSpecs> specs =
                service.parseActionUnits(s);

        // Assert
        assertThat(specs).hasSize(1);

        ActionUnitSeeder.ActionUnitSpecs au = specs.get(0);

        // Identifier logic
        assertThat(au.fullIdentifier()).isEqualTo("UA-001");
        assertThat(au.identifier()).isEqualTo("UA-001");
        assertThat(au.name()).isEqualTo("Décapage zone nord");

        // Code
        assertThat(au.primaryActionCode()).isEqualTo("FOU");

        // Type (URI)
        assertThat(au.typeVocabularyExtId()).isEqualTo("th9");
        assertThat(au.typeConceptExtId()).isEqualTo("99");

        // Creator / institution
        assertThat(au.authorEmail()).isEqualTo("user@site.fr");
        assertThat(au.institutionIdentifier()).isEqualTo("INST");

        // Dates
        assertThat(au.beginDate()).isEqualTo(
                OffsetDateTime.of(2023, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(au.endDate()).isEqualTo(
                OffsetDateTime.of(2023, 3, 10, 0, 0, 0, 0, ZoneOffset.UTC)
        );

        // Spatial context
        assertThat(au.spatialContextKeys())
                .extracting(SpatialUnitSeeder.SpatialUnitKey::unitName)
                .containsExactly("US-01", "US-02");
    }

    @Test
    void parseInstitutions_fullValidRow() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("Institution");

        // Header
        Row header = s.createRow(0);
        header.createCell(0).setCellValue("Nom");
        header.createCell(1).setCellValue("Description");
        header.createCell(2).setCellValue("Identifiant");
        header.createCell(3).setCellValue("Email Admins");
        header.createCell(4).setCellValue("Thesaurus");

        // Data row
        Row row = s.createRow(1);
        row.createCell(0).setCellValue("INRAP");
        row.createCell(1).setCellValue("Institut national");
        row.createCell(2).setCellValue("INRAP-ID");
        row.createCell(3).setCellValue("a@inrap.fr; b@inrap.fr");
        row.createCell(4).setCellValue("https://thesaurus.fr/api?idt=th230&idc=999");

        // Execute
        List<InstitutionSeeder.InstitutionSpec> specs =
                service.parseInstitutions(s);

        // Assert
        assertThat(specs).hasSize(1);

        InstitutionSeeder.InstitutionSpec inst = specs.get(0);

        assertThat(inst.name()).isEqualTo("INRAP");
        assertThat(inst.description()).isEqualTo("Institut national");
        assertThat(inst.identifier()).isEqualTo("INRAP-ID");

        // Email admins
        assertThat(inst.managerEmails())
                .containsExactly("a@inrap.fr", "b@inrap.fr");

        // Thesaurus
        assertThat(inst.externalId()).isEqualTo("th230");

    }

    @Test
    void parseSpecimens_fullValidRow() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("Specimen");

        // Header
        Row header = s.createRow(0);

        header.createCell(0).setCellValue("Identifiant");
        header.createCell(1).setCellValue("Catégorie");
        header.createCell(2).setCellValue("Matière");
        header.createCell(3).setCellValue("Designatation");
        header.createCell(4).setCellValue("Institution");
        header.createCell(5).setCellValue("Auteur fiche email");
        header.createCell(6).setCellValue("Collecteur emails");
        header.createCell(7).setCellValue("Unité d'enregistrement");


        // Data row
        Row row = s.createRow(1);

        row.createCell(0).setCellValue("SP-001");
        row.createCell(1).setCellValue("https://thesaurus.fr/api?idt=th12&idc=88");
        row.createCell(2).setCellValue("https://thesaurus.fr/api?idt=th12&idc=89");
        row.createCell(3).setCellValue("https://thesaurus.fr/api?idt=th12&idc=90");
        row.createCell(4).setCellValue("INRAP");
        row.createCell(5).setCellValue("user@site.fr");
        row.createCell(6).setCellValue("user@site.fr");
        row.createCell(7).setCellValue("US-001");

        // Execute
        List<SpecimenSeeder.SpecimenSpecs> specs =
                service.parseSpecimens(s, null);

        // Assert
        assertThat(specs).hasSize(1);

        SpecimenSeeder.SpecimenSpecs sp = specs.get(0);

        assertThat(sp.fullIdentifier()).isEqualTo("SP-001");

        // Type (thesaurus)
        assertThat(sp.type().vocabularyExtId()).isEqualTo("th12");
        assertThat(sp.type().conceptExtId()).isEqualTo("89");

        // Relations
        assertThat(sp.institutionIdentifier()).isEqualTo("INRAP");

        assertThat(sp.recordingUnitKey().fullIdentifier()).isEqualTo("US-001");


    }

    @Test
    void extractThesaurusDomain_nullInput_returnsNull() {
        // when
        String result = service.extractThesaurusDomain(null);

        // then
        assertThat(result).isNull();
    }

    @ParameterizedTest(name = "{index} => input={0}, expected={1}")
    @CsvSource({
            "'https://thesaurus.fr/api', 'https://thesaurus.fr/api'",
            "'https://thesaurus.fr/api?idt=th230&idc=999', 'https://thesaurus.fr/ap'",
            "'a?idt=1', ''"
    })
    void extractThesaurusDomain_parametrized(String input, String expected) {
        String result = service.extractThesaurusDomain(input);
        assertThat(result).isEqualTo(expected);
    }


    // -------------------------------------------------------------------------
    // parseStratiRels
    // -------------------------------------------------------------------------

    @Test
    void parseStratiRels_nullSheet_returnsEmpty() {
        assertThat(service.parseStratiRels(null)).isEmpty();
    }

    @Test
    void parseStratiRels_noHeaderRow_returnsEmpty() {
        Workbook wb = workbook();
        Sheet s = wb.createSheet("Strati");
        // no row created at all
        assertThat(service.parseStratiRels(s)).isEmpty();
    }

    @Test
    void parseStratiRels_missingUs1Column_returnsEmpty() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Strati", "us2", "relation");
        row(s, 1, "US-002", "uri?idt=th1&idc=1");
        assertThat(service.parseStratiRels(s)).isEmpty();
    }

    @Test
    void parseStratiRels_missingUs2Column_returnsEmpty() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Strati", "us1", "relation");
        row(s, 1, "US-001", "uri?idt=th1&idc=1");
        assertThat(service.parseStratiRels(s)).isEmpty();
    }

    @Test
    void parseStratiRels_fullValidRow_parsesAllFields() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Strati",
                "us1", "us2", "relation", "direction vocabulaire", "asynchrone", "incertain");
        row(s, 1, "US-001", "US-002", "uri?idt=th240&idc=4287979", "True", "True", "False");

        List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> specs = service.parseStratiRels(s);

        assertThat(specs).hasSize(1);
        RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO dto = specs.get(0);
        assertThat(dto.us1()).isEqualTo("US-001");
        assertThat(dto.us2()).isEqualTo("US-002");
        assertThat(dto.rel()).isEqualTo(new ConceptSeeder.ConceptKey("th240", "4287979"));
        assertThat(dto.conceptDirection()).isTrue();
        assertThat(dto.isAsynchronous()).isTrue();
        assertThat(dto.isUncertain()).isFalse();
    }

    @Test
    void parseStratiRels_booleanNotTrue_isFalse() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Strati",
                "us1", "us2", "relation", "direction vocabulaire", "asynchrone", "incertain");
        row(s, 1, "US-001", "US-002", "uri?idt=th240&idc=1", "False", "false", "0");

        RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO dto = service.parseStratiRels(s).get(0);
        assertThat(dto.conceptDirection()).isFalse();
        assertThat(dto.isAsynchronous()).isFalse();
        assertThat(dto.isUncertain()).isFalse();
    }

    @Test
    void parseStratiRels_missingOptionalColumns_defaultsToFalseAndNullRelKey() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Strati", "us1", "us2");
        row(s, 1, "A-001", "B-002");

        RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO dto = service.parseStratiRels(s).get(0);
        assertThat(dto.rel()).isNull();
        assertThat(dto.conceptDirection()).isFalse();
        assertThat(dto.isAsynchronous()).isFalse();
        assertThat(dto.isUncertain()).isFalse();
    }

    @Test
    void parseStratiRels_blankUs1_rowIsSkipped() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Strati", "us1", "us2");
        row(s, 1, "   ", "US-002");

        assertThat(service.parseStratiRels(s)).isEmpty();
    }

    @Test
    void parseStratiRels_blankUs2_rowIsSkipped() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Strati", "us1", "us2");
        row(s, 1, "US-001", "");

        assertThat(service.parseStratiRels(s)).isEmpty();
    }

    @Test
    void parseStratiRels_multipleRows_allParsed() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Strati", "us1", "us2");
        row(s, 1, "A", "B");
        row(s, 2, "C", "D");

        assertThat(service.parseStratiRels(s)).hasSize(2);
    }

    @Test
    void parseStratiRels_rowsWithMixedBlankUs1_onlyValidRowsIncluded() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Strati", "us1", "us2");
        row(s, 1, "A", "B");
        row(s, 2, "", "D");      // blank us1 → skipped
        row(s, 3, "E", "F");

        assertThat(service.parseStratiRels(s)).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // parsePhases
    // -------------------------------------------------------------------------

    @Test
    void parsePhases_nullSheet_returnsEmpty() {
        assertThat(service.parsePhases(null, null)).isEmpty();
    }

    @Test
    void parsePhases_noHeaderRow_returnsEmpty() {
        Workbook wb = workbook();
        Sheet s = wb.createSheet("Phase");
        assertThat(service.parsePhases(s, null)).isEmpty();
    }

    @Test
    void parsePhases_blankIdentifier_rowIsSkipped() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Phase", "Identifiant", "Titre");
        row(s, 1, "   ", "titre quelconque");

        assertThat(service.parsePhases(s,  null)).isEmpty();
    }

    @Test
    void parsePhases_fullValidRow_withoutActionUnit_parsesAllFields() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Phase",
                "Identifiant", "Titre", "Type uri", "Description",
                "Ordre", "Borne inferieure", "Borne superieure",
                "Auteur", "Projet", "Institution");
        row(s, 1,
                "PH-01", "Phase 1", "uri?idt=th240&idc=100", "Une description",
                "2", "1000", "2000",
                "author@site.fr", "UA-001", "INST");

        List<PhaseSeeder.PhaseSpecs> specs =
                service.parsePhases(s,  null);

        assertThat(specs).hasSize(1);
        PhaseSeeder.PhaseSpecs ph = specs.get(0);
        assertThat(ph.identifier()).isEqualTo("PH-01");
        assertThat(ph.title()).isEqualTo("Phase 1");
        assertThat(ph.type()).isEqualTo(new ConceptSeeder.ConceptKey("th240", "100"));
        assertThat(ph.description()).isEqualTo("Une description");
        assertThat(ph.orderNumber()).isEqualTo(2);
        assertThat(ph.lowerBound()).isEqualTo(1000);
        assertThat(ph.upperBound()).isEqualTo(2000);
        assertThat(ph.authorEmail()).isEqualTo("author@site.fr");
        assertThat(ph.actionUnitKey().fullIdentifier()).isEqualTo("UA-001");
        assertThat(ph.actionUnitKey().institutionIdentifier()).isEqualTo("INST");
    }

    @Test
    void parsePhases_withActionUnit_usesActionUnitIdentifiersInsteadOfSheetColumns() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setIdentifier("CODE-INST");

        ActionUnitDTO au = new ActionUnitDTO();
        au.setFullIdentifier("AU-FULL-ID");
        au.setCreatedByInstitution(inst);

        Workbook wb = workbook();
        Sheet s = sheet(wb, "Phase", "Identifiant", "Titre", "Auteur");
        row(s, 1, "PH-02", "Phase 2", "user@site.fr");

        PhaseSeeder.PhaseSpecs ph =
                service.parsePhases(s,  au).get(0);

        assertThat(ph.actionUnitKey().fullIdentifier()).isEqualTo("AU-FULL-ID");
        assertThat(ph.actionUnitKey().institutionIdentifier()).isEqualTo("CODE-INST");
    }

    @Test
    void parsePhases_missingOptionalColumns_nullsForMissingFields() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Phase", "Identifiant");
        row(s, 1, "PH-03");

        PhaseSeeder.PhaseSpecs ph =
                service.parsePhases(s, null).get(0);

        assertThat(ph.identifier()).isEqualTo("PH-03");
        assertThat(ph.title()).isNull();
        assertThat(ph.type()).isNull();
        assertThat(ph.description()).isNull();
        assertThat(ph.orderNumber()).isNull();
        assertThat(ph.lowerBound()).isNull();
        assertThat(ph.upperBound()).isNull();
        assertThat(ph.authorEmail()).isNull();
    }

    @Test
    void parsePhases_multipleRows_allParsed() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Phase", "Identifiant", "Titre");
        row(s, 1, "PH-01", "Phase A");
        row(s, 2, "PH-02", "Phase B");
        row(s, 3, "PH-03", "Phase C");

        assertThat(service.parsePhases(s,  null)).hasSize(3);
    }

    @Test
    void parsePhases_mixedBlankIdentifiers_onlyNonBlankIncluded() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Phase", "Identifiant", "Titre");
        row(s, 1, "PH-01", "Phase A");
        row(s, 2, "", "Phase B");    // blank identifier → skipped
        row(s, 3, "PH-03", "Phase C");

        assertThat(service.parsePhases(s, null)).hasSize(2);
    }

    @Test
    void extractThesaurusDomain_questionMarkAtStart_returnsEmptyString() {
        // given
        String thesaurus = "?idt=th230";

        // when
        String result = service.extractThesaurusDomain(thesaurus);

        // then
        assertThat(result).isNull();
    }

    // -------------------------------------------------------------------------
    // Catch-block coverage: every parse* method must wrap unexpected exceptions
    // in IllegalStateException("[Feuille '<name>'] : ...").
    //
    // Strategy:
    //  - URI-parsing methods (extractIdtFromUri / extractIdcFromUri) throw when
    //    the URI does not contain idt= or idc=.  Placing "bad-uri" in the type
    //    column of a data row reliably reaches the outer catch.
    //  - For parsePersons / parseRecordingRels that contain no URI calls,
    //    a FORMULA cell (=1+1) in the key column triggers
    //    "Cannot get a STRING value from a NUMERIC formula cell" inside
    //    getStringCell(), which then propagates through forEachDataRow and is
    //    wrapped by the outer catch.
    // -------------------------------------------------------------------------

    private static final String BAD_URI = "not-a-valid-uri";

    /** Creates a formula cell (=1+1) whose getStringCellValue() throws. */
    private void formulaCell(Row r, int col) {
        r.createCell(col).setCellFormula("1+1");
    }

    @Test
    void parseInstitutions_rowThrows_wrappedWithSheetName() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Institution", "Nom", "Description", "Identifiant", "Email Admins", "Thesaurus");
        row(s, 1, "INRAP", "Desc", "ID", "a@b.fr", BAD_URI);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.parseInstitutions(s));

        assertThat(ex.getMessage()).contains("[Feuille 'Institution']");
    }

    @Test
    void parsePersons_rowThrows_wrappedWithSheetName() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Personne", "Email", "Nom", "Prenom", "Identifiant");
        Row r = s.createRow(1);
        formulaCell(r, 0); // Email column – formula cell triggers ISE in getStringCell

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.parsePersons(s));

        assertThat(ex.getMessage()).contains("[Feuille 'Personne']");
    }

    @Test
    void parseSpatialUnits_rowThrows_wrappedWithSheetName() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Lieu", "Nom", "Uri type", "Createur", "Institution", "Enfants");
        row(s, 1, "Site Nord", BAD_URI, "sys", "INST", null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.parseSpatialUnits(s,  null));

        assertThat(ex.getMessage()).contains("[Feuille 'Lieu']");
    }

    @Test
    void parseRecordingRels_rowThrows_wrappedWithSheetName() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Relations", "Parent", "Enfant");
        Row r = s.createRow(1);
        formulaCell(r, 0); // Parent column
        r.createCell(1).setCellValue("child");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.parseRecordingRels(s));

        assertThat(ex.getMessage()).contains("[Feuille 'Relations']");
    }

    @Test
    void parseStratiRels_rowThrows_wrappedWithSheetName() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Strati", "us1", "us2", "relation");
        row(s, 1, "A", "B", BAD_URI);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.parseStratiRels(s));

        assertThat(ex.getMessage()).contains("[Feuille 'Strati']");
    }

    @Test
    void parseActionCodes_rowThrows_wrappedWithSheetName() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "ActionCode", "Code", "Type uri");
        row(s, 1, "FOU", BAD_URI);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.parseActionCodes(s));

        assertThat(ex.getMessage()).contains("[Feuille 'ActionCode']");
    }

    @Test
    void parseActionUnits_rowThrows_wrappedWithSheetName() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Action", "Nom", "Identifiant", "Code", "Type uri",
                "Createur", "Institution", "Date debut", "Date fin", "Contexte spatiale");
        row(s, 1, "Fouille", "UA-001", "FOU", BAD_URI,
                "user@fr", "INST", null, null, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.parseActionUnits(s));

        assertThat(ex.getMessage()).contains("[Feuille 'Action']");
    }

    @Test
    void parseRecordingUnits_rowThrows_wrappedWithSheetName() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "UE",
                "Identifiant", "Description", "Type uri", "Cycle uri",
                "Couleur de la matrice", "Texture de la matrice", "Composition de la matrice",
                "Agent uri", "Interpretation uri", "Author email", "Institution",
                "Contributeurs email", "Date d'ouverture", "Date de fermeture",
                "Unite spatiale", "Unite d'action");
        row(s, 1,
                "123", "desc", BAD_URI, null,
                null, null, null,
                null, null, "a@b.fr", "INST",
                null, null, null, null, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.parseRecordingUnits(s, OOXMLImportService.ImportScope.ALL, null));

        assertThat(ex.getMessage()).contains("[Feuille 'UE']");
    }

    @Test
    void parseSpecimens_rowThrows_wrappedWithSheetName() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Spécimen",
                "Identifiant", "Catégorie", "Matière", "Designatation",
                "Institution", "Auteur fiche email", "Collecteur emails",
                "Unité d'enregistrement");
        row(s, 1, "SP-001", BAD_URI, null, null,
                "INST", "a@b.fr", null, "US-001");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.parseSpecimens(s,null));

        assertThat(ex.getMessage()).contains("[Feuille 'Spécimen']");
    }

    @Test
    void parsePhases_rowThrows_wrappedWithSheetName() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Phase",
                "Identifiant", "Titre", "Type uri", "Description",
                "Ordre", "Borne inferieure", "Borne superieure",
                "Auteur", "Projet", "Institution");
        row(s, 1, "PH-01", "Title", BAD_URI, "Desc",
                null, null, null, "a@b.fr", "UA-001", "INST");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.parsePhases(s, null));

        assertThat(ex.getMessage()).contains("[Feuille 'Phase']");
    }

}
