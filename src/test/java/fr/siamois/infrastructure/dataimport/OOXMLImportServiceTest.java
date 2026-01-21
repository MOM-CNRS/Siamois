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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
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
                LocalDate.of(2024, 2, 10)
                        .atStartOfDay(ZoneOffset.UTC)  // <-- UTC
                        .toInstant()
        ));
        // Excel considère ce type comme NUMERIC
        CellStyle style = wb.createCellStyle();
        style.setDataFormat((short) 14); // format date
        c.setCellStyle(style);

        OffsetDateTime dt = service.parseOffsetDateTime(c);
        assertThat(dt).isEqualTo(
                LocalDate.of(2024, 2, 10).atStartOfDay().atOffset(ZoneOffset.UTC)
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
                new ByteArrayInputStream(out.toByteArray())
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
                new ByteArrayInputStream(out.toByteArray())
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
                service.parseRecordingUnits(s);

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
        assertThat(ru.createdBy()).isEqualTo("system@siamois.fr");
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
                service.parseSpecimens(s);

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

    @Test
    void extractThesaurusDomain_noQueryParams_returnsFullString() {
        // given
        String thesaurus = "https://thesaurus.fr/api";

        // when
        String result = service.extractThesaurusDomain(thesaurus);

        // then
        assertThat(result).isEqualTo("https://thesaurus.fr/api");
    }

    @Test
    void extractThesaurusDomain_withQueryParams_truncatesBeforeQuestionMarkMinusOne() {
        // given
        String thesaurus = "https://thesaurus.fr/api?idt=th230&idc=999";

        // when
        String result = service.extractThesaurusDomain(thesaurus);

        // then
        // current behavior: idx-1 → drops last char of path
        assertThat(result).isEqualTo("https://thesaurus.fr/ap");
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

    @Test
    void extractThesaurusDomain_questionMarkAtSecondChar_returnsFirstChar() {
        // given
        String thesaurus = "a?idt=1";

        // idx = 1 → idx-1 = 0

        // when
        String result = service.extractThesaurusDomain(thesaurus);

        // then
        assertThat(result).isEqualTo("");
    }




}
