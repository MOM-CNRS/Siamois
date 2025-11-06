package fr.siamois.infrastructure.database.initializer.seeder.customform;

import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldAnswerDTO;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeederSpec;

import java.util.List;

/** Version DTO de la spec d’activation. */
public record EnabledWhenSpecSeedDTO(
        Operator operator,
        CustomFieldSeederSpec field, // le CustomField dont on observe la valeur
        List<CustomFieldAnswerDTO> expectedValues  // 1 valeur pour EQUALS/NOT_EQUALS, ≥1 pour IN
) {
    public enum Operator { EQUALS, NOT_EQUALS, IN }
}
