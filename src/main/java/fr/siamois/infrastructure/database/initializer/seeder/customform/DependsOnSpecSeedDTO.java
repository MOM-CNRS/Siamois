package fr.siamois.infrastructure.database.initializer.seeder.customform;

import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeederSpec;

/** Version DTO de DependsOnJson pour le seeding. */
public record DependsOnSpecSeedDTO(
        CustomFieldSeederSpec field // le CustomField dont la réponse sert de base value
) {
}
