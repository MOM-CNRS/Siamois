package fr.siamois.infrastructure.dataimport;

import fr.siamois.infrastructure.database.initializer.seeder.MissingConceptException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One problem found while reading or saving a workbook.
 *
 * @param vocabulary when the problem is about the thesaurus (a label that does not resolve, a concept that is not
 *                   loaded into Siamois): what the screen needs to link to the field's configuration and to the
 *                   thesaurus; {@code null} otherwise
 */
public record ImportError(String sheet, int row, String column, String message, VocabularyIssue vocabulary) {

    /**
     * @param fieldCode  the field a label was looked up for (e.g. {@code SIARU.NATURE}); {@code null} for a concept
     *                   that is not loaded, whose field is not known
     * @param typeLabel  the type label of the row, when it has one: lets the screen link to that type's configuration
     * @param conceptUrl the address of the missing concept in the thesaurus, for a concept that is not loaded
     */
    public record VocabularyIssue(String fieldCode, String typeLabel, String conceptUrl) {
    }

    private static final Pattern COLUMN_PREFIX = Pattern.compile("^\\[colonne '([^']*)']\\s*:\\s*(.*)$", Pattern.DOTALL);

    public ImportError(String sheet, int row, String column, String message) {
        this(sheet, row, column, message, null);
    }

    /** Whether this is a thesaurus problem the screen can offer configuration and thesaurus links for. */
    public boolean isVocabularyError() {
        return vocabulary != null;
    }

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
        return forRow(sheet, row, rawMessage, null);
    }

    /**
     * Same as {@link #forRow(String, int, String)} for a failure that may be a {@link VocabularyResolutionException}
     * (looked for in the cause chain): the error then carries the field code and the row's type label.
     */
    public static ImportError forRow(String sheet, int row, Throwable failure, String typeLabel) {
        VocabularyResolutionException vocabulary = causeOfType(failure, VocabularyResolutionException.class);
        VocabularyIssue issue = vocabulary == null ? null : new VocabularyIssue(vocabulary.getFieldCode(), typeLabel, null);
        return forRow(sheet, row, failure.getMessage(), issue);
    }

    /**
     * The error of a table that could not be saved. {@code message} is what to show; when the failure comes from a
     * concept that is not loaded into Siamois, the error carries that concept's address in the thesaurus.
     */
    public static ImportError forTable(String tableId, Throwable failure, String message) {
        MissingConceptException missing = causeOfType(failure, MissingConceptException.class);
        VocabularyIssue issue = missing == null ? null : new VocabularyIssue(null, null, missing.getConceptUrl());
        return new ImportError(tableId, 0, "—", message, issue);
    }

    private static ImportError forRow(String sheet, int row, String rawMessage, VocabularyIssue issue) {
        if (rawMessage != null) {
            Matcher m = COLUMN_PREFIX.matcher(rawMessage);
            if (m.matches()) {
                return new ImportError(sheet, row, m.group(1), m.group(2), issue);
            }
        }
        return new ImportError(sheet, row, "", rawMessage, issue);
    }

    private static <T extends Throwable> T causeOfType(Throwable failure, Class<T> type) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }
}
