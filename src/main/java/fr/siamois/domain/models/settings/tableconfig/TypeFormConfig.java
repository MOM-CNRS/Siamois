package fr.siamois.domain.models.settings.tableconfig;

import lombok.*;

import java.io.Serializable;

/**
 * Read-only summary shown above the field/identifier tabs: the type's name and the definition of
 * the thesaurus concept it represents.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TypeFormConfig implements Serializable {
    private String typeName;
    private String definition;
}
