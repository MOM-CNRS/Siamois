package fr.siamois.infrastructure.database.initializer.seeder.customfield;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.infrastructure.database.initializer.seeder.ConceptSeeder;
import io.micrometer.common.lang.Nullable;
import lombok.Data;

public record CustomFieldSeederSpec(
        Class<? extends CustomField> answerClass,
        Boolean isSystemField,
        String label,
        ConceptSeeder.ConceptKey conceptKey,
        @Nullable String valueBinding,
        @Nullable String iconClass,
        @Nullable String styleClass,
        @Nullable String fieldCode
){};
