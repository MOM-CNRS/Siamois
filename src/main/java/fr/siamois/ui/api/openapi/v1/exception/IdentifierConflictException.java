package fr.siamois.ui.api.openapi.v1.exception;

import lombok.Getter;
import org.springframework.lang.Nullable;

/**
 * An identifier already used in its project: answered 409 by {@code RestExceptionHandler}, with a message in the
 * caller's language — which is why it carries no text of its own, only the identifier when it is known.
 */
@Getter
public class IdentifierConflictException extends RuntimeException {

    @Nullable
    private final String identifier;

    public IdentifierConflictException() {
        this(null, null);
    }

    public IdentifierConflictException(@Nullable String identifier, @Nullable Throwable cause) {
        super(identifier == null ? "Identifier already used in the project"
                : "Identifier already used in the project: " + identifier, cause);
        this.identifier = identifier;
    }
}
