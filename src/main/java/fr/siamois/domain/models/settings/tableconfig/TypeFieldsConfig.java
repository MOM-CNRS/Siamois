package fr.siamois.domain.models.settings.tableconfig;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TypeFieldsConfig implements Serializable {
    private List<TypeFieldFormConfig> fields = new ArrayList<>();
}
