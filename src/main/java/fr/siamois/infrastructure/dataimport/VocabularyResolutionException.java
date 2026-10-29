package fr.siamois.infrastructure.dataimport;

import lombok.Getter;

/**
 * A label of the workbook that could not be resolved to a concept of the thesaurus configured for a field.
 * It carries the field code, so the import screen can send the user to that field's configuration and to
 * the thesaurus branch the label is looked up in.
 */
@Getter
public class VocabularyResolutionException extends IllegalStateException {

    private final String fieldCode;

    public VocabularyResolutionException(String fieldCode, String message, Throwable cause) {
        super(message, cause);
        this.fieldCode = fieldCode;
    }
}
