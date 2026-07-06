package fr.siamois.infrastructure.dataimport;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ImportError(String sheet, int row, String column, String message) {

    private static final Pattern COLUMN_PREFIX = Pattern.compile("^\\[colonne '([^']*)']\\s*:\\s*(.*)$", Pattern.DOTALL);

    /**
     * Builds a row-level error from a raw exception message, pulling the column name out of the
     * "[colonne 'X'] : ..." prefix that {@code getOptionalCell}/{@code parseOptionalDate} and similar
     * helpers add when a specific column fails to parse.
     *
     * @param sheet name of the sheet the error occurred on
     * @param row row number (1-based, as shown to the user) the error occurred on
     * @param rawMessage raw exception message, optionally prefixed with "[colonne 'X'] : "
     * @return an error with {@code column} and {@code message} split out of the prefix, or an empty
     *         column and the raw message unchanged if it didn't match the prefix
     */
    public static ImportError forRow(String sheet, int row, String rawMessage) {
        if (rawMessage != null) {
            Matcher m = COLUMN_PREFIX.matcher(rawMessage);
            if (m.matches()) {
                return new ImportError(sheet, row, m.group(1), m.group(2));
            }
        }
        return new ImportError(sheet, row, "", rawMessage);
    }
}
