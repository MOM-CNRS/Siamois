package fr.siamois.domain.models.exceptions.recordingunit;

public class FailedRecordingUnitSaveException extends RuntimeException {
    public FailedRecordingUnitSaveException(String message) {
        super(message);
    }

    public FailedRecordingUnitSaveException(String message, Throwable cause) {
        super(message, cause);
    }
}
