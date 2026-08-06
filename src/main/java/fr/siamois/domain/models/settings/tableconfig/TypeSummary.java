package fr.siamois.domain.models.settings.tableconfig;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TypeSummary implements Serializable {
    private String name;
    private boolean isDefault;
}
