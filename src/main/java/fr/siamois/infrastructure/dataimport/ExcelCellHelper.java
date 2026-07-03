package fr.siamois.infrastructure.dataimport;

import fr.siamois.infrastructure.database.initializer.seeder.SpatialUnitSeeder;
import org.apache.poi.ss.usermodel.*;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class ExcelCellHelper {

    private ExcelCellHelper() {}

    public static Map<String, Integer> indexColumns(Row header) {
        return indexColumns(header, Map.of());
    }

    public static Map<String, Integer> indexColumns(Row header, Map<String, String> aliases) {
        Map<String, Integer> map = new HashMap<>();
        if (header == null) return map;
        for (int c = 0; c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            if (cell != null) {
                String raw = getStringCell(cell);
                if (raw != null) {
                    String norm = normalize(raw);
                    String key  = aliases.getOrDefault(norm, norm);
                    map.put(key, c);
                }
            }
        }
        return map;
    }

    public static String getStringCell(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        return getStringCell(cell);
    }

    public static String getStringCellOrNull(Row row, Integer colIndex) {
        return colIndex != null ? getStringCell(row, colIndex) : null;
    }

    public static String getStringCellOrNull(Row row, Map<String, Integer> cols, String key) {
        try {
            Integer index = cols.get(key);
            return index != null ? getStringCell(row, index) : null;
        } catch (Exception e) {
            throw new IllegalStateException("[colonne '" + key + "'] : " + e.getMessage(), e);
        }
    }

    public static Integer getIntegerCellOrNull(Row row, Map<String, Integer> cols, String key) {
        Integer colIndex = cols.get(key);
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return (int) cell.getNumericCellValue();
        String s = getStringCell(cell);
        return parseIntegerSafe(s);
    }

    public static String getStringCell(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            double v = cell.getNumericCellValue();
            return v == Math.floor(v) && !Double.isInfinite(v)
                    ? String.valueOf((long) v)
                    : String.valueOf(v);
        }
        String val = cell.getStringCellValue();
        return val != null ? val.trim() : null;
    }

    public static List<String> parsePersonList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("[;,]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public static String normalize(String s) {
        if (s == null) return "";
        String tmp = s.trim().toLowerCase(Locale.ROOT);
        tmp = Normalizer.normalize(tmp, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        tmp = tmp.replaceAll("\\s+", " ");
        return tmp;
    }

    public static OffsetDateTime parseOffsetDateTime(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                Date d = cell.getDateCellValue();
                return d.toInstant().atOffset(ZoneOffset.UTC);
            }
            String raw = cell.getStringCellValue();
            if (raw == null || raw.isBlank()) return null;
            raw = raw.trim();
            LocalDate ld = LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
            return ld.atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }

    public static Set<SpatialUnitSeeder.SpatialUnitKey> parseSpatialNames(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split("&&"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(SpatialUnitSeeder.SpatialUnitKey::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static void forEachDataRow(Sheet sheet, List<ImportError> errors, Consumer<Row> consumer) {
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null) {
                try {
                    consumer.accept(row);
                } catch (Exception e) {
                    errors.add(ImportError.forRow(sheet.getSheetName(), r + 1, e.getMessage()));
                }
            }
        }
    }

    public static Integer parseIntegerSafe(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return Integer.valueOf(str.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
