package fr.siamois.infrastructure.database.initializer.seeder.customform;

import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldAnswerDTO;

import java.util.List;

/** Version DTO de la spec d’activation. */
public record EnabledWhenSpecSeedDTO(
        Operator operator,
        List<CustomFieldAnswerDTO> expectedValues  // 1 valeur pour EQUALS/NOT_EQUALS, ≥1 pour IN
) {
    public enum Operator { EQUALS, NOT_EQUALS, IN }
}
