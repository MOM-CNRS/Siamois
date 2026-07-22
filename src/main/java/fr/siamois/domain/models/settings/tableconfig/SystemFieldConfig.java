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
public class SystemFieldConfig implements Serializable {
    private String name;
    private FieldType type;
    private boolean visible;
    private boolean required;
    private boolean configurable;
    private String sourceLabel;
}
