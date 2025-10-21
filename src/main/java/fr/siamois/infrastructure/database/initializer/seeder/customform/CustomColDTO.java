package fr.siamois.infrastructure.database.initializer.seeder.customform;

import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeederSpec;


import java.io.Serializable;


public record CustomColDTO(
        boolean readOnly,
        boolean isRequired,
        CustomFieldSeederSpec field,
        String className
) implements Serializable {
}
