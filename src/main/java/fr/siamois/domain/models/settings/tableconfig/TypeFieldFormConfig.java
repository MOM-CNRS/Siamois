package fr.siamois.domain.models.settings.tableconfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Mirrors {@code fr.siamois.domain.models.form.config.FieldFormConfig} (join of a
 * {@code CustomField} to a {@code FormConfig}): {@code systemField} stands in for
 * {@code CustomField.isSystemField}, {@code active}/{@code mandatory}/{@code institutionLocked}
 * mirror {@code isActive}/{@code isMandatory}/{@code isInstitutionLocked}. {@code name}/{@code type}
 * are UI-only display stand-ins for what would be read through the linked {@code CustomField};
 * {@code configurable}/{@code sourceLabel} are UI-only with no entity equivalent.
 * <p>
 * {@code institutionLocked} is read-only from the project screen — it can only be set on the
 * (not yet implemented) institution-level settings screens.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TypeFieldFormConfig implements Serializable {
    private String name;
    private FieldType type;
    private boolean systemField;
    private boolean active;
    private boolean mandatory;
    private boolean institutionLocked;
    private boolean configurable;
    private String sourceLabel;
}
