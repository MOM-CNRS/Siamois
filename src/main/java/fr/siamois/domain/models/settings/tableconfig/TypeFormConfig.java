package fr.siamois.domain.models.settings.tableconfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Mirrors {@code fr.siamois.domain.models.form.config.FormConfig}: {@code valueConceptLabel}
 * stands in for {@code valueConcept} (empty/null meaning the {@code _default} type). The other
 * fields are UI-only and have no equivalent on the real entity.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TypeFormConfig implements Serializable {
    private String typeName;
    private String valueConceptLabel;
    private String description;
    private boolean inheritsDefaultFields;
    private boolean visibleInApp;
}
