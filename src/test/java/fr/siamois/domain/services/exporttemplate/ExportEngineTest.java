package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.*;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.mapper.ConceptMapper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ExportEngineTest {

    private static final Long PROJECT_ID = 9L;
    private static final ConceptRef CODE = new ConceptRef("th230", "1", null);
    private static final ConceptRef NATURE = new ConceptRef("th230", "2", null);
    private static final Source UE = new EntitySource(EntityKind.RECORDING_UNIT, List.of());

    @Mock private ExportSourceReader sourceReader;
    @Mock private ExportValueReader valueReader;
    @Mock private ExportFieldResolver fieldResolver;
    @Mock private ConceptLabelBatchResolver labelResolver;
    @Mock private ConceptMapper conceptMapper;

    private ExportEngine engine;
    private ActionUnit project;

    @BeforeEach
    void setUp() {
        engine = new ExportEngine(sourceReader, valueReader, fieldResolver, labelResolver, conceptMapper);
        project = new ActionUnit();
        project.setFullIdentifier("ORG-1");
        project.setName("Fouille Sainte-Ursule");
        project.setOaCode("OA42");
        lenient().when(sourceReader.project(PROJECT_ID)).thenReturn(project);
        lenient().when(valueReader.prefetch(any(), any(), any())).thenReturn(ExportValueReader.AnswerCache.EMPTY);
    }

    /** Une ligne dont l'« entité » est une map concept-clé → valeurs ; le lecteur de valeurs la dépouille. */
    private static ExportRow row(Map<String, List<Object>> values) {
        return ExportRow.of(ExportSubject.RECORDING_UNIT, values);
    }

    @SuppressWarnings("unchecked")
    private void stubFieldReads() {
        lenient().when(fieldResolver.resolve(any(), any(), eq(PROJECT_ID), any())).thenAnswer(i -> {
            ConceptRef ref = i.getArgument(1);
            return "unknown".equals(ref.conceptId()) ? List.of() : List.of(org.mockito.Mockito.mock(CustomField.class));
        });
        lenient().when(valueReader.read(any(), any(), any(), any())).thenAnswer(i -> {
            ExportRow r = i.getArgument(0);
            FieldRef f = i.getArgument(1);
            if (f instanceof ConceptField cf) {
                return ((Map<String, List<Object>>) r.entity()).getOrDefault(cf.concept().conceptId(), List.of());
            }
            return List.of();
        });
    }

    private void rows(Source source, ExportRow... rows) {
        lenient().when(sourceReader.read(source, PROJECT_ID)).thenReturn(List.of(rows));
    }

    private static ExportTemplateDefinition template(String pattern, ExportTemplateDefinition.Sheet... sheets) {
        return new ExportTemplateDefinition(3, "u", "1", "Rapport", pattern, List.of(sheets));
    }

    private static ExportTemplateDefinition.Sheet sheet(String name, boolean omit, List<String> sortBy, List<Source> sources, Column... columns) {
        return new ExportTemplateDefinition.Sheet(name, omit, sources, sortBy, List.of(columns));
    }

    private static Column column(String header, OutputType output, Rule... rules) {
        return new Column(header, output, List.of(rules));
    }

    private static Rule direct(ConceptRef ref, ListOptions list) {
        return new DirectRule(List.of(), new ConceptField(ref), List.of(), list);
    }

    private ExportEngine.Result run(ExportTemplateDefinition d) {
        return engine.run(d, PROJECT_ID, "fr");
    }

    private static Workbook open(ExportEngine.Result result) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(result.content()));
    }

    @Test
    void fieldAndConstantColumns_areWrittenWithBoldHeaders() throws IOException {
        stubFieldReads();
        rows(UE, row(Map.of("1", List.of("UE-1"))), row(Map.of("1", List.of("UE-2"))));

        ExportEngine.Result result = run(template(null, sheet("UE", false, List.of(), List.of(UE),
                column("code", OutputType.TEXT, direct(CODE, null)),
                column("type", OutputType.TEXT, new ConstantRule(List.of(), "US")))));

        try (Workbook wb = open(result)) {
            Sheet s = wb.getSheet("UE");
            assertThat(s.getRow(0).getCell(0).getStringCellValue()).isEqualTo("code");
            assertThat(wb.getFontAt(s.getRow(0).getCell(0).getCellStyle().getFontIndex()).getBold()).isTrue();
            assertThat(s.getRow(1).getCell(0).getStringCellValue()).isEqualTo("UE-1");
            assertThat(s.getRow(2).getCell(1).getStringCellValue()).isEqualTo("US");
            assertThat(s.getLastRowNum()).isEqualTo(2);
        }
        assertThat(result.rowsBySheet()).containsEntry("UE", 2);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void multipleValues_areJoined_andSortedWhenAsked() throws IOException {
        stubFieldReads();
        rows(UE, row(Map.of("1", List.of("b", "a"))));

        ExportEngine.Result result = run(template(null, sheet("UE", false, List.of(), List.of(UE),
                column("plain", OutputType.TEXT, direct(CODE, null)),
                column("sorted", OutputType.TEXT, direct(CODE, new ListOptions(" | ", true))))));

        try (Workbook wb = open(result)) {
            assertThat(wb.getSheet("UE").getRow(1).getCell(0).getStringCellValue()).isEqualTo("b & a");
            assertThat(wb.getSheet("UE").getRow(1).getCell(1).getStringCellValue()).isEqualTo("a | b");
        }
    }

    @Test
    void concat_skipsEmptyParts_andAddsLabels() throws IOException {
        stubFieldReads();
        rows(UE, row(Map.of("1", List.of("UE-1"), "2", List.of("sable"))), row(Map.of("1", List.of("UE-2"))), row(Map.of()));
        Rule concat = new ConcatRule(List.of(), List.of(
                new ConcatPart("code", null, new ConceptField(CODE), List.of()),
                new ConcatPart(null, "-", null, List.of()),
                new ConcatPart("nature", null, new ConceptField(NATURE), List.of())), " ", "=");

        ExportEngine.Result result = run(template(null, sheet("UE", false, List.of(), List.of(UE),
                column("ref", OutputType.TEXT, concat))));

        try (Workbook wb = open(result)) {
            Sheet s = wb.getSheet("UE");
            assertThat(s.getRow(1).getCell(0).getStringCellValue()).isEqualTo("code=UE-1 - nature=sable");
            assertThat(s.getRow(2).getCell(0).getStringCellValue()).isEqualTo("code=UE-2 -");
            assertThat(s.getRow(3).getCell(0).getStringCellValue()).isEmpty();
        }
    }

    @Test
    void numberColumn_writesNumericCells_andWarnsOnText() throws IOException {
        stubFieldReads();
        rows(UE, row(Map.of("1", List.of(new java.math.BigDecimal("12.50")))), row(Map.of("1", List.of("3,5"))), row(Map.of("1", List.of("n/a"))));

        ExportEngine.Result result = run(template(null, sheet("UE", false, List.of(), List.of(UE),
                column("z", OutputType.NUMBER, direct(CODE, null)))));

        try (Workbook wb = open(result)) {
            Sheet s = wb.getSheet("UE");
            assertThat(s.getRow(1).getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(s.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(12.5);
            assertThat(s.getRow(2).getCell(0).getNumericCellValue()).isEqualTo(3.5);
            assertThat(s.getRow(3).getCell(0).getStringCellValue()).isEqualTo("n/a");
        }
        assertThat(result.warnings()).containsExactly(new ExportWarning(ExportWarning.Code.NOT_NUMERIC, "UE", "z", "n/a"));
    }

    @Test
    void numberColumn_keepsTextWhenADoubleWouldLosePrecision_orWhenAmbiguous() throws IOException {
        stubFieldReads();
        rows(UE, row(Map.of("1", List.of("12345678901234567"))), row(Map.of("1", List.of("1,234.5"))));

        ExportEngine.Result result = run(template(null, sheet("UE", false, List.of(), List.of(UE),
                column("z", OutputType.NUMBER, direct(CODE, null)))));

        try (Workbook wb = open(result)) {
            Sheet s = wb.getSheet("UE");
            assertThat(s.getRow(1).getCell(0).getStringCellValue()).isEqualTo("12345678901234567");
            assertThat(s.getRow(2).getCell(0).getStringCellValue()).isEqualTo("1,234.5");
        }
        assertThat(result.warnings()).extracting(ExportWarning::code).containsOnly(ExportWarning.Code.NOT_NUMERIC);
    }

    @Test
    void sortBy_ordersRows_numbersNumerically() throws IOException {
        stubFieldReads();
        rows(UE, row(Map.of("1", List.of("10"))), row(Map.of("1", List.of("9"))), row(Map.of("1", List.of("100"))));

        ExportEngine.Result result = run(template(null, sheet("UE", false, List.of("n"), List.of(UE),
                column("n", OutputType.NUMBER, direct(CODE, null)))));

        try (Workbook wb = open(result)) {
            Sheet s = wb.getSheet("UE");
            assertThat(List.of(s.getRow(1).getCell(0).getNumericCellValue(), s.getRow(2).getCell(0).getNumericCellValue(),
                    s.getRow(3).getCell(0).getNumericCellValue())).containsExactly(9.0, 10.0, 100.0);
        }
    }

    @Test
    void unresolvedField_leavesTheColumnEmpty_withAWarning() throws IOException {
        stubFieldReads();
        rows(UE, row(Map.of("1", List.of("UE-1"))));

        ExportEngine.Result result = run(template(null, sheet("UE", false, List.of(), List.of(UE),
                column("code", OutputType.TEXT, direct(new ConceptRef("th230", "unknown", null), null)))));

        try (Workbook wb = open(result)) {
            assertThat(wb.getSheet("UE").getRow(1).getCell(0).getStringCellValue()).isEmpty();
        }
        assertThat(result.warnings()).containsExactly(
                new ExportWarning(ExportWarning.Code.FIELD_UNRESOLVED, "UE", "code", "th230|unknown"));
    }

    @Test
    void severalSources_areUnioned_eachWithItsOwnRule() throws IOException {
        stubFieldReads();
        Source project = new ProjectSource();
        rows(UE, row(Map.of("1", List.of("UE-1"))));
        rows(project, ExportRow.of(ExportSubject.PROJECT, Map.of("1", List.of("OA-X"))));

        ExportEngine.Result result = run(template(null, sheet("S", false, List.of(), List.of(UE, project),
                column("code", OutputType.TEXT,
                        new DirectRule(List.of(0), new ConceptField(CODE), List.of(), null),
                        new ConstantRule(List.of(1), "projet")))));

        try (Workbook wb = open(result)) {
            Sheet s = wb.getSheet("S");
            assertThat(s.getRow(1).getCell(0).getStringCellValue()).isEqualTo("UE-1");
            assertThat(s.getRow(2).getCell(0).getStringCellValue()).isEqualTo("projet");
        }
    }

    @Test
    void ruleWithoutAMatchingSource_leavesTheCellEmpty() throws IOException {
        Source project = new ProjectSource();
        rows(project, ExportRow.of(ExportSubject.PROJECT, Map.of()));

        ExportEngine.Result result = run(template(null, sheet("S", false, List.of(), List.of(project),
                column("c", OutputType.TEXT, new ConstantRule(List.of(3), "x")))));

        try (Workbook wb = open(result)) {
            assertThat(wb.getSheet("S").getRow(1).getCell(0).getStringCellValue()).isEmpty();
        }
    }

    @Test
    void emptySheet_isOmittedWhenAsked_andIfEverythingIsEmptyTheFirstIsKept() throws IOException {
        Source project = new ProjectSource();
        rows(project, ExportRow.of(ExportSubject.PROJECT, Map.of()));
        rows(UE);
        ExportTemplateDefinition.Sheet filled = sheet("OA", false, List.of(), List.of(project), column("c", OutputType.TEXT, new ConstantRule(List.of(), "x")));
        ExportTemplateDefinition.Sheet empty = sheet("UE", true, List.of(), List.of(UE), column("c", OutputType.TEXT, new ConstantRule(List.of(), "x")));

        try (Workbook wb = open(run(template(null, filled, empty)))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
            assertThat(wb.getSheetName(0)).isEqualTo("OA");
        }
        ExportEngine.Result onlyEmpty = run(template(null, empty));
        try (Workbook wb = open(onlyEmpty)) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
            assertThat(wb.getSheet("UE").getLastRowNum()).isZero();
        }
        assertThat(onlyEmpty.warnings()).extracting(ExportWarning::code).contains(ExportWarning.Code.EMPTY_WORKBOOK);
    }

    @Test
    void longCell_isTruncated_withAWarning() throws IOException {
        Source project = new ProjectSource();
        rows(project, ExportRow.of(ExportSubject.PROJECT, Map.of()));

        ExportEngine.Result result = run(template(null, sheet("S", false, List.of(), List.of(project),
                column("c", OutputType.TEXT, new ConstantRule(List.of(), "x".repeat(ExportWorkbookWriter.MAX_CELL_LENGTH + 10))))));

        try (Workbook wb = open(result)) {
            assertThat(wb.getSheet("S").getRow(1).getCell(0).getStringCellValue()).hasSize(ExportWorkbookWriter.MAX_CELL_LENGTH);
        }
        assertThat(result.warnings()).extracting(ExportWarning::code).containsExactly(ExportWarning.Code.CELL_TRUNCATED);
    }

    @Test
    void sheetNames_areMadeValidForExcel_andUnique() throws IOException {
        Source project = new ProjectSource();
        rows(project, ExportRow.of(ExportSubject.PROJECT, Map.of()));
        Column c = column("c", OutputType.TEXT, new ConstantRule(List.of(), "x"));
        ExportTemplateDefinition d = template(null,
                sheet("a/b", false, List.of(), List.of(project), c),
                sheet("A_B", false, List.of(), List.of(project), c),
                sheet("x".repeat(40), false, List.of(), List.of(project), c));

        ExportEngine.Result result = run(d);

        try (Workbook wb = open(result)) {
            assertThat(wb.getSheetName(0)).isEqualTo("a_b");
            assertThat(wb.getSheetName(1)).isEqualTo("A_B~2");
            assertThat(wb.getSheetName(2)).hasSize(31);
        }
        assertThat(result.warnings()).extracting(ExportWarning::code).containsOnly(ExportWarning.Code.SHEET_RENAMED);
    }

    @Test
    void conceptValues_useTheBatchResolvedLabels() throws IOException {
        stubFieldReads();
        Concept concept = new Concept();
        concept.setId(5L);
        concept.setExternalId("ext");
        ConceptDTO dto = new ConceptDTO();
        dto.setId(5L);
        lenient().when(conceptMapper.convert(concept)).thenReturn(dto);
        lenient().when(labelResolver.resolveLabels(any(), eq("fr"))).thenReturn(Map.of(5L, "Fait"));
        rows(UE, row(Map.of("1", List.of(concept))));

        try (Workbook wb = open(run(template(null, sheet("UE", false, List.of(), List.of(UE),
                column("nature", OutputType.TEXT, direct(CODE, null))))))) {
            assertThat(wb.getSheet("UE").getRow(1).getCell(0).getStringCellValue()).isEqualTo("Fait");
        }
    }

    @Test
    void unsupportedValue_leavesTheCellEmpty_withAWarning() throws IOException {
        stubFieldReads();
        rows(UE, row(Map.of("1", List.of(new Object()))));

        ExportEngine.Result result = run(template(null, sheet("UE", false, List.of(), List.of(UE),
                column("c", OutputType.TEXT, direct(CODE, null)))));

        assertThat(result.warnings()).extracting(ExportWarning::code).containsExactly(ExportWarning.Code.UNSUPPORTED_VALUE);
    }

    @Test
    void fileName_followsThePattern_andIsMadeSafe() {
        Source project = new ProjectSource();
        rows(project, ExportRow.of(ExportSubject.PROJECT, Map.of()));
        ExportTemplateDefinition.Sheet s = sheet("S", false, List.of(), List.of(project), column("c", OutputType.TEXT, new ConstantRule(List.of(), "x")));

        ExportEngine.Result named = run(template("OA{oaCode}_{name}_{nope}", s));
        ExportEngine.Result defaulted = run(template(null, s));

        assertThat(named.fileName()).isEqualTo("OAOA42_Fouille_Sainte-Ursule.xlsx");
        assertThat(named.warnings()).extracting(ExportWarning::code).contains(ExportWarning.Code.UNKNOWN_PLACEHOLDER);
        assertThat(defaulted.fileName()).startsWith("Rapport_ORG-1_20").endsWith(".xlsx");
    }

    @Test
    void preview_limitsTheRowsReadPerSource_butCountsAllOfThem() {
        stubFieldReads();
        rows(UE, row(Map.of("1", List.of("a"))), row(Map.of("1", List.of("b"))), row(Map.of("1", List.of("c"))));

        ExportEngine.Preview preview = engine.preview(template(null, sheet("UE", false, List.of(), List.of(UE),
                column("code", OutputType.TEXT, direct(CODE, null)),
                column("n", OutputType.NUMBER, new ConstantRule(List.of(), "2,50")))), PROJECT_ID, "fr", 2);

        assertThat(preview.sheets()).singleElement().satisfies(s -> {
            assertThat(s.name()).isEqualTo("UE");
            assertThat(s.headers()).containsExactly("code", "n");
            assertThat(s.totalRows()).isEqualTo(3);
            assertThat(s.rows()).containsExactly(List.of("a", "2.5"), List.of("b", "2.5"));
        });
    }

    @Test
    void preview_keepsEmptySheets_andCutsLongCells() {
        Source project = new ProjectSource();
        rows(project, ExportRow.of(ExportSubject.PROJECT, Map.of()));
        rows(UE);

        ExportEngine.Preview preview = engine.preview(template(null,
                sheet("OA", false, List.of(), List.of(project), column("c", OutputType.TEXT, new ConstantRule(List.of(), "x".repeat(400)))),
                sheet("UE", true, List.of(), List.of(UE), column("c", OutputType.TEXT, new ConstantRule(List.of(), "x")))), PROJECT_ID, "fr", 10);

        assertThat(preview.sheets()).extracting(ExportEngine.SheetPreview::name).containsExactly("OA", "UE");
        assertThat(preview.sheets().get(0).rows().get(0).get(0)).hasSize(301).endsWith("…");
        assertThat(preview.sheets().get(1).rows()).isEmpty();
    }

    @Test
    void sheetWithoutSource_hasNoRow_andIsOmittedWhenAsked() throws IOException {
        stubFieldReads();
        rows(UE, row(Map.of("1", List.of("UE-1"))));

        ExportEngine.Result result = run(template(null,
                sheet("UE", false, List.of(), List.of(UE), column("code", OutputType.TEXT, direct(CODE, null))),
                sheet("Vide", true, List.of(), List.of(), column("h", OutputType.TEXT))));

        try (Workbook wb = open(result)) {
            assertThat(wb.getSheet("Vide")).isNull();
            assertThat(wb.getSheet("UE")).isNotNull();
        }
    }

    @Test
    void measurementProperty_readsTheUnitOrTheComment_ofTheMeasure() throws IOException {
        stubFieldReads();
        fr.siamois.domain.models.form.measurement.UnitDefinition unit = new fr.siamois.domain.models.form.measurement.UnitDefinition();
        unit.setLabel("gramme");
        fr.siamois.domain.models.form.measurement.MeasurementAnswer weight =
                fr.siamois.domain.models.form.measurement.MeasurementAnswer.builder().numericValue(12.5).unit(unit).comment("fragile").build();
        rows(UE, row(Map.of("1", List.of(weight))));

        ExportEngine.Result result = run(template(null, sheet("UE", false, List.of(), List.of(UE),
                column("poids", OutputType.TEXT, direct(CODE, null)),
                column("unite", OutputType.TEXT, new DirectRule(List.of(), new ConceptField(CODE, "unit"), List.of(), null)),
                column("note", OutputType.TEXT, new DirectRule(List.of(), new ConceptField(CODE, "comment"), List.of(), null)))));

        try (Workbook wb = open(result)) {
            Sheet s = wb.getSheet("UE");
            assertThat(s.getRow(1).getCell(0).getStringCellValue()).isEqualTo("12.5");
            assertThat(s.getRow(1).getCell(1).getStringCellValue()).isEqualTo("gramme");
            assertThat(s.getRow(1).getCell(2).getStringCellValue()).isEqualTo("fragile");
        }
    }
}
