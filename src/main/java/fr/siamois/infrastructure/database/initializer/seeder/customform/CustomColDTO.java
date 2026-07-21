package fr.siamois.infrastructure.database.initializer.seeder.customform;

import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeederSpec;

import java.io.Serializable;

public record CustomColDTO(
        boolean readOnly,
        boolean isRequired,
        CustomFieldSeederSpec field,
        String className,
        EnabledWhenSpecSeedDTO enabledWhen,
        DependsOnSpecSeedDTO dependsOn
) implements Serializable {

    public CustomColDTO(
            boolean readOnly,
            boolean isRequired,
            CustomFieldSeederSpec field,
            String className
    ) {
        this(readOnly, isRequired, field, className, null, null);
    }

    public CustomColDTO(
            boolean readOnly,
            boolean isRequired,
            CustomFieldSeederSpec field,
            String className,
            EnabledWhenSpecSeedDTO enabledWhen
    ) {
        this(readOnly, isRequired, field, className, enabledWhen, null);
    }
}
