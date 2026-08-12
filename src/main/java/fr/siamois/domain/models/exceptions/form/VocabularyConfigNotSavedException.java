package fr.siamois.domain.models.exceptions.form;

/**
 * A branch or a collection was picked, but writing it onto the field failed : either the saved field
 * can't be found back as a concept field, or the thesaurus import behind the configuration did not
 * go through.
 */
public class VocabularyConfigNotSavedException extends FieldVocabularyConfigException {

    public VocabularyConfigNotSavedException(String message, String messageCode) {
        super(message, messageCode);
    }

    public VocabularyConfigNotSavedException(String message, String messageCode, Throwable cause) {
        super(message, messageCode, cause);
    }

}
