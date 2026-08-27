package fr.siamois.domain.models.exceptions.recordingunit;

import lombok.Getter;

/**
 * A generated recording-unit identifier already exists in the action unit, so the caller can
 * surface it directly rather than a generic failure message.
 */
@Getter
public class RecordingUnitIdentifierAlreadyExistsException extends RuntimeException {

    private final String identifier;

    public RecordingUnitIdentifierAlreadyExistsException(String identifier) {
        super("Generated recording-unit identifier already exists: " + identifier);
        this.identifier = identifier;
    }
}
