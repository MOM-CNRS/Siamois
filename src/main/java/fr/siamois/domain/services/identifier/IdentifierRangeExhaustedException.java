package fr.siamois.domain.services.identifier;

/** Every number of the configured identifier range has been allocated: no new identifier can be generated. */
public class IdentifierRangeExhaustedException extends IllegalStateException {
    public IdentifierRangeExhaustedException(String message) {
        super(message);
    }
}
