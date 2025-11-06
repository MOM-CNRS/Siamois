package fr.siamois.infrastructure.database.initializer.seeder.customform;

import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeederSpec;

import java.io.Serializable;

public record CustomColDTO(
        boolean readOnly,
        boolean isRequired,
        CustomFieldSeederSpec field,
        String className,
        EnabledWhenSpecSeedDTO enabledWhen
) implements Serializable {

    public CustomColDTO(
            boolean readOnly,
            boolean isRequired,
            CustomFieldSeederSpec field,
            String className
    ) {
        this(readOnly, isRequired, field, className, null);
    }
}
