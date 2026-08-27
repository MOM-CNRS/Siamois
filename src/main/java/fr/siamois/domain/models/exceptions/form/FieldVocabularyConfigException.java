package fr.siamois.domain.models.exceptions.form;

import lombok.Getter;

/**
 * Raised when the branch/collection picked for a field could not be configured on it. The message of
 * the exception is meant for the logs, while {@link #getMessageCode()} carries the message bundle key
 * the caller displays, so a single catch can report a different message per cause.
 */
@Getter
public abstract class FieldVocabularyConfigException extends RuntimeException {

    private final String messageCode;

    protected FieldVocabularyConfigException(String message, String messageCode) {
        super(message);
        this.messageCode = messageCode;
    }

    protected FieldVocabularyConfigException(String message, String messageCode, Throwable cause) {
        super(message, cause);
        this.messageCode = messageCode;
    }

}
