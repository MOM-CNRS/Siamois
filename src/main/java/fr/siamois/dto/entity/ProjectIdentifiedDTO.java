package fr.siamois.dto.entity;

import fr.siamois.domain.models.ValidationStatus;

/**
 * An entity identified in its project by a free-text identifier, and carrying a validation status:
 * what the form PATCH of its fiche checks and writes the same way for every such entity.
 */
public interface ProjectIdentifiedDTO {

    /**
     * The entity's identifier, unique in its project.
     *
     * @return the identifier
     */
    String getIdentifier();

    /**
     * Replaces the entity's identifier.
     *
     * @param identifier the new identifier
     */
    void setIdentifier(String identifier);

    /**
     * The entity's validation status.
     *
     * @return the status
     */
    ValidationStatus getValidationStatus();
}
