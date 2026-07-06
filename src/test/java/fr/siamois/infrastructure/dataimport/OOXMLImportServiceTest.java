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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.misc.ImportProgress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class OOXMLImportServiceTest {

    private OOXMLImportService service;

    @Mock
    private ConceptService conceptService;

    @BeforeEach
    void setUp() {
        service = new OOXMLImportService(conceptService);
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

    private List<ImportError> errors() {
        return new ArrayList<>();
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

        List<ImportError> errs = errors();
        List<PersonSeeder.PersonSpec> persons = service.parsePersons(List.of(s), SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(persons).hasSize(1);
        assertThat(errs).isEmpty();

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

        List<ImportError> errs = errors();
        List<SpatialUnitSeeder.SpatialUnitSpecs> specs = service.parseSpatialUnits(List.of(s), null, SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(errs).isEmpty();
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

        List<ImportError> errs = errors();
        List<ActionCodeSeeder.ActionCodeSpec> specs = service.parseActionCodes(List.of(s), SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(specs).hasSize(1);
        assertThat(errs).isEmpty();
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

        List<ImportError> errs = errors();
        List<RecordingUnitRelSeeder.RecordingUnitRelDTO> rels = service.parseRecordingRels(List.of(s), SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(rels).hasSize(1);
        assertThat(errs).isEmpty();
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

        Sheet meta = sheet(wb, "_meta",
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

        ImportResult result = service.importFromExcel(
                new ByteArrayInputStream(out.toByteArray()), OOXMLImportService.ImportScope.ALL, null
        );

        assertThat(result).isNotNull();
        assertThat(result.specs()).isNotNull();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void importFromExcel_noMetaSheet_defaultSheetNamesAreRecognizedPerTable() throws Exception {
        Workbook wb = workbook();

        sheet(wb, "Institution", "Nom");
        row(wb.getSheet("Institution"), 1, "INRAP");

        sheet(wb, "Personne", "Email");
        row(wb.getSheet("Personne"), 1, "a@b.fr");

        Sheet spatial = sheet(wb, "Unité spatiale", "Nom");
        row(spatial, 1, "Parcelle A");

        sheet(wb, "Code", "Code", "Type uri");
        row(wb.getSheet("Code"), 1, "FOU", "uri?idt=th9&idc=99");

        Sheet action = sheet(wb, "Unite action", "Nom", "Identifiant");
        row(action, 1, "Fouille", "UA-001");

        Sheet specimen = sheet(wb, "Prelev", "Identifiant");
        row(specimen, 1, "SP-001");

        sheet(wb, "UE_rel", "Parent", "Enfant");
        row(wb.getSheet("UE_rel"), 1, "100", "101");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);

        ImportResult result = service.importFromExcel(
                new ByteArrayInputStream(out.toByteArray()), OOXMLImportService.ImportScope.ALL, null
        );

        assertThat(result.errors()).isEmpty();
        assertThat(result.specs().institutions()).hasSize(1);
        assertThat(result.specs().persons()).hasSize(1);
        assertThat(result.specs().spatialUnits()).hasSize(1);
        assertThat(result.specs().actionCodes()).hasSize(1);
        assertThat(result.specs().actionUnits()).hasSize(1);
        assertThat(result.specs().specimenSpecs()).hasSize(1);
        assertThat(result.specs().recordingUnitRelSpecs()).hasSize(1);
    }

    @Test
    void importFromExcel_columnAliasMapping() throws Exception {
        Workbook wb = workbook();

        Sheet personSheet = sheet(wb, "Personnes", "Courriel", "Nom", "Prenom", "Login");
        row(personSheet, 1, "alice@example.fr", "Martin", "Alice", "amartin");

        Sheet meta = sheet(wb, "_meta",
                "sheet_id", "sheet_name", "column_alias", "column_canonical");
        row(meta, 1, "person", "Personnes", null, null);
        row(meta, 2, "person", "Personnes", "Courriel", "email");
        row(meta, 3, "person", "Personnes", "Login", "identifiant");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);

        ImportResult result = service.importFromExcel(
                new ByteArrayInputStream(out.toByteArray()), OOXMLImportService.ImportScope.ALL, null
        );

        assertThat(result.errors()).isEmpty();
        assertThat(result.specs().persons()).hasSize(1);
        assertThat(result.specs().persons().get(0).email()).isEqualTo("alice@example.fr");
        assertThat(result.specs().persons().get(0).username()).isEqualTo("amartin");
    }

    @Test
    void importFromExcel_multipleSheetsSameTable() throws Exception {
        Workbook wb = workbook();

        Sheet s1 = sheet(wb, "Personnes_A", "Email", "Nom");
        row(s1, 1, "alice@example.fr", "Alice");

        Sheet s2 = sheet(wb, "Personnes_B", "Email", "Nom");
        row(s2, 1, "bob@example.fr", "Bob");

        Sheet meta = sheet(wb, "_meta", "sheet_id", "sheet_name");
        row(meta, 1, "person", "Personnes_A");
        row(meta, 2, "person", "Personnes_B");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);

        ImportResult result = service.importFromExcel(
                new ByteArrayInputStream(out.toByteArray()), OOXMLImportService.ImportScope.ALL, null
        );

        assertThat(result.errors()).isEmpty();
        assertThat(result.specs().persons()).hasSize(2)
                .extracting(PersonSeeder.PersonSpec::email)
                .containsExactlyInAnyOrder("alice@example.fr", "bob@example.fr");
    }

    @Test
    void parseRecordingUnits_fullValidRow() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("Unite action");

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

        List<ImportError> errs = errors();
        List<RecordingUnitSeeder.RecordingUnitSpecs> specs =
                service.parseRecordingUnits(List.of(s), OOXMLImportService.ImportScope.ALL, null, SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(specs).hasSize(1);
        assertThat(errs).isEmpty();

        RecordingUnitSeeder.RecordingUnitSpecs ru = specs.get(0);

        assertThat(ru.fullIdentifier()).isEqualTo("123");
        assertThat(ru.identifier()).isEqualTo(123);

        assertThat(ru.type()).isEqualTo(new ConceptSeeder.ConceptKey("th1", "10"));
        assertThat(ru.geomorphologicalCycle()).isEqualTo(new ConceptSeeder.ConceptKey("th2", "20"));
        assertThat(ru.geomorphologicalAgent()).isEqualTo(new ConceptSeeder.ConceptKey("th3", "30"));
        assertThat(ru.interpretation()).isEqualTo(new ConceptSeeder.ConceptKey("th4", "40"));

        assertThat(ru.authorEmail()).isEqualTo("author@site.fr");
        assertThat(ru.institutionIdentifier()).isEqualTo("INST");
        assertThat(ru.excavators()).containsExactly("a@b.fr", "c@d.fr");

        assertThat(ru.beginDate()).isEqualTo(
                OffsetDateTime.of(2023, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(ru.endDate()).isEqualTo(
                OffsetDateTime.of(2023, 2, 20, 0, 0, 0, 0, ZoneOffset.UTC)
        );

        assertThat(ru.spatialUnitName().unitName()).isEqualTo("US-01");
        assertThat(ru.actionUnitIdentifier().fullIdentifier()).isEqualTo("UA-99");

        assertThat(ru.matrixColor()).isEqualTo("Brun");
        assertThat(ru.matrixComposition()).isEqualTo("Argile");
        assertThat(ru.matrixTexture()).isEqualTo("Sableux");

        assertThat(ru.createdBy()).isEqualTo(ImportSchema.SIAMOIS_SYSTEM);
        assertThat(ru.creationTime()).isNotNull();
    }

    @Test
    void parseActionUnits_fullValidRow() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("Unite action");

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

        List<ImportError> errs = errors();
        List<ActionUnitSeeder.ActionUnitSpecs> specs =
                service.parseActionUnits(List.of(s), SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(specs).hasSize(1);
        assertThat(errs).isEmpty();

        ActionUnitSeeder.ActionUnitSpecs au = specs.get(0);

        assertThat(au.fullIdentifier()).isEqualTo("UA-001");
        assertThat(au.identifier()).isEqualTo("UA-001");
        assertThat(au.name()).isEqualTo("Décapage zone nord");

        assertThat(au.primaryActionCode()).isEqualTo("FOU");

        assertThat(au.typeVocabularyExtId()).isEqualTo("th9");
        assertThat(au.typeConceptExtId()).isEqualTo("99");

        assertThat(au.authorEmail()).isEqualTo("user@site.fr");
        assertThat(au.institutionIdentifier()).isEqualTo("INST");

        assertThat(au.beginDate()).isEqualTo(
                OffsetDateTime.of(2023, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(au.endDate()).isEqualTo(
                OffsetDateTime.of(2023, 3, 10, 0, 0, 0, 0, ZoneOffset.UTC)
        );

        assertThat(au.spatialContextKeys())
                .extracting(SpatialUnitSeeder.SpatialUnitKey::unitName)
                .containsExactly("US-01", "US-02");
    }

    @Test
    void parseInstitutions_fullValidRow() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("Institution");

        Row header = s.createRow(0);
        header.createCell(0).setCellValue("Nom");
        header.createCell(1).setCellValue("Description");
        header.createCell(2).setCellValue("Identifiant");
        header.createCell(3).setCellValue("Email Admins");
        header.createCell(4).setCellValue("Thesaurus");

        Row row = s.createRow(1);
        row.createCell(0).setCellValue("INRAP");
        row.createCell(1).setCellValue("Institut national");
        row.createCell(2).setCellValue("INRAP-ID");
        row.createCell(3).setCellValue("a@inrap.fr; b@inrap.fr");
        row.createCell(4).setCellValue("https://thesaurus.fr/api?idt=th230&idc=999");

        List<ImportError> errs = errors();
        List<InstitutionSeeder.InstitutionSpec> specs =
                service.parseInstitutions(List.of(s), SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(specs).hasSize(1);
        assertThat(errs).isEmpty();

        InstitutionSeeder.InstitutionSpec inst = specs.get(0);

        assertThat(inst.name()).isEqualTo("INRAP");
        assertThat(inst.description()).isEqualTo("Institut national");
        assertThat(inst.identifier()).isEqualTo("INRAP-ID");

        assertThat(inst.managerEmails())
                .containsExactly("a@inrap.fr", "b@inrap.fr");

        assertThat(inst.externalId()).isEqualTo("th230");
    }

    @Test
    void parseSpecimens_fullValidRow() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("Specimen");

        Row header = s.createRow(0);
        header.createCell(0).setCellValue("Identifiant");
        header.createCell(1).setCellValue("Catégorie");
        header.createCell(2).setCellValue("Matière");
        header.createCell(3).setCellValue("Designatation");
        header.createCell(4).setCellValue("Institution");
        header.createCell(5).setCellValue("Auteur fiche email");
        header.createCell(6).setCellValue("Collecteur emails");
        header.createCell(7).setCellValue("Unité d'enregistrement");

        Row row = s.createRow(1);
        row.createCell(0).setCellValue("SP-001");
        row.createCell(1).setCellValue("https://thesaurus.fr/api?idt=th12&idc=88");
        row.createCell(2).setCellValue("https://thesaurus.fr/api?idt=th12&idc=89");
        row.createCell(3).setCellValue("https://thesaurus.fr/api?idt=th12&idc=90");
        row.createCell(4).setCellValue("INRAP");
        row.createCell(5).setCellValue("user@site.fr");
        row.createCell(6).setCellValue("user@site.fr");
        row.createCell(7).setCellValue("US-001");

        List<ImportError> errs = errors();
        List<SpecimenSeeder.SpecimenSpecs> specs =
                service.parseSpecimens(List.of(s), null, SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(specs).hasSize(1);
        assertThat(errs).isEmpty();

        SpecimenSeeder.SpecimenSpecs sp = specs.get(0);

        assertThat(sp.fullIdentifier()).isEqualTo("SP-001");
        assertThat(sp.type().vocabularyExtId()).isEqualTo("th12");
        assertThat(sp.type().conceptExtId()).isEqualTo("89");
        assertThat(sp.institutionIdentifier()).isEqualTo("INRAP");
        assertThat(sp.recordingUnitKey().fullIdentifier()).isEqualTo("US-001");
    }

    @Test
    void extractThesaurusDomain_nullInput_returnsNull() {
        assertThat(service.extractThesaurusDomain(null)).isNull();
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

    private ActionUnitDTO actionUnitWithInstitution(long institutionId) {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(institutionId);
        inst.setIdentifier("INST");
        ActionUnitDTO au = new ActionUnitDTO();
        au.setFullIdentifier("AU-001");
        au.setCreatedByInstitution(inst);
        return au;
    }

    private fr.siamois.domain.models.vocabulary.Concept conceptWithKey(String vocabExtId, String conceptExtId) {
        fr.siamois.domain.models.vocabulary.Vocabulary vocabulary = new fr.siamois.domain.models.vocabulary.Vocabulary();
        vocabulary.setExternalVocabularyId(vocabExtId);
        fr.siamois.domain.models.vocabulary.Concept concept = new fr.siamois.domain.models.vocabulary.Concept();
        concept.setVocabulary(vocabulary);
        concept.setExternalId(conceptExtId);
        return concept;
    }

    @Test
    void parseStratiRels_labelFallback_resolvesConceptViaConceptService() {
        var au = actionUnitWithInstitution(42L);
        org.mockito.Mockito.when(conceptService.resolveConceptByLabel(42L, RecordingUnit.STRATI_FIELD_CODE, "Postérieur"))
                .thenReturn(conceptWithKey("th240", "9999"));

        Workbook wb = workbook();
        Sheet s = sheet(wb, "Strati", "us1", "us2", "relation", "relation label");
        row(s, 1, "US-001", "US-002", "", "Postérieur");

        RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO dto = service.parseStratiRels(s, au).get(0);

        assertThat(dto.rel()).isEqualTo(new ConceptSeeder.ConceptKey("th240", "9999"));
    }

    @Test
    void parseStratiRels_uriAndLabelBothPresent_uriWinsAndLabelIsIgnored() {
        var au = actionUnitWithInstitution(42L);

        Workbook wb = workbook();
        Sheet s = sheet(wb, "Strati", "us1", "us2", "relation", "relation label");
        row(s, 1, "US-001", "US-002", "uri?idt=th240&idc=4287979", "Postérieur");

        RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO dto = service.parseStratiRels(s, au).get(0);

        assertThat(dto.rel()).isEqualTo(new ConceptSeeder.ConceptKey("th240", "4287979"));
        org.mockito.Mockito.verifyNoInteractions(conceptService);
    }

    @Test
    void parseStratiRels_labelNoMatch_errorAddedWithLabelColumnName() {
        var au = actionUnitWithInstitution(42L);
        org.mockito.Mockito.when(conceptService.resolveConceptByLabel(42L, RecordingUnit.STRATI_FIELD_CODE, "Inconnu"))
                .thenThrow(new IllegalStateException("Concept 'Inconnu' introuvable dans le thésaurus configuré"));

        Workbook wb = workbook();
        Sheet s = sheet(wb, "Strati", "us1", "us2", "relation", "relation label");
        row(s, 1, "US-001", "US-002", "", "Inconnu");

        List<ImportError> errors = new ArrayList<>();
        List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> specs =
                service.parseStratiRels(List.of(s), au, SheetMetadata.empty(), errors, new ImportProgress());

        assertThat(specs).isEmpty();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).column()).isEqualTo("relation label");
        assertThat(errors.get(0).message()).contains("introuvable");
    }

    @Test
    void parseRecordingUnits_labelFallback_resolvesTypeConceptViaConceptService() {
        var au = actionUnitWithInstitution(7L);
        org.mockito.Mockito.when(conceptService.resolveConceptByLabel(7L, RecordingUnit.TYPE_FIELD_CODE, "Fosse"))
                .thenReturn(conceptWithKey("th1", "5"));

        Workbook wb = workbook();
        Sheet s = sheet(wb, "UE", "Identifiant", "Description", "type uri", "type label");
        row(s, 1, "UE-001", "desc", "", "Fosse");

        RecordingUnitSeeder.RecordingUnitSpecs spec = service.parseRecordingUnits(s, OOXMLImportService.ImportScope.PROJECT, au).get(0);

        assertThat(spec.type()).isEqualTo(new ConceptSeeder.ConceptKey("th1", "5"));
    }

    @Test
    void parseSpatialUnits_labelFallback_resolvesCategoryConceptViaConceptService() {
        var au = actionUnitWithInstitution(9L);
        org.mockito.Mockito.when(conceptService.resolveConceptByLabel(9L, SpatialUnit.CATEGORY_FIELD_CODE, "Site"))
                .thenReturn(conceptWithKey("th2", "10"));

        Workbook wb = workbook();
        Sheet s = sheet(wb, "Lieu", "nom", "uri type", "type label", "enfants");
        row(s, 1, "Lieu A", "", "Site", "");

        SpatialUnitSeeder.SpatialUnitSpecs spec = service.parseSpatialUnits(s, au).get(0);

        assertThat(spec.typeVocabularyExtId()).isEqualTo("th2");
        assertThat(spec.typeConceptExtId()).isEqualTo("10");
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
        row(s, 2, "", "D");
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

        assertThat(service.parsePhases(s, null)).isEmpty();
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

        List<PhaseSeeder.PhaseSpecs> specs = service.parsePhases(s, null);

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

        PhaseSeeder.PhaseSpecs ph = service.parsePhases(s, au).get(0);

        assertThat(ph.actionUnitKey().fullIdentifier()).isEqualTo("AU-FULL-ID");
        assertThat(ph.actionUnitKey().institutionIdentifier()).isEqualTo("CODE-INST");
    }

    @Test
    void parsePhases_missingOptionalColumns_nullsForMissingFields() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Phase", "Identifiant");
        row(s, 1, "PH-03");

        PhaseSeeder.PhaseSpecs ph = service.parsePhases(s, null).get(0);

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

        assertThat(service.parsePhases(s, null)).hasSize(3);
    }

    @Test
    void parsePhases_mixedBlankIdentifiers_onlyNonBlankIncluded() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Phase", "Identifiant", "Titre");
        row(s, 1, "PH-01", "Phase A");
        row(s, 2, "", "Phase B");
        row(s, 3, "PH-03", "Phase C");

        assertThat(service.parsePhases(s, null)).hasSize(2);
    }

    @Test
    void extractThesaurusDomain_questionMarkAtStart_returnsEmptyString() {
        assertThat(service.extractThesaurusDomain("?idt=th230")).isNull();
    }

    // -------------------------------------------------------------------------
    // Error collection: bad rows are skipped, errors accumulated
    // -------------------------------------------------------------------------

    private static final String BAD_URI = "not-a-valid-uri";

    private void formulaCell(Row r, int col) {
        r.createCell(col).setCellFormula("1+1");
    }

    @Test
    void parseInstitutions_badUriRow_isSkippedAndErrorCollected() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Institution", "Nom", "Description", "Identifiant", "Email Admins", "Thesaurus");
        row(s, 1, "INRAP-OK", "Desc", "ID", "a@b.fr", "uri?idt=th1&idc=1");
        row(s, 2, "INRAP-BAD", "Desc", "ID2", "a@b.fr", BAD_URI);

        List<ImportError> errs = errors();
        List<InstitutionSeeder.InstitutionSpec> result = service.parseInstitutions(List.of(s), SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("INRAP-OK");
        assertThat(errs).hasSize(1);
        assertThat(errs.get(0).sheet()).isEqualTo("Institution");
    }

    @Test
    void parsePersons_formulaCell_isSkippedAndErrorCollected() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Personne", "Email", "Nom", "Prenom", "Identifiant");
        row(s, 1, "valid@example.fr", "Dupont", "Jean", "jd");
        Row bad = s.createRow(2);
        formulaCell(bad, 0);

        List<ImportError> errs = errors();
        List<PersonSeeder.PersonSpec> result = service.parsePersons(List.of(s), SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(result).hasSize(1);
        assertThat(errs).hasSize(1);
        assertThat(errs.get(0).sheet()).isEqualTo("Personne");
    }

    @Test
    void parseSpatialUnits_badUriRow_isSkippedAndErrorCollected() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Lieu", "Nom", "Uri type", "Createur", "Institution", "Enfants");
        row(s, 1, "Site OK", "uri?idt=th1&idc=1", "sys", "INST", null);
        row(s, 2, "Site Bad", BAD_URI, "sys", "INST", null);

        List<ImportError> errs = errors();
        List<SpatialUnitSeeder.SpatialUnitSpecs> result = service.parseSpatialUnits(List.of(s), null, SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Site OK");
        assertThat(errs).hasSize(1);
        assertThat(errs.get(0).sheet()).isEqualTo("Lieu");
    }

    @Test
    void parseRecordingRels_formulaCell_isSkippedAndErrorCollected() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Relations", "Parent", "Enfant");
        row(s, 1, "100", "101");
        Row bad = s.createRow(2);
        formulaCell(bad, 0);
        bad.createCell(1).setCellValue("child");

        List<ImportError> errs = errors();
        List<RecordingUnitRelSeeder.RecordingUnitRelDTO> result = service.parseRecordingRels(List.of(s), SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(result).hasSize(1);
        assertThat(errs).hasSize(1);
        assertThat(errs.get(0).sheet()).isEqualTo("Relations");
    }

    @Test
    void parseStratiRels_badUri_isSkippedAndErrorCollected() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Strati", "us1", "us2", "relation");
        row(s, 1, "A", "B", "uri?idt=th240&idc=1");
        row(s, 2, "C", "D", BAD_URI);

        List<ImportError> errs = errors();
        List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> result =
                service.parseStratiRels(List.of(s), null, SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(result).hasSize(1);
        assertThat(errs).hasSize(1);
        assertThat(errs.get(0).sheet()).isEqualTo("Strati");
    }

    @Test
    void parseActionCodes_badUri_isSkippedAndErrorCollected() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "ActionCode", "Code", "Type uri");
        row(s, 1, "FOU", "uri?idt=th9&idc=99");
        row(s, 2, "BAD", BAD_URI);

        List<ImportError> errs = errors();
        List<ActionCodeSeeder.ActionCodeSpec> result = service.parseActionCodes(List.of(s), SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("FOU");
        assertThat(errs).hasSize(1);
        assertThat(errs.get(0).sheet()).isEqualTo("ActionCode");
    }

    @Test
    void parseActionUnits_badUri_isSkippedAndErrorCollected() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Action", "Nom", "Identifiant", "Code", "Type uri",
                "Createur", "Institution", "Date debut", "Date fin", "Contexte spatiale");
        row(s, 1, "Fouille OK", "UA-001", "FOU", "uri?idt=th9&idc=99",
                "user@fr", "INST", null, null, null);
        row(s, 2, "Fouille Bad", "UA-002", "FOU", BAD_URI,
                "user@fr", "INST", null, null, null);

        List<ImportError> errs = errors();
        List<ActionUnitSeeder.ActionUnitSpecs> result = service.parseActionUnits(List.of(s), SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullIdentifier()).isEqualTo("UA-001");
        assertThat(errs).hasSize(1);
        assertThat(errs.get(0).sheet()).isEqualTo("Action");
    }

    @Test
    void parseRecordingUnits_badUri_isSkippedAndErrorCollected() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "UE",
                "Identifiant", "Description", "Type uri", "Cycle uri",
                "Couleur de la matrice", "Texture de la matrice", "Composition de la matrice",
                "Agent uri", "Interpretation uri", "Author email", "Institution",
                "Contributeurs email", "Date d'ouverture", "Date de fermeture",
                "Unite spatiale", "Unite d'action");
        row(s, 1, "100", "desc valide", "uri?idt=th1&idc=10", null,
                null, null, null, null, null, "a@b.fr", "INST",
                null, null, null, null, null);
        row(s, 2,
                "123", "desc", BAD_URI, null,
                null, null, null, null, null, "a@b.fr", "INST",
                null, null, null, null, null);

        List<ImportError> errs = errors();
        List<RecordingUnitSeeder.RecordingUnitSpecs> result =
                service.parseRecordingUnits(List.of(s), OOXMLImportService.ImportScope.ALL, null, SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullIdentifier()).isEqualTo("100");
        assertThat(errs).hasSize(1);
        assertThat(errs.get(0).sheet()).isEqualTo("UE");
    }

    @Test
    void parseSpecimens_badUri_isSkippedAndErrorCollected() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Spécimen",
                "Identifiant", "Catégorie", "Matière", "Designatation",
                "Institution", "Auteur fiche email", "Collecteur emails",
                "Unité d'enregistrement");
        row(s, 1, "SP-OK", "uri?idt=th12&idc=88", null, null, "INRAP", "a@b.fr", null, "US-001");
        row(s, 2, "SP-001", BAD_URI, null, null, "INST", "a@b.fr", null, "US-001");

        List<ImportError> errs = errors();
        List<SpecimenSeeder.SpecimenSpecs> result = service.parseSpecimens(List.of(s), null, SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullIdentifier()).isEqualTo("SP-OK");
        assertThat(errs).hasSize(1);
        assertThat(errs.get(0).sheet()).isEqualTo("Spécimen");
    }

    @Test
    void parsePhases_badUri_isSkippedAndErrorCollected() {
        Workbook wb = workbook();
        Sheet s = sheet(wb, "Phase",
                "Identifiant", "Titre", "Type uri", "Description",
                "Ordre", "Borne inferieure", "Borne superieure",
                "Auteur", "Projet", "Institution");
        row(s, 1, "PH-OK", "Title OK", "uri?idt=th240&idc=100", "Desc",
                null, null, null, "a@b.fr", "UA-001", "INST");
        row(s, 2, "PH-BAD", "Title", BAD_URI, "Desc",
                null, null, null, "a@b.fr", "UA-001", "INST");

        List<ImportError> errs = errors();
        List<PhaseSeeder.PhaseSpecs> result = service.parsePhases(List.of(s), null, SheetMetadata.empty(), errs, new ImportProgress());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).identifier()).isEqualTo("PH-OK");
        assertThat(errs).hasSize(1);
        assertThat(errs.get(0).sheet()).isEqualTo("Phase");
    }
}
