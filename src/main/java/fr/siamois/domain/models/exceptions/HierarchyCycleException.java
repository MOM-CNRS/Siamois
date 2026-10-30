package fr.siamois.domain.models.exceptions;

/**
 * A parent/child link that would make an entity its own ancestor. An {@link IllegalStateException}
 * so the existing hierarchy callers keep mapping it to 409; the type lets the saves that wrap their
 * failures let it through, and the REST handler answer it with its own message.
 */
public class HierarchyCycleException extends IllegalStateException {
    public HierarchyCycleException(String message) {
        super(message);
    }
}
