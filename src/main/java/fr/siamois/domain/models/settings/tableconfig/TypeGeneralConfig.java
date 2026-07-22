package fr.siamois.domain.models.settings.tableconfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TypeGeneralConfig implements Serializable {
    private String typeName;
    private String linkedConceptLabel;
    private String description;
    private boolean inheritsDefaultFields;
    private boolean visibleInApp;
}
