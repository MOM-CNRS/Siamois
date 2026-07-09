package fr.siamois.infrastructure.database.initializer.seeder;

import lombok.Getter;

/** Wraps a seeding failure with the technical table ID of the entity being seeded, so the UI can route the error to the matching validation tab. */
@Getter
public class SeedException extends RuntimeException {
    private final String tableId;

    public SeedException(String tableId, String message, Throwable cause) {
        super(message, cause);
        this.tableId = tableId;
    }
}
