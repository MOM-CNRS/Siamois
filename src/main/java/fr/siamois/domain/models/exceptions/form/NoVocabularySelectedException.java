package fr.siamois.domain.models.exceptions.form;

/**
 * The field was set to be driven by a branch or a collection, but none was picked : there is nothing
 * to configure, and the field would silently keep no vocabulary at all.
 */
public class NoVocabularySelectedException extends FieldVocabularyConfigException {

    public NoVocabularySelectedException(String message, String messageCode) {
        super(message, messageCode);
    }

}
