package fr.siamois.domain.models.settings.tableconfig;

import lombok.*;

import java.io.Serializable;

/**
 * A reusable field definition offered by the "reuse an existing field" picker, independent of any
 * one type's configuration.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class FieldCatalogEntry implements Serializable {
    private String name;
    private FieldType type;
    private String description;
}
