package fr.siamois.infrastructure.database.initializer.seeder;

import lombok.Getter;

/**
 * A concept the workbook points at that is not loaded into Siamois. It carries the link to that concept in
 * the thesaurus (when it can be built), so the import screen can offer it next to the message.
 */
@Getter
public class MissingConceptException extends IllegalStateException {

    private final String conceptUrl;

    public MissingConceptException(String message, String conceptUrl) {
        super(message);
        this.conceptUrl = conceptUrl;
    }

    /** The exception for {@code key}, with the message and the link {@code seeder} describes it with. */
    public static MissingConceptException of(ConceptSeeder seeder, ConceptSeeder.ConceptKey key, Long institutionId) {
        return new MissingConceptException(seeder.describeMissingConcept(key, institutionId),
                key == null ? null : seeder.conceptLink(key, institutionId));
    }
}
