package fr.siamois.infrastructure.dataimport;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;

import static fr.siamois.infrastructure.dataimport.ExcelCellHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

class ExcelCellHelperTest {

    // -------------------------------------------------------------------------
    // Column normalization
    // -------------------------------------------------------------------------

    @Test
    void indexColumns_normalizesAccentsAndSpaces() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("Test");
        Row header = s.createRow(0);
        header.createCell(0).setCellValue("Nom");
        header.createCell(1).setCellValue("Prénom");
        header.createCell(2).setCellValue("Email Admins");
        header.createCell(3).setCellValue("Unité Spatiale");

        Map<String, Integer> cols = indexColumns(s.getRow(0));

        assertThat(cols)
                .containsEntry("nom", 0)
                .containsEntry("prenom", 1)
                .containsEntry("email admins", 2)
                .containsEntry("unite spatiale", 3);
    }

    @Test
    void indexColumns_appliesAliases() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("Test");
        Row header = s.createRow(0);
        header.createCell(0).setCellValue("Ma Colonne");
        header.createCell(1).setCellValue("Mon Id");

        Map<String, String> aliases = Map.of("ma colonne", "nom", "mon id", "identifiant");
        Map<String, Integer> cols = indexColumns(s.getRow(0), aliases);

        assertThat(cols)
                .containsEntry("nom", 0)
                .containsEntry("identifiant", 1);
    }

    // -------------------------------------------------------------------------
    // Date parsing
    // -------------------------------------------------------------------------

    @Test
    void parseOffsetDateTime_fromIsoString() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet();
        Row r = s.createRow(0);
        Cell c = r.createCell(0);
        c.setCellValue("2024-02-10");

        OffsetDateTime dt = parseOffsetDateTime(c);

        assertThat(dt).isEqualTo(
                OffsetDateTime.of(2024, 2, 10, 0, 0, 0, 0, ZoneOffset.UTC)
        );
    }

    @Test
    void parseOffsetDateTime_nullCell() {
        OffsetDateTime dt = parseOffsetDateTime(null);
        assertThat(dt).isNull();
    }

    @Test
    void parseOffsetDateTime_emptyString() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet();
        Row r = s.createRow(0);
        Cell c = r.createCell(0);
        c.setCellValue("   ");

        OffsetDateTime dt = parseOffsetDateTime(c);
        assertThat(dt).isNull();
    }

    @Test
    void parseOffsetDateTime_excelNumericDate() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet();
        Row r = s.createRow(0);
        Cell c = r.createCell(0);

        c.setCellValue(Date.from(
                LocalDate.of(2024, Month.FEBRUARY, 10)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
        ));
        CellStyle style = wb.createCellStyle();
        style.setDataFormat((short) 14);
        c.setCellStyle(style);

        OffsetDateTime dt = parseOffsetDateTime(c);
        assertThat(dt).isEqualTo(
                LocalDate.of(2024, Month.FEBRUARY, 10).atStartOfDay().atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    void parseOffsetDateTime_invalidString() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet();
        Row r = s.createRow(0);
        Cell c = r.createCell(0);
        c.setCellValue("not-a-date");

        OffsetDateTime dt = parseOffsetDateTime(c);
        assertThat(dt).isNull();
    }

    @Test
    void parseOffsetDateTime_stringWithSpaces() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet();
        Row r = s.createRow(0);
        Cell c = r.createCell(0);
        c.setCellValue(" 2024-02-10 ");

        OffsetDateTime dt = parseOffsetDateTime(c);
        assertThat(dt).isEqualTo(
                OffsetDateTime.of(2024, 2, 10, 0, 0, 0, 0, ZoneOffset.UTC)
        );
    }

    @Test
    void parseOffsetDateTime_numericNotDate() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet();
        Row r = s.createRow(0);
        Cell c = r.createCell(0);
        c.setCellValue(123.45);

        OffsetDateTime dt = parseOffsetDateTime(c);
        assertThat(dt).isNull();
    }
}
