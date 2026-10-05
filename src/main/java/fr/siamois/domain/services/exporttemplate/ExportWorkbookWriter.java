package fr.siamois.domain.services.exporttemplate;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Écrit les feuilles évaluées dans un classeur {@code .xlsx} : en-têtes en gras, nombres en cellules
 * numériques, textes coupés à la limite d'Excel, noms de feuilles rendus acceptables et uniques.
 * Aucune formule n'est écrite.
 */
final class ExportWorkbookWriter {

    /** Limite d'Excel : caractères par cellule. */
    static final int MAX_CELL_LENGTH = 32_767;
    private static final int MAX_SHEET_NAME = 31;
    private static final Pattern SHEET_FORBIDDEN = Pattern.compile("[\\[\\]:*?/\\\\]");

    private ExportWorkbookWriter() {
        throw new UnsupportedOperationException();
    }

    /**
     * Limite connue (streaming) : le classeur entier est construit en mémoire ({@link XSSFWorkbook}) puis copié dans un
     * {@code byte[]}, et l'appelant garde déjà toutes les lignes évaluées. Pour les très gros projets,
     * passer à {@code SXSSFWorkbook} et lire les sources par pages, en écrivant au fil de l'eau.
     * Reporté jusqu'à ce que les volumes réels l'exigent.
     */
    static byte[] write(List<ExportSheetData> sheets, List<ExportWarning> warnings) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            header.setFont(bold);
            Set<String> usedNames = new HashSet<>();
            for (ExportSheetData data : sheets) {
                var worksheet = workbook.createSheet(sheetName(data.sheet().name(), usedNames, warnings));
                Row head = worksheet.createRow(0);
                for (int c = 0; c < data.sheet().columns().size(); c++) {
                    Cell cell = head.createCell(c);
                    cell.setCellValue(data.sheet().columns().get(c).header());
                    cell.setCellStyle(header);
                }
                for (int r = 0; r < data.rows().size(); r++) {
                    Row row = worksheet.createRow(r + 1);
                    List<Object> values = data.rows().get(r);
                    for (int c = 0; c < values.size(); c++) {
                        writeCell(row.createCell(c), values.get(c), data, c, warnings);
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the export workbook", e);
        }
    }

    private static void writeCell(Cell cell, Object value, ExportSheetData data, int column, List<ExportWarning> warnings) {
        if (value instanceof Double d) {
            cell.setCellValue(d);
            return;
        }
        String text = (String) value;
        if (text.length() > MAX_CELL_LENGTH) {
            warnings.add(new ExportWarning(ExportWarning.Code.CELL_TRUNCATED, data.sheet().name(),
                    data.sheet().columns().get(column).header(), null));
            text = truncate(text);
        }
        cell.setCellValue(text);
    }

    /** Coupe à la limite d'Excel sans scinder une paire de substituts UTF-16. */
    private static String truncate(String text) {
        int end = MAX_CELL_LENGTH;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    /** Nom accepté par Excel : sans caractères interdits, 31 caractères au plus, unique sans tenir compte de la casse. */
    private static String sheetName(String wanted, Set<String> used, List<ExportWarning> warnings) {
        String name = SHEET_FORBIDDEN.matcher(wanted).replaceAll("_");
        name = name.length() > MAX_SHEET_NAME ? name.substring(0, MAX_SHEET_NAME) : name;
        String unique = name;
        int n = 2;
        while (!used.add(unique.toLowerCase(Locale.ROOT))) {
            String suffix = "~" + n++;
            unique = name.substring(0, Math.min(name.length(), MAX_SHEET_NAME - suffix.length())) + suffix;
        }
        if (!unique.equals(wanted)) {
            warnings.add(new ExportWarning(ExportWarning.Code.SHEET_RENAMED, wanted, null, unique));
        }
        return unique;
    }
}
