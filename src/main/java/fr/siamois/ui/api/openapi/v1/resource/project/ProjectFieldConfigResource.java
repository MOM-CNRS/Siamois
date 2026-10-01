package fr.siamois.ui.api.openapi.v1.resource.project;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A field-configuration row scoped to one project type (plan §6) — references into the sibling
 * root-level {@code fields} catalog by id. Deliberately carries only {@code active}/
 * {@code institutionLocked}: unlike RU/Mobilier/Phase/Container (where {@code FieldFormConfig}
 * genuinely owns {@code isMandatory}), Project's {@code mandatory}/{@code readOnly} come from the
 * layout ({@code CustomColUiDto.isRequired}/{@code .readOnly} inside {@code form.layoutJson}), not
 * from this row — and there is deliberately no {@code order}/{@code section} here either, same
 * reason: {@code layoutJson} already encodes row/column/field arrangement.
 */
@Schema(description = "Configuration d'un champ pour un type de projet")
public record ProjectFieldConfigResource(
        @Schema(description = "Identifiant du champ (custom_field_id), référence dans le catalogue fields", example = "3")
        String field,
        @Schema(description = "Le champ est actif pour ce type")
        boolean active,
        @Schema(description = "Le champ est verrouillé par l'institution (non modifiable par un sous-périmètre)")
        boolean institutionLocked
) {
}
