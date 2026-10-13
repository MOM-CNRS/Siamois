package fr.siamois.domain.models.settings.tableconfig;

import java.io.Serializable;

/** A thesaurus concept as the field screen shows it: its id, its name and its definition, in the user's language. */
public record ConceptSummary(Long id, String name, String description) implements Serializable {

    public boolean hasDescription() {
        return description != null && !description.isBlank();
    }
}
