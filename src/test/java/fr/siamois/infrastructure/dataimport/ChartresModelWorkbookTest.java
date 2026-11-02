package fr.siamois.infrastructure.dataimport;

import fr.siamois.domain.services.vocabulary.ConceptService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The workbook offered as the import model uses the default names, and its mapping sheet lists them all. */
class ChartresModelWorkbookTest {

    private static final String MODEL = "/datasets/Import_Chartres_Projet.xlsx";

    private final OOXMLImportService service = new OOXMLImportService(Mockito.mock(ConceptService.class));

    @Test
    void everySheetIsAMappedTableAndEveryColumnIsRecognized() throws Exception {
        try (InputStream is = getClass().getResourceAsStream(MODEL); Workbook wb = WorkbookFactory.create(is)) {
            SheetMetadata meta = service.readSheetMetadata(wb);

            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                if (ImportSchema.META_SHEET.equals(sheet.getSheetName())) continue;
                Map<String, String> aliases = meta.columnAliases().get(sheet.getSheetName());
                assertThat(aliases).as(sheet.getSheetName()).isNotNull();
                for (String header : headers(sheet)) {
                    String normalizedHeader = ExcelCellHelper.normalize(header);
                    assertThat(aliases).as(sheet.getSheetName() + " / " + header).containsKey(normalizedHeader);
                }
            }
            assertThat(meta.tableToSheets()).containsKeys("spatial_unit", "recording_unit", "specimen", "phase",
                    "recordingRel", "stratiRel", "spatialUnitRel");
        }
    }

    @Test
    void sheetsAndColumnsCarryTheDefaultNamesAndNoUri() throws Exception {
        try (InputStream is = getClass().getResourceAsStream(MODEL); Workbook wb = WorkbookFactory.create(is)) {
            ImportSchema.DEFAULT_SHEET_NAMES.forEach((table, name) -> {
                if (ImportSchema.COLUMN_LABELS.containsKey(table)) {
                    assertThat(wb.getSheet(name)).as(name).isNotNull();
                }
            });
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                headers(sheet).forEach(h -> assertThat(h.toLowerCase()).as(sheet.getSheetName()).doesNotContain(" uri"));
            }
        }
    }

    @Test
    void metaSheetListsEveryColumnWithReadableHeaders() throws Exception {
        try (InputStream is = getClass().getResourceAsStream(MODEL); Workbook wb = WorkbookFactory.create(is)) {
            Sheet meta = wb.getSheet(ImportSchema.META_SHEET);
            assertThat(headers(meta)).containsExactly(ImportSchema.META_TABLE, ImportSchema.META_SHEET_NAME,
                    ImportSchema.META_COLUMN, ImportSchema.META_FIELD);

            int listed = meta.getLastRowNum();
            int columns = 0;
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                if (!ImportSchema.META_SHEET.equals(sheet.getSheetName())) columns += headers(sheet).size();
            }
            assertThat(listed).isEqualTo(columns);
        }
    }

    @Test
    void importingTheModel_readsEveryTableFromItsRenamedSheetsAndColumns() throws Exception {
        ConceptService concepts = Mockito.mock(ConceptService.class);
        fr.siamois.domain.models.vocabulary.Concept concept = new fr.siamois.domain.models.vocabulary.Concept();
        fr.siamois.domain.models.vocabulary.Vocabulary vocabulary = new fr.siamois.domain.models.vocabulary.Vocabulary();
        vocabulary.setExternalVocabularyId("th1");
        concept.setVocabulary(vocabulary);
        concept.setExternalId("1");
        Mockito.when(concepts.resolveConceptByLabel(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString())).thenReturn(concept);

        fr.siamois.dto.entity.InstitutionDTO institution = new fr.siamois.dto.entity.InstitutionDTO();
        institution.setId(1L);
        institution.setIdentifier("chartres");
        fr.siamois.dto.entity.ActionUnitDTO project = new fr.siamois.dto.entity.ActionUnitDTO();
        project.setId(3L);
        project.setFullIdentifier("P");
        project.setCreatedByInstitution(institution);

        try (InputStream is = getClass().getResourceAsStream(MODEL)) {
            ImportResult result = new OOXMLImportService(concepts)
                    .importFromExcel(is, OOXMLImportService.ImportScope.PROJECT, project);

            assertThat(result.errors()).isEmpty();
            assertThat(result.specs().spatialUnits()).isNotEmpty();
            assertThat(result.specs().recordingUnits()).isNotEmpty();
            assertThat(result.specs().specimenSpecs()).isNotEmpty();
            assertThat(result.specs().phaseSpecs()).isNotEmpty();
            assertThat(result.specs().recordingUnitRelSpecs()).isNotEmpty();
            assertThat(result.specs().recordingUnitStratiRelSpecs()).isNotEmpty();
            assertThat(result.specs().spatialUnitRelSpecs()).isNotEmpty();
            assertThat(result.specs().recordingUnits().get(0).type()).isNotNull();
        }
    }

    private static List<String> headers(Sheet sheet) {
        List<String> result = new ArrayList<>();
        Row row = sheet.getRow(0);
        for (int c = 0; row != null && c < row.getLastCellNum(); c++) {
            String value = ExcelCellHelper.getStringCell(row.getCell(c));
            if (value != null && !value.isBlank()) result.add(value);
        }
        return result;
    }
}
