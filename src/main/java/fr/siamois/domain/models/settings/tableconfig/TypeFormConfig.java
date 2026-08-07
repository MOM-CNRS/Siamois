package fr.siamois.domain.models.settings.tableconfig;

import lombok.*;

import java.io.Serializable;

/**
 * General configuration of a table type: its read-only thesaurus summary and its editable
 * identifier settings.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TypeFormConfig implements Serializable {
    private String typeName;
    private String definition;
    private String identifierFormat;
    private int minCode;
    private int maxCode;
}
