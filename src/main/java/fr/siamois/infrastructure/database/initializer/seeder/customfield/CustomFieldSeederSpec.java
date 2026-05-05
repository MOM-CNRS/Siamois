package fr.siamois.infrastructure.database.initializer.seeder.customfield;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.infrastructure.database.initializer.seeder.ConceptSeeder;
import io.micrometer.common.lang.Nullable;
import lombok.Builder;

@Builder
public record CustomFieldSeederSpec(
        Class<? extends CustomField> answerClass,
        Boolean isSystemField,
        String label,
        ConceptSeeder.ConceptKey conceptKey,
        @Nullable String valueBinding,
        @Nullable String iconClass,
        @Nullable String styleClass,
        @Nullable String fieldCode,
        @Nullable Boolean isTextArea,
        @Nullable UnitDefinitionDTO unitDefinitionDTO
        ){
    public CustomFieldSeederSpec(
            Class<? extends CustomField> answerClass,
            Boolean isSystemField,
            String label,
            ConceptSeeder.ConceptKey conceptKey,
            @Nullable String valueBinding,
            @Nullable String iconClass,
            @Nullable String styleClass,
            @Nullable String fieldCode
    ) {
        this(answerClass, isSystemField, label, conceptKey,
                valueBinding, iconClass, styleClass, fieldCode, null, null);
    }
}


