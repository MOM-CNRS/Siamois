package fr.siamois.infrastructure.database.initializer.seeder.customform;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeederSpec;
import lombok.Data;

import java.io.Serializable;


public record CustomColDTO(
        boolean readOnly,
        boolean isRequired,
        CustomFieldSeederSpec field,
        String className
) implements Serializable {
}
